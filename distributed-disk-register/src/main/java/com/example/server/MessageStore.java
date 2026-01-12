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

    private final File dir;

    // CONSTRUCTOR: Config dosyasını alıp stratejiyi belirler
    public MessageStore(ToleranceConfigReader config, int port) {
        // Klasör yoksa oluştur
        this.dir = new File("messages_" + port);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String ioType = config.getIoType();

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
        memoryMap.put(id, msg);

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
        if (memoryMap.containsKey(id)) {
            return memoryMap.get(id);
        }

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

    public long getMessageCount() {
        if (dir.exists() && dir.isDirectory()) {
            String[] files = dir.list();
            return files != null ? files.length : 0;
        }
        return 0;
    }
}