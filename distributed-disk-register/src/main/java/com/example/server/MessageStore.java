package com.example.server;

import com.example.config.ToleranceConfigReader;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MessageStore {
    // RAM Önbelleği (Hızlı okuma için)
    private final Map<Integer, String> memoryMap = new ConcurrentHashMap<>();

    // Seçilen Disk Stratejisi (Arayüz)
    private final DiskStrategy diskStrategy;

    // Kayıt klasörü
    private final File dir = new File("messages");

    // CONSTRUCTOR: Config dosyasını alıp stratejiyi belirler
    public MessageStore(ToleranceConfigReader config) {
        // Klasör yoksa oluştur
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Config'den io_type değerini oku (Hata almamak için getIoType metodunu ConfigReader'a eklemelisin)
        String ioType = config.getIoType();

        // Seçime göre stratejiyi yükle (Strategy Pattern)
        if ("unbuffered".equalsIgnoreCase(ioType)) {
            this.diskStrategy = new UnbufferedDiskStrategy(dir);
            System.out.println("LOG: Disk Modu -> UNBUFFERED (Güvenli/Direct IO)");
        } else {
            this.diskStrategy = new BufferedDiskStrategy(dir);
            System.out.println("LOG: Disk Modu -> BUFFERED (Hızlı/Standart IO)");
        }
    }

    // Mesaj Kaydetme (PUT)
    public String put(int id, String msg) {
        // 1. Önce RAM'e yaz (Hız için)
        memoryMap.put(id, msg);

        // 2. Seçilen strateji ile diske yaz (Güvenlik için)
        try {
            diskStrategy.writeToDisk(id, msg);
            return "OK";
        } catch (IOException e) {
            e.printStackTrace();
            return "ERROR: Disk yazma hatası (" + e.getMessage() + ")";
        }
    }

    // Mesaj Okuma (GET)
    public String get(int id) {
        // 1. Önce RAM'e bak (En hızlısı)
        if (memoryMap.containsKey(id)) {
            return memoryMap.get(id);
        }

        // 2. RAM'de yoksa diskten oku (Crash sonrası kurtarma)
        try {
            String msg = diskStrategy.readFromDisk(id);
            if (msg != null) {
                // Hazır okumuşken RAM'e de geri yükle (Cache warming)
                memoryMap.put(id, msg);
                return msg;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "ERROR: Disk okuma hatası";
        }

        return "NOT_FOUND";
    }
}