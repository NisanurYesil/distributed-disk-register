package com.example.family;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class SimpleClient {
    public static void main(String[] args) {
        String host = "127.0.0.1"; // Veya Lider'in IP'si
        int port = 6666;

        System.out.println("HaToKuSe Simple Client Started. Connecting to " + host + ":" + port);
        System.out.println("Komutlar: SET <id> <msg>, GET <id>, EXIT");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                String command = scanner.nextLine();

                if (command.equalsIgnoreCase("EXIT"))
                    break;

                try (Socket socket = new Socket(host, port);
                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    out.println(command);

                    // Sunucudan gelen yanıtı oku
                    String response = in.readLine();
                    if (response != null) {
                        System.out.println("Sunucu Yanıtı: " + response);
                    } else {
                        System.out.println("Sunucu yanıt vermedi.");
                    }

                } catch (Exception e) {
                    System.out.println("Bağlantı hatası: " + e.getMessage());
                }
            }
        }
    }
}
