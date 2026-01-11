package com.example.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClusterGateway {
    private final int port;
    private final ExecutorService pool = Executors.newFixedThreadPool(32);
    private final MessageStore messageStore;

    public ClusterGateway(int port) throws IOException {
        this.port = port;
        this.messageStore = new MessageStore(new com.example.config.ToleranceConfigReader());
    }

    public void start() throws IOException {
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("[Leader] Listening on port " + port);
            while (true) {
                Socket client = server.accept();
                pool.submit(() -> handleClient(client));
            }
        }
    }

    private void handleClient(Socket client) {
        String remote = client.getRemoteSocketAddress().toString();
        System.out.println("[Leader] Accepted connection from " + remote);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                Command cmd = CommandParser.parse(line);
                String result = cmd.execute(messageStore);
                writer.write(result);
                writer.newLine();
                writer.flush();
            }
        } catch (Exception e) {
            System.err.println("[Leader] Error with client " + remote + ": " + e.getMessage());
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    public static void main(String[] args) throws IOException {
        new ClusterGateway(6666).start();
    }
}


