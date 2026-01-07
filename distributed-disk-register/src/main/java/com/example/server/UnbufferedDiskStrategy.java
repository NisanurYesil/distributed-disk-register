package com.example.server;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

public class UnbufferedDiskStrategy implements DiskStrategy {
    private final File dir;

    public UnbufferedDiskStrategy(File dir) {
        this.dir = dir;
    }

    @Override
    public void writeToDisk(int id, String msg) throws IOException {
        File file = new File(dir, id + ".msg");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
             FileChannel channel = raf.getChannel()) {

            byte[] data = msg.getBytes(StandardCharsets.UTF_8);

            // "Direct Buffer" kullanımı Zero-Copy ilkesine yakındır.
            // İşletim sistemi belleğine (Kernel Space) doğrudan erişim sağlar.
            ByteBuffer buffer = ByteBuffer.allocateDirect(data.length);
            buffer.put(data);
            buffer.flip(); // Okuma moduna geç

            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }

            // KRİTİK KOMUT: force(true)
            // Veriyi OS cache'inde bekletmeden fiziksel diske zorla yazar.
            // "Unbuffered" olmasını sağlayan temel komut budur.
            channel.force(true);
        }
    }

    @Override
    public String readFromDisk(int id) throws IOException {
        File file = new File(dir, id + ".msg");
        if (!file.exists()) return null;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             FileChannel channel = raf.getChannel()) {

            ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
            channel.read(buffer);
            buffer.flip();

            // Byte buffer'ı String'e çevir
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            return new String(data, StandardCharsets.UTF_8);
        }
    }
}