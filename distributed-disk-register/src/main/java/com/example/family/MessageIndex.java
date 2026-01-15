package com.example.family;

import family.NodeInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MessageIndex {
    private static final MessageIndex instance = new MessageIndex();

    // Mesaj ID (Integer) -> Node Adres Listesi (Host:Port)
    private final Map<Integer, List<String>> index = new ConcurrentHashMap<>();

    private MessageIndex() {
    }

    public static MessageIndex getInstance() {
        return instance;
    }

    public void addLocation(int messageId, NodeInfo node) {
        String address = node.getHost() + ":" + node.getPort();
        index.computeIfAbsent(messageId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(address);
    }

    public List<String> getLocations(int messageId) {
        return index.getOrDefault(messageId, Collections.emptyList());
    }

    public void printIndex() {
        System.out.println("\n=== Lider Mesaj İndeksi (Kimde Ne Var?) ===");
        if (index.isEmpty()) {
            System.out.println("(Boş)");
        } else {
            index.forEach((id, nodes) -> {
                System.out.println("Mesaj [" + id + "] -> " + nodes);
            });
        }
        System.out.println("===========================================\n");
    }
}