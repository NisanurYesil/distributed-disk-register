package com.example.server;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class SimpleClient {
    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 6666;

        System.out.println("Simple Client Başlatıldı. Bağlanılıyor: " + host + ":" + port);
        System.out.println("Komutlar: SET <id> <mesaj>, GET <id>, EXIT");

        try (Socket socket = new Socket(host, port);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                Scanner scanner = new Scanner(System.in)) {

            System.out.println("Bağlantı başarılı! Komut girebilirsiniz.");

            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine())
                    break;

                String command = scanner.nextLine().trim();

                if (command.isEmpty())
                    continue;
                if (command.equalsIgnoreCase("EXIT"))
                    break;

                out.println(command);

                // Sunucudan cevabı oku
                String response = in.readLine();
                if (response == null) {
                    System.out.println("Sunucu bağlantısı koptu.");
                    break;
                }
                System.out.println("Sunucu: " + response);
            }

        } catch (IOException e) {
            System.err.println("Bağlantı hatası: " + e.getMessage());
        }
    }
}
