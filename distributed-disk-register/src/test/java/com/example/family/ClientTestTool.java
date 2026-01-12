package com.example.family;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientTestTool {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 6666;

    public static void main(String[] args) {
        if (args.length == 0) {
            runAutomatedTest();
            return;
        }

        String command = String.join(" ", args);
        sendSingleCommand(command);
    }

    private static void sendSingleCommand(String command) {
        System.out.println("Connecting to " + HOST + ":" + PORT + "...");
        try (Socket socket = new Socket(HOST, PORT);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            System.out.println("Sending: " + command);
            out.println(command);

            String response = in.readLine();
            System.out.println("Response: " + response);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Make sure the Leader Node (Port 6666) is running!");
        }
    }

    private static void runAutomatedTest() {
        System.out.println("=== Running Automated Client Integration Test ===");

        // 1. SET Test
        String setCmd = "SET 999 IntegrationTestValue";
        System.out.println("\n[TEST 1] Sending: " + setCmd);
        String setResponse = sendCommandWithResult(setCmd);
        System.out.println("Result: " + setResponse);

        if (!"OK".equals(setResponse)) {
            System.err.println("FAILED: SET command did not return OK");
            return;
        }

        try {
            Thread.sleep(500);
        } catch (Exception e) {
        }

        // 2. GET Test
        String getCmd = "GET 999";
        System.out.println("\n[TEST 2] Sending: " + getCmd);
        String getResponse = sendCommandWithResult(getCmd);
        System.out.println("Result: " + getResponse);

        if ("IntegrationTestValue".equals(getResponse)) {
            System.out.println("\n✅ TEST PASSED: Data retrieved successfully.");
        } else {
            System.err.println("❌ TEST FAILED: Expected 'IntegrationTestValue', got '" + getResponse + "'");
        }
    }

    private static String sendCommandWithResult(String command) {
        try (Socket socket = new Socket(HOST, PORT);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println(command);
            return in.readLine();

        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
