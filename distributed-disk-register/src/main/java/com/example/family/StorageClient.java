package com.example.family;
import family.NodeInfo;
import family.StoredMessage;
import family.MessageId;
import family.StorageServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class StorageClient {

    public static boolean sendStoreRPC(NodeInfo node, StoredMessage msg) {
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder.forAddress(node.getHost(), node.getPort())
                    .usePlaintext()
                    .build();

            StorageServiceGrpc.StorageServiceBlockingStub stub =
                    StorageServiceGrpc.newBlockingStub(channel);

            stub.store(msg); // RPC çağrısı
            return true;
        } catch (Exception e) {
            System.err.printf("Store RPC failed bunnu değiştirmd to %s:%d (%s)%n",
                    node.getHost(), node.getPort(), e.getMessage());
            return false;
        } finally {
            if (channel != null) channel.shutdownNow();
        }
    }

    public static String sendRetrieveRPC(NodeInfo node, int id) {
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder.forAddress(node.getHost(), node.getPort())
                    .usePlaintext()
                    .build();

            StorageServiceGrpc.StorageServiceBlockingStub stub =
                    StorageServiceGrpc.newBlockingStub(channel);

            MessageId req = MessageId.newBuilder().setId(id).build();
            return stub.retrieve(req).getText();
        } catch (Exception e) {
            System.err.printf("Retrieve RPC failed to %s:%d (%s)%n",
                    node.getHost(), node.getPort(), e.getMessage());
            return null;
        } finally {
            if (channel != null) channel.shutdownNow();
        }
    }
}
