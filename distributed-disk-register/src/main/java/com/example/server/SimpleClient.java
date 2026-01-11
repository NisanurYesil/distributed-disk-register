package com.example.server;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class SimpleClient {
    public static void main(String[] args) {
        // Liderin dinlediği port: 6666
        try (Socket socket = new Socket("127.0.0.1", 6666);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            String message = "Bu mesaj sadece 2 kisiye gitmeli!";
            writer.write(message);
            writer.newLine();
            writer.flush();

            System.out.println("Mesaj gonderildi: " + message);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
