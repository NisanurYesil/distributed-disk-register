package com.example.server;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

public class ZeroCopyDiskStrategy implements DiskStrategy {

    private final File dir;

    public ZeroCopyDiskStrategy(File dir) {
        this.dir = dir;
    }

    @Override
    public void writeToDisk(int id, String msg) throws IOException {
        File file = new File(dir, id + ".msg");

        // MappedByteBuffer kullanarak Zero-Copy yazma
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
             FileChannel channel = raf.getChannel()) {

            byte[] data = msg.getBytes(StandardCharsets.UTF_8);

            // Dosyayı belleğe haritala (Kernel Space)
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, data.length);

            // Veriyi doğrudan haritalanmış belleğe yaz
            buffer.put(data);

            // force() çağrısı veriyi diske zorlar (Unbuffered gibi davranmasını istersen açabilirsin)
            // buffer.force();
        }
    }

    @Override
    public String readFromDisk(int id) throws IOException {
        File file = new File(dir, id + ".msg");
        if (!file.exists()) return null;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             FileChannel channel = raf.getChannel()) {

            long size = channel.size();
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, size);

            byte[] data = new byte[(int) size];
            buffer.get(data);

            return new String(data, StandardCharsets.UTF_8);
        }
    }
}