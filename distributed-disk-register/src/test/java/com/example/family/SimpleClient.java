package com.example.family;

import java.io.*;
import java.net.Socket;

public class SimpleClient {
    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 6666;

        if (args.length < 1) {
            System.out.println("Usage: java SimpleClient <message>");
            return;
        }

        String message = args[0];

        try (Socket socket = new Socket(host, port);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            System.out.println("Connected to " + host + ":" + port);
            out.println(message);
            System.out.println("Sent: " + message);

            // The leader doesn't necessarily reply on the same socket for the chat message
            // immediately
            // unless it's a READ command or if we modified it to echo.
            // But for this test, we just want to send.

            Thread.sleep(1000); // Give it a moment
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
