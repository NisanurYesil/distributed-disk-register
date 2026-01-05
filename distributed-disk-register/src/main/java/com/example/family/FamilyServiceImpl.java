package com.example.family;

import family.*;
import io.grpc.stub.StreamObserver;

// MessageStore importuna şu an gerek yok, çünkü hoca mimarisinde bu sınıf üyelik için kullanılmıyor
// import com.example.server.MessageStore;

public class FamilyServiceImpl extends FamilyServiceGrpc.FamilyServiceImplBase {

    private final NodeRegistry registry;
    private final NodeInfo self;

    // HATA ÇÖZÜMÜ: Hocanın orijinal 2 parametreli mimarisine geri döndük.
    // MessageStore parametresini sildik çünkü NodeMain sadece 2 tane gönderiyor.
    public FamilyServiceImpl(NodeRegistry registry, NodeInfo self) {
        this.registry = registry;
        this.self = self;
        this.registry.add(self); // Kendimizi deftere ekliyoruz
    }

    /**
     * Dinamik Üye Katılımı (Task #4):
     * Yeni bir node sisteme girmek istediğinde bu metodu çağırır.
     */
    @Override
    public void join(NodeInfo request, StreamObserver<FamilyView> responseObserver) {
        // 1. Yeni gelen üyeyi listeye ekle (Dinamik Katılımın kalbi burası)
        registry.add(request);

        // 2. Güncel listeyi (snapshot) tüm aile üyeleriyle birlikte geri gönder
        FamilyView view = FamilyView.newBuilder()
                .addAllMembers(registry.snapshot())
                .build();

        responseObserver.onNext(view);
        responseObserver.onCompleted();

        System.out.println("✅ Yeni üye katıldı: " + request.getPort());
    }

    // Orijinal yapıdaki diğer metodlar (heartbeat, leave vb.) aşağıya eklenebilir
}