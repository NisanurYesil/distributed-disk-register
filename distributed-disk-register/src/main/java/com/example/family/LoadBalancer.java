package com.example.family;

import family.NodeInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadBalancer {

    // Round Robin için global sayaç (Sıranın kimde olduğunu tutar)
    private static final AtomicInteger counter = new AtomicInteger(0);

    public static List<NodeInfo> selectNodes(List<NodeInfo> candidates, int count) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Listeyi port numarasına göre sırala (Kararlılık/Stability için)
        // Set yapısı sıralı olmadığı için, her çağrıda listenin aynı sırada olduğundan emin olmalıyız.
        List<NodeInfo> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingInt(NodeInfo::getPort));

        // 2. Round Robin mantığı ile seçim yap
        List<NodeInfo> selected = new ArrayList<>();
        int size = sorted.size();

        // Counter'ı atomik olarak al ve count kadar artır
        int start = counter.getAndAdd(count);

        for (int i = 0; i < count; i++) {
            // Modulo işlemi ile dairesel döngü (listenin sonuna gelince başa dönme)
            int index = Math.abs((start + i) % size);
            selected.add(sorted.get(index));
        }

        return selected;
    }
}