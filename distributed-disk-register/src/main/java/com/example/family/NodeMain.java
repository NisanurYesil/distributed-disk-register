package com.example.family;
import java.util.Collections;
import java.util.ArrayList;
import com.example.config.ToleranceConfigReader; // Config okumak için
import family.Empty;
import family.FamilyServiceGrpc;
import family.FamilyView;
import family.NodeInfo;
import family.ChatMessage;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;


import java.io.IOException;
import java.net.ServerSocket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

public class NodeMain {

    private static final int START_PORT = 5555;
    private static final int PRINT_INTERVAL_SECONDS = 10;

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = findFreePort(START_PORT);

        NodeInfo self = NodeInfo.newBuilder()
                .setHost(host)
                .setPort(port)
                .build();

        NodeRegistry registry = new NodeRegistry();

        // 1. MessageStore başlatma (Task #10)
        com.example.server.MessageStore messageStore = new com.example.server.MessageStore(new ToleranceConfigReader());

        // 2. Servise ve Printer'a bu instance'ı veriyoruz
        FamilyServiceImpl service = new FamilyServiceImpl(registry, self, messageStore);
        Server server = ServerBuilder
                .forPort(port)
                .addService(service) // Şimdi 'service' değişkenini bulabilecek
                .build()
                .start();

        System.out.printf("Node started on %s:%d%n", host, port);

        // Eğer bu ilk node ise (port 5555), TCP 6666'da text dinlesin
        if (port == START_PORT) {
            startLeaderTextListener(registry, self);
        }

        discoverExistingNodes(host, port, registry, self);
        startFamilyPrinter(registry, self, messageStore);
        //startHealthChecker(registry, self);

        server.awaitTermination();




    }

    private static void startLeaderTextListener(NodeRegistry registry, NodeInfo self) {
        // Sadece lider (5555 portlu node) bu methodu çağırmalıdır
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(6666)) {
                System.out.printf("Leader listening for text on TCP %s:%d%n",
                        self.getHost(), 6666);

                while (true) {
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleClientTextConnection(client, registry, self)).start();
                }

            } catch (IOException e) {
                System.err.println("Error in leader text listener: " + e.getMessage());
            }
        }, "LeaderTextListener").start();
    }

    private static void handleClientTextConnection(Socket client,
                                                   NodeRegistry registry,
                                                   NodeInfo self) {
        System.out.println("New TCP client connected: " + client.getRemoteSocketAddress());
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String text = line.trim();
                if (text.isEmpty()) continue;

                long ts = System.currentTimeMillis();

                // Kendi üstüne de yaz
                System.out.println("📝 Received from TCP: " + text);

                ChatMessage msg = ChatMessage.newBuilder()
                        .setText(text)
                        .setFromHost(self.getHost())
                        .setFromPort(self.getPort())
                        .setTimestamp(ts)
                        .build();

                // Tüm family üyelerine broadcast et
                List<NodeInfo> sentTo = broadcastToFamily(registry, self, msg);

                // İndekse kaydet
                for (NodeInfo node : sentTo) {
                    MessageIndex.getInstance().addLocation(ts, node);
                }

                // Konsola mevcut durumu yaz (Görmek için)
                MessageIndex.getInstance().printIndex();
            }

        } catch (IOException e) {
            System.err.println("TCP client handler error: " + e.getMessage());
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    // DÖNÜŞ TİPİ DEĞİŞTİ: void -> List<NodeInfo>
    private static List<NodeInfo> broadcastToFamily(NodeRegistry registry,
                                                    NodeInfo self,
                                                    ChatMessage msg) {

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
        List<NodeInfo> successfulNodes = new ArrayList<>(); // EKLENDİ

        System.out.println("Replikasyon Hedefleri Seçildi (" + countToSend + " kişi):");

        // 6. Sadece seçilenlere gönder
        for (NodeInfo n : selectedNodes) {
            ManagedChannel channel = null;
            try {
                channel = ManagedChannelBuilder
                        .forAddress(n.getHost(), n.getPort())
                        .usePlaintext()
                        .build();

                FamilyServiceGrpc.FamilyServiceBlockingStub stub =
                        FamilyServiceGrpc.newBlockingStub(channel);

                stub.receiveChat(msg);

                System.out.printf(" -> Mesaj gönderildi: %s:%d%n", n.getHost(), n.getPort());
                successfulNodes.add(n); // EKLENDİ

            } catch (Exception e) {
                System.err.printf(" -> GÖNDERİLEMEDİ %s:%d (%s)%n",
                        n.getHost(), n.getPort(), e.getMessage());
            } finally {
                if (channel != null) channel.shutdownNow();
            }
        }
        return successfulNodes; // EKLENDİ
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

                FamilyServiceGrpc.FamilyServiceBlockingStub stub =
                        FamilyServiceGrpc.newBlockingStub(channel);

                FamilyView view = stub.join(self);
                registry.addAll(view.getMembersList());

                System.out.printf("Joined through %s:%d, family size now: %d%n",
                        host, port, registry.snapshot().size());

            } catch (Exception ignored) {
            } finally {
                if (channel != null) channel.shutdownNow();
            }
        }
    }

    private static void startFamilyPrinter(NodeRegistry registry, NodeInfo self, com.example.server.MessageStore messageStore) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        // MessageStore parametre olarak geldiği için yeniden oluşturmuyoruz

        scheduler.scheduleAtFixedRate(() -> {
            List<NodeInfo> members = registry