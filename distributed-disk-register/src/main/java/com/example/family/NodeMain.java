package com.example.family;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.ArrayList;
import com.example.config.ToleranceConfigReader; // Config okumak için
// imports removed
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
        // --- FIX 1: Dinamik IP Tespiti (LAN IP) ---
        // Eğer komut satırından IP verilirse onu kullan yoksa bul
        String host = getLanIp();

        // Komut satırından hedef Lider IP'si gelebilir
        // Örn: java NodeMain [TARGET_LEADER_IP]
        String targetLeaderIp = null;
        if (args.length > 0) {
            targetLeaderIp = args[0];
            System.out.println("Hedef Lider IP girildi: " + targetLeaderIp);
        }
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

        // Eğer bu ilk node ise (port 5555) VE hedef lider yoksa, TCP 6666'da text
        // dinlesin
        // Hedef lider varsa muhtemelen 2. PC'deyizdir, yine de 5555 olma ihtimali var.
        // Basit kural: 5555 isek Lider adayıyızdır.
        if (port == START_PORT) {
            startLeaderTextListener(registry, self, messageStore);
        }

        discoverExistingNodes(host, port, registry, self, targetLeaderIp);
        startFamilyPrinter(registry, self, messageStore);
        // startHealthChecker(registry, self);

        server.awaitTermination();

    }

    // Gerçek LAN IP'sini bulmaya çalışır (192.168... gibi)
    private static String getLanIp() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface
                    .getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp())
                    continue;

                java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    // IPv4 ve link-local olmayan adres
                    if (addr instanceof java.net.Inet4Address && !addr.isLinkLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "127.0.0.1"; // Bulamazsa localhost
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

                            // 1. Kendi üzerine yazma işlemini İPTAL ET (Lider sadece yönlendirici olsun)
                            // String result = messageStore.put(id, msgContent);
                            String result = "OK"; 

                            // 2. Diğer üyelere replike et (StorageService ile)
                            List<NodeInfo> sentTo = broadcastStoreToFamily(registry, self, id, msgContent);

                            // 3. İndekse kendini EKLEME (Çünkü kaydetmedik)
                            // MessageIndex.getInstance().addLocation(id, self);
                            for (NodeInfo node : sentTo) {
                                MessageIndex.getInstance().addLocation(id, node); // Üyelerde var
                            }

                            // 4. İstemciye yanıt dön (TOLERANCE KONTROLÜ)
                            int tolerance = 1; 
                            try {
                                ToleranceConfigReader config = new ToleranceConfigReader();
                                tolerance = config.getTolerance();
                            } catch (Exception e) {
                                System.err.println("Config okunamadı, varsayılan tolerans(1) kullanılıyor.");
                            }

                            if (sentTo.size() >= tolerance) {
                                PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                                out.println("OK");
                            } else {
                                PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                                out.println("ERROR: Tolerance not met. Required: " + tolerance + ", Achieved: " + sentTo.size());
                            }

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

        StoredMessage msg = StoredMessage.newBuilder()
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
            NodeInfo self,
            String targetLeaderIp) {

        List<String> addressesToCheck = new ArrayList<>();
        long scanTimeoutMs = 2000; // Artık paraleliz, uzun bekleyebiliriz (2sn)

        // 1. Hedef Lider IP verilmişse sadece onu dene
        if (targetLeaderIp != null && !targetLeaderIp.isEmpty()) {
            System.out.println("Doğrudan hedef lidere bağlanılıyor: " + targetLeaderIp);
            if (tryJoin(targetLeaderIp, START_PORT, self, registry, 5000)) {
                return; // Başarılı
            }
        }
        // 2. Hedef yoksa ve Localhost değilsek -> LAN Taraması IP Listesi Hazırla
        else if (!host.equals("127.0.0.1") && !host.equals("localhost")) {
            System.out.println("Hedef belirtilmedi. LAN taranıyor (Paralel taranıyor, maksimum 3sn sürecek)...");
            String prefix = host.substring(0, host.lastIndexOf(".") + 1);

            // Tüm subneti ekle
            for (int i = 1; i < 255; i++) {
                String potentialIp = prefix + i;
                if (!potentialIp.equals(host)) {
                    addressesToCheck.add(potentialIp);
                }
            }
        }

        // --- PARALEL TARAMA BAŞLIYOR ---
        final java.util.concurrent.atomic.AtomicBoolean joined = new java.util.concurrent.atomic.AtomicBoolean(false);

        if (!addressesToCheck.isEmpty() && targetLeaderIp == null) {
            // Sadece Lider portunu (5555) tara
            ExecutorService executor = Executors.newFixedThreadPool(100); // 100 Thread ile hızlı tara

            for (String targetHost : addressesToCheck) {
                executor.submit(() -> {
                    if (joined.get())
                        return; // Biri bulduysa diğerleri dursun (CPU tasarrufu)

                    // tryJoin zaten exception handle ediyor
                    if (tryJoin(targetHost, START_PORT, self, registry, scanTimeoutMs)) {
                        joined.set(true);
                    }
                });
            }

            executor.shutdown();
            try {
                // Timeout süresinden biraz fazla bekle ki tüm threadler dönebilsin
                executor.awaitTermination(scanTimeoutMs + 1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                System.err.println("Tarama kesildi.");
            }
        }

        if (joined.get()) {
            return; // Bulduk
        }

        // Eğer LAN'da bulamadıysak ve Localhost isek veya fallback gerekirse yerel
        // portları tara (Burayı da hızlandırmak için paralel yapmıyoruz, zaten az port
        // var ve localhost hızlıdır)
        System.out.println("Ağda Lider bulunamadı veya yerel moddayız. Yerel portlar taranıyor...");
        for (int p = START_PORT; p < selfPort; p++) {
            if (tryJoin(host, p, self, registry, 200)) { // Yerel tarama hızlı olsun
                // joined = true; // gerek yok, return yeterli
                // Birine bağlandık, ama belki diğerlerini de görmek isteriz?
                // Orijinal kodda break yoktu, ama mantıken bir cluster'a dahil olduk.
            }
        }
    }

    private static boolean tryJoin(String targetHost, int port, NodeInfo self, NodeRegistry registry, long timeoutMs) {
        ManagedChannel channel = null;
        try {
            // Timeout ayarı olmadığı için varsayılanı kullanır, LAN'da hızlıdır.
            // Ancak olmayan IP'ler için biraz bekletebilir.
            channel = ManagedChannelBuilder
                    .forAddress(targetHost, port)
                    .usePlaintext()
                    .build();

            FamilyServiceGrpc.FamilyServiceBlockingStub stub = FamilyServiceGrpc.newBlockingStub(channel);

            // Bağlantı denemesi (biraz riskli, timeout koymak lazım ama gRPC managed
            // channel handle eder)
            FamilyView view = stub.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS).join(self);
            registry.addAll(view.getMembersList());

            System.out.printf("BAŞARILI! %s:%d adresindeki aileye katılındı. Aile boyutu: %d%n",
                    targetHost, port, registry.snapshot().size());

            return true;

        } catch (Exception e) {
            // System.out.println("... " + targetHost + " yanıt vermedi.");
            return false;
        } finally {
            if (channel != null)
                channel.shutdownNow();
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
                        MessageStats stats = stub.getStats(Empty.newBuilder().build());
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