package com.example.family;

import family.*;
import io.grpc.stub.StreamObserver;
import com.example.server.MessageStore;

public class FamilyServiceImpl extends FamilyServiceGrpc.FamilyServiceImplBase {

    private final NodeRegistry registry;
    private final NodeInfo self;
    private final MessageStore messageStore;

    public FamilyServiceImpl(NodeRegistry registry, NodeInfo self, MessageStore messageStore) {
        this.registry = registry;
        this.self = self;
        this.messageStore = messageStore;
        this.registry.add(self); // Kendimizi deftere ekliyoruz
    }

    /**
     * Dinamik Üye Katılımı (Task #4):
     * Yeni bir node sisteme girmek istediğinde bu metodu çağırır.
     */
    @Override
    public void join(NodeInfo request, StreamObserver<FamilyView> responseObserver) {
        // 1. Yeni gelen üyeyi listeye ekle
        registry.add(request);

        // 2. Güncel listeyi geri gönder
        FamilyView view = FamilyView.newBuilder()
                .addAllMembers(registry.snapshot())
                .build();

        responseObserver.onNext(view);
        responseObserver.onCompleted();

        System.out.println("✅ Yeni üye katıldı: " + request.getPort());
    }

    /**
     * Replication Logic (Task #10):
     * Gelen mesajı diske kaydeder.
     */
    @Override
    public void receiveChat(ChatMessage request, StreamObserver<Empty> responseObserver) {
        System.out.printf("📩 Mesaj alındı (%s:%d): %s%n",
                request.getFromHost(), request.getFromPort(), request.getText());

        // Mesajı diske yaz
        // ID olarak timestamp'in bir kısmını kullanıyoruz (demo için yeterli)
        int msgId = (int) (System.currentTimeMillis() & 0xFFFFFFF);
        
        try {
            messageStore.put(msgId, request.getText());
        } catch (Exception e) {
            System.err.println("Mesaj kaydedilemedi: " + e.getMessage());
        }

        // Başarılı yanıt dön
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }
}