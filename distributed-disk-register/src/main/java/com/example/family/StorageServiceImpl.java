package com.example.family;

import family.*;
import io.grpc.stub.StreamObserver;
import com.example.server.MessageStore;

public class StorageServiceImpl extends StorageServiceGrpc.StorageServiceImplBase {
    private final MessageStore messageStore;

    public StorageServiceImpl(MessageStore messageStore) {
        this.messageStore = messageStore;
    }
    @Override
    public void store(StoredMessage request, StreamObserver<StoreResult> responseObserver) {
        try {
            MessageStore.put(request.getId(), request.getText());
            // setOk(true) yerine setStatus("OK") kullanmalısın
            responseObserver.onNext(StoreResult.newBuilder().setStatus("OK").build());
        } catch (Exception e) {
            responseObserver.onNext(StoreResult.newBuilder().setStatus("ERROR").build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void retrieve(MessageId request, StreamObserver<StoredMessage> responseObserver) {
        try {
            // HATA DÜZELTME: 'read' yerine 'get' kullanıyoruz
            String content = MessageStore.get(request.getId());

            StoredMessage msg = StoredMessage.newBuilder()
                    .setId(request.getId())
                    .setText(content)
                    .build();

            responseObserver.onNext(msg);
        } catch (Exception e) {
            responseObserver.onError(e);
        }
        responseObserver.onCompleted();
    }
}