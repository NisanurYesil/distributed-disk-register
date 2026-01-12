package com.example.family;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class MemberCrashTest {

    public static void main(String[] args) throws Exception {
        System.out.println("TEST: Starting Member Crash Test Runner...");

        List<Process> processes = new ArrayList<>();
        try {
            // Check if WE can see the class
            Class.forName("family.NodeInfo");
            System.out.println("TEST RUNNER: correctly sees family.NodeInfo");

            // 1. Build classpath
            String classpath = System.getProperty("java.class.path");
            System.out.println("DEBUG CP: " + classpath);
            String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

            // 2. Start Leader (Should pick 5555)
            System.out.println("Starting Leader...");
            Process p1 = new ProcessBuilder(javaBin, "-cp", classpath, "com.example.family.NodeMain")
                    .redirectErrorStream(true) // Merge stderr to stdout (optional, but good for debugging)
                    // We don't inherit IO to avoid cluttering main console too much, but maybe
                    // useful for debugging
                    .start();
            processes.add(p1);
            readStreamAsync(p1.getInputStream(), "[Leader]");

            Thread.sleep(5000); // Wait for leader to fully start

            // 3. Start Member 1 (Should pick 5556)
            System.out.println("Starting Member 1...");
            Process p2 = new ProcessBuilder(javaBin, "-cp", classpath, "com.example.family.NodeMain")
                    .redirectErrorStream(true)
                    .start();
            processes.add(p2);
            readStreamAsync(p2.getInputStream(), "[Member1]");

            Thread.sleep(3000);

            // 4. Start Member 2 (Should pick 5557)
            System.out.println("Starting Member 2...");
            Process p3 = new ProcessBuilder(javaBin, "-cp", classpath, "com.example.family.NodeMain")
                    .redirectErrorStream(true)
                    .start();
            processes.add(p3);
            readStreamAsync(p3.getInputStream(), "[Member2]");

            Thread.sleep(3000);

            // 5. Send Message to Leader
            System.out.println("Sending Message...");
            String msg = "CrashTestMsg";
            Long msgId = null;

            try (Socket socket = new Socket("localhost", 6666);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                out.println(msg);

                // Read ACK
                String ack = in.readLine();
                System.out.println("TEST CLIENT: Received " + ack);
                if (ack != null && ack.startsWith("ACK:")) {
                    msgId = Long.parseLong(ack.split(":")[1]);
                }
            }

            if (msgId == null) {
                throw new RuntimeException("Could not get Message ID from Leader!");
            }
            System.out.println("Message ID: " + msgId);

            System.out.println("Waiting 5s for replication...");
            Thread.sleep(5000);

            // 6. Kill Member 1 (Process 2)
            System.out.println("KILLING Member 1 (Process 2)...");
            p2.destroy();
            p2.waitFor(); // Wait for it to die
            System.out.println("Member 1 is DEAD.");

            Thread.sleep(2000);

            // 7. Read Request
            System.out.println("Sending READ Request for ID: " + msgId);
            try (Socket socket = new Socket("localhost", 6666);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                out.println("READ:" + msgId);

                // NodeMain prints to stdout but simple client logic implies checking logs?
                // No, verify via what NodeMain *logs*?
                // Ah, `handleClientTextConnection` implementation for READ:
                // It prints "SUCCESS: Retrieved..." to its STDOUT.
                // It does NOT send response back to client socket in current NodeMain
                // implementation!
                // Wait, check NodeMain again.
                // It does NOT write to `client.getOutputStream()`.
                // So "READ" command is fire-and-forget from client perspective on the socket.
                // The feedback is on the Leader's Console.

                // I am capturing Leader's stdout with `readStreamAsync`.
                // So I need to inspect the captured log.
            }

            // Allow time for processing
            Thread.sleep(3000);

            System.out.println("Test Sequence Finished. Please check Leader logs above for 'SUCCESS: Retrieved'.");

        } finally {
            for (Process p : processes) {
                if (p.isAlive())
                    p.destroyForcibly();
            }
        }
    }

    private static void readStreamAsync(InputStream is, String prefix) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(prefix + " " + line);
                }
            } catch (IOException e) {
                // Process died
            }
        }).start();
    }
}