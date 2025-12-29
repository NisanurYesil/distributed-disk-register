package com.example.server;


import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class MessageStore {
    private static final Map<Integer, String> map = new ConcurrentHashMap<>();
    private static final File dir = new File("messages");

    static {
        if (!dir.exists()) dir.mkdirs();
    }

    public static String put(int id, String msg) {
        map.put(id, msg);
        // Diskte dosya oluştur
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(dir, id + ".msg")))) {
            writer.write(msg);
        } catch (IOException e) {
            e.printStackTrace();
            return "ERROR: Disk yazma hatası";
        }
        return "OK";
    }

    public static String get(int id) {
        // Önce map’ten oku
        String msg = map.get(id);
        if (msg != null) return msg;

        // Diskten oku
        File file = new File(dir, id + ".msg");
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                return reader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
                return "ERROR: Disk okuma hatası";
            }
        }
        return "NOT_FOUND";
    }
}