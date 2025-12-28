package com.example;

import com.example.config.ToleranceConfigReader;

public class NodeMain {
    public static void main(String[] args) {
        try {
            ToleranceConfigReader config = new ToleranceConfigReader();

            System.out.println("Retry Count: " + config.getRetryCount());
            System.out.println("Timeout (ms): " + config.getTimeoutMillis());
            System.out.println("Log Level: " + config.getLogLevel());

        } catch (Exception e) {
            System.err.println("Config dosyası okunamadı: " + e.getMessage());
        }
    }
}