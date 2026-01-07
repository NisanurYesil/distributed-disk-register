package com.example.server;

import java.io.*;

public class BufferedDiskStrategy implements DiskStrategy {
    private final File dir;

    public BufferedDiskStrategy(File dir) {
        this.dir = dir;
    }

    @Override
    public void writeToDisk(int id, String msg) throws IOException {
        File file = new File(dir, id + ".msg");
        // BufferedWriter otomatik olarak bellekte tamponlama (buffer) yapar
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(msg);
        }
    }

    @Override
    public String readFromDisk(int id) throws IOException {
        File file = new File(dir, id + ".msg");
        if (!file.exists()) return null;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.readLine();
        }
    }
}