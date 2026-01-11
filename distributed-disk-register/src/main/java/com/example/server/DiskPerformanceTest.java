package com.example.server;

import java.io.File;
import java.io.IOException;

public class DiskPerformanceTest {

    private static final int MESSAGE_COUNT = 50_000; // Test edilecek mesaj sayısı
    private static final String TEST_DIR = "benchmark_data";

    public static void main(String[] args) throws IOException {
        System.out.println("=== Disk IO Performans Testi Başlıyor ===");
        System.out.println("Yazılacak Mesaj Sayısı: " + MESSAGE_COUNT);

        File dir = new File(TEST_DIR);
        if (!dir.exists()) dir.mkdirs();

        // 1. BUFFERED IO TESTİ
        System.out.println("\n--- BufferedDiskStrategy Test Ediliyor ---");
        DiskStrategy buffered = new BufferedDiskStrategy(dir);
        long start = System.currentTimeMillis();

        for (int i = 0; i < MESSAGE_COUNT; i++) {
            buffered.writeToDisk(i, "Bu bir test mesajıdır, içerik numarası: " + i);
        }

        long end = System.currentTimeMillis();
        printResults("Buffered IO", end - start);

        // Dosyaları temizle (diğer test için disk boşalsın)
        // cleanDirectory(dir);

        // 2. UNBUFFERED IO TESTİ
        System.out.println("\n--- UnbufferedDiskStrategy Test Ediliyor ---");
        DiskStrategy unbuffered = new UnbufferedDiskStrategy(dir);
        start = System.currentTimeMillis();

        for (int i = 0; i < MESSAGE_COUNT; i++) {
            unbuffered.writeToDisk(i + MESSAGE_COUNT, "Bu bir test mesajıdır, içerik numarası: " + i);
        }

        end = System.currentTimeMillis();
        printResults("Unbuffered IO", end - start);

        // 3. ZERO-COPY IO TESTİ (YENİ)
        System.out.println("\n--- ZeroCopyDiskStrategy Test Ediliyor ---");
        DiskStrategy zeroCopy = new ZeroCopyDiskStrategy(dir);
        start = System.currentTimeMillis();

        for (int i = 0; i < MESSAGE_COUNT; i++) {
            zeroCopy.writeToDisk(i, "Test mesaji " + i);
        }
        end = System.currentTimeMillis();
        printResults("Zero Copy IO", end - start);

        System.out.println("\nTest tamamlandı. Dosyalar '" + TEST_DIR + "' klasöründe.");
    }

    private static void printResults(String type, long durationMs) {
        double seconds = durationMs / 1000.0;
        double throughput = MESSAGE_COUNT / seconds;

        System.out.println(type + " Süre: " + durationMs + " ms");
        System.out.printf(type + " Hız : %.2f mesaj/saniye%n", throughput);
    }
}