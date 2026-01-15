package com.example.family;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.ArrayList;
import com.example.config.ToleranceConfigReader; // Config okumak için
import family.Empty;
import family.FamilyServiceGrpc;
import family.FamilyView;
import family.NodeInfo;
import family.ChatMessage;
import family.StoredMessage; // YENİ EKLENDİ

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.InetAddress; // YENİ EKLENDİ

import java.io.IOException;
import java.net.ServerSocket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

public class NodeMain {

    private static final int START_PORT = 5555;
    private static final int PRINT_INTERVAL_SECONDS = 10;

    public static void main(String[] args) throws Exception {
        // --- FIX 1: Dinamik IP Tespiti ---
        String host = java.net.InetAddress.getLocalHost().getHostAddress();
        // -------------------------------

        int port = findFreePort(START_PORT);

        NodeInfo self = NodeInfo.newBuilder()
                .setHost(host)
                .setPort(port)
                .build();

        NodeRegistry registry = new NodeRegistry();

        com.example.server.MessageStore messageStore = new com.example.server.MessageStore(new ToleranceConfigReader(),
                port);

        // 2. Servise ve Printer'a bu instance'ı veriyoruz
        FamilyServiceImpl service = new FamilyServiceImpl(registry, self, messageStore);
        Server server = ServerBuilder
                .forPort(port)
                .addService(service) // Şimdi 'service' değişkenini bulabilecek
                .addService(new StorageServiceImpl(messageStore)) // Storage servisini ekliyoruz
                .build()
                .start();

        System.out.printf("Node started on %s:%d%n", host, port);

        // Eğer bu ilk node ise (port 5555), TCP 6666'da text dinlesin
        if (port == START_PORT) {
            startLeaderTextListener(registry, self, messageStore);
        }

        discoverExistingNodes(host, port, registry, self);
        startFamilyPrinter(registry, self, messageStore);
        // startHealthChecker(registry, self);

        server.awaitTermination();

    }

    private static void startLeaderTextListener(NodeRegistry registry, NodeInfo self,
            com.example.server.MessageStore messageStore) {
        // Sadece lider (5555 portlu node) bu methodu çağırmalıdır
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(6666)) {
                System.out.printf("Leader listening for text on TCP %s:%d%n",
                        self.getHost(), 6666);

                while (true) {
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleClientTextConnection(client, registry, self, messageStore)).start();
                }

            } catch (IOException e) {
                System.err.println("Error in leader text listener: " + e.getMessage());
            }
        }, "LeaderTextListener").start();
    }

    private static void handleClientTextConnection(Socket client,
            NodeRegistry registry,
            NodeInfo self,
            com.example.server.MessageStore messageStore) {
        System.out.println("New TCP client connected: " + client.getRemoteSocketAddress());
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String text = line.trim();
                if (text.isEmpty())
                    continue;

                // SET Komutu (FIXED: Dağıtık Replikasyon eklendi)
                if (text.toUpperCase().startsWith("SET ")) {
                    String[] parts = text.split(" ", 3);
                    if (parts.length == 3) {
                        try {
                            int id = Integer.parseInt(parts[1]);
                            String msgContent = parts[2];

                            // 1. Kendi üzerine yaz (Lider)
                            String result = messageStore.put(id, msgContent);

                            // 2. Diğer üyelere replike et (StorageService ile)
                            List<NodeInfo> sentTo = broadcastStoreToFamily(registry, self, id, msgContent);

                            // 3. İndekse kaydet (ID ile)
                            MessageIndex.getInstance().addLocation(id, self); // Liderde var
                            for (NodeInfo node : sentTo) {
                                MessageIndex.getInstance().addLocation(id, node); // Üyelerde var
                            }

                            // 4. İstemciye yanıt dön
                            PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                            out.println(result);

                            // Konsola mevcut durumu yaz
                            MessageIndex.getInstance().printIndex();

                        } catch (Exception e) {
                            new PrintWriter(client.getOutputStream(), true).println("ERROR: " + e.getMessage());
                        }
                    }
                    continue;
                }

                // GET Komutu (FIXED: Dağıtık Getirme eklendi)
                if (text.toUpperCase().startsWith("GET ")) {
                    String[] parts = text.split(" ", 2);
                    if (parts.length == 2) {
                        try {
                            int id = Integer.parseInt(parts[1]);

                            // 1. Önce kendi diskine bak
                            String result = messageStore.get(id);

                            if (result.equals("NOT_FOUND")) {
                                System.out.println("Lokalde bulunamadı (" + id + "), ağda aranıyor...");
                                // 2. Bulamazsa diğer üyelere sor (MessageIndex kullanarak)
                                List<String> locations = MessageIndex.getInstance().getLocations(id);
                                boolean found = false;

                                for (String loc : locations) {
                                    // Kendimdeyse zaten yukarıda bakmıştım, atla
                                    if (loc.equals(self.getHost() + ":" + self.getPort()))
                                        continue;

                                    System.out.println("Asking " + loc + " for message " + id);
                                    String[] addr = loc.split(":");
                                    NodeInfo targetNode = NodeInfo.newBuilder()
                                            .setHost(addr[0])
                                            .setPort(Integer.parseInt(addr[1]))
                                            .build();

                                    String remoteData = StorageClient.sendRetrieveRPC(targetNode, id);
                                    if (remoteData != null) {
                                        result = remoteData;
                                        found = true;
                                        // Cache warming (isteğe bağlı): messageStore.put(id, remoteData);
                                        break;
                                    }
                                }

                                if (!found) {
                                    new PrintWriter(client.getOutputStream(), true).println("NOT_FOUND");
                                    continue;
                                }
                            }

                            PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                            // HaToKuSe protokolü gereği başarılı yanıtlar OK ile başlamalı
                            out.println("OK " + result);
                        } catch (Exception e) {
                            new PrintWriter(client.getOutputStream(), true).println("ERROR: " + e.getMessage());
                        }
                    }
                    continue;
                }
            }

        } catch (IOException e) {
            System.err.println("TCP client handler error: " + e.getMessage());
        } finally {
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }
    }

    // YENİ METOD: StorageService kullanarak replikasyon yapar
    private static List<NodeInfo> broadcastStoreToFamily(NodeRegistry registry,
            NodeInfo self,
            int id,
            String text) {

        // 1. Config'den Tolerans değerini oku
        int tolerance = 1; // Varsayılan
        try {
            ToleranceConfigReader config = new ToleranceConfigReader();
            tolerance = config.getTolerance();
        } catch (Exception e) {
            System.err.println("Config okunamadı, varsayılan tolerans(1) kullanılıyor.");
        }

        // 2. Tüm üyeleri al
        List<NodeInfo> allMembers = registry.snapshot();

        // 3. Kendimiz hariç diğerlerini filtrele
        List<NodeInfo> targets = new ArrayList<>();
        for (NodeInfo n : allMembers) {
            // Eğer bu node ben değilsem listeye ekle
            if (!n.getHost().equals(self.getHost()) || n.getPort() != self.getPort()) {
                targets.add(n);
            }
        }

        // 4. Load Balancer ile Round Robin seçimi yap (Dengeli dağılım)
        int countToSend = Math.min(tolerance, targets.size());
        List<NodeInfo> selectedNodes = LoadBalancer.selectNodes(targets, countToSend);
        List<NodeInfo> successfulNodes = new ArrayList<>();

        System.out.println("Replikasyon Hedefleri Seçildi (" + countToSend + " kişi):");

        family.StoredMessage msg = family.StoredMessage.newBuilder()
                .setId(id)
                .setText(text)
                .build();

        // 6. Sadece seçilenlere gönder
        for (NodeInfo n : selectedNodes) {
            boolean success = StorageClient.sendStoreRPC(n, msg);
            if (success) {
                System.out.printf(" -> Mesaj gönderildi: %s:%d%n", n.getHost(), n.getPort());
                successfulNodes.add(n);
            } else {
                System.err.printf(" -> GÖNDERİLEMEDİ %s:%d%n", n.getHost(), n.getPort());
            }
        }
        return successfulNodes;
    }

    private static int findFreePort(int startPort) {
        int port = startPort;
        while (true) {
            try (ServerSocket ignored = new ServerSocket(port)) {
                return port;
            } catch (IOException e) {
                port++;
            }
        }
    }

    private static void discoverExistingNodes(String host,
            int selfPort,
            NodeRegistry registry,
            NodeInfo self) {

        for (int port = START_PORT; port < selfPort; port++) {
            ManagedChannel channel = null;
            try {
                channel = ManagedChannelBuilder
                        .forAddress(host, port)
                        .usePlaintext()
                        .build();

                FamilyServiceGrpc.FamilyServiceBlockingStub stub = FamilyServiceGrpc.newBlockingStub(channel);

                FamilyView view = stub.join(self);
                registry.addAll(view.getMembersList());

                System.out.printf("Joined through %s:%d, family size now: %d%n",
                        host, port, registry.snapshot().size());

            } catch (Exception ignored) {
            } finally {
                if (channel != null)
                    channel.shutdownNow();
            }
        }
    }

    private static void startFamilyPrinter(NodeRegistry registry, NodeInfo self,
            com.example.server.MessageStore messageStore) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        // MessageStore parametre olarak geldiği için yeniden oluşturmuyoruz

        scheduler.scheduleAtFixedRate(() -> {
            List<NodeInfo> members = registry.snapshot();
            System.out.println("======================================");
            System.out.printf("Family at %s:%d (me)%n", self.getHost(), self.getPort());
            System.out.println("Time: " + LocalDateTime.now());

            // Mesaj Sayısı Raporu
            long localCount = messageStore.getMessageCount();
            System.out.println("Local Message Count: " + localCount);

            if (self.getPort() == START_PORT) {
                long totalNetworkMessages = localCount;
                System.out.println("--- System Observer Report ---");

                for (NodeInfo n : members) {
                    // Kendim hariç diğerlerine sor
                    if (n.getHost().equals(self.getHost()) && n.getPort() == self.getPort())
                        continue;

                    ManagedChannel channel = null;
                    try {
                        channel = ManagedChannelBuilder.forAddress(n.getHost(), n.getPort())
                                .usePlaintext().build();
                        FamilyServiceGrpc.FamilyServiceBlockingStub stub = FamilyServiceGrpc.newBlockingStub(channel);

                        // RPC Çağrısı
                        family.MessageStats stats = stub.getStats(Empty.newBuilder().build());
                        System.out.printf(" -> Node %d has %d messages%n", n.getPort(), stats.getMessageCount());
                        totalNetworkMessages += stats.getMessageCount();

                    } catch (Exception e) {
                        System.out.println(" -> Node " + n.getPort() + " unreachable");
                    } finally {
                        if (channel != null)
                            channel.shutdownNow();
                    }
                }
                System.out.println("TOTAL NETWORK MESSAGES: " + totalNetworkMessages);
                System.out.println("------------------------------");
            }
            // ----------------------------------------------------

            System.out.println("Members:");

            for (NodeInfo n : members) {
                boolean isMe = n.getHost().equals(self.getHost()) && n.getPort() == self.getPort();
                System.out.printf(" - %s:%d%s%n",
                        n.getHost(),
                        n.getPort(),
                        isMe ? " (me)" : "");
            }
            System.out.println("======================================");
        }, 3, PRINT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
}