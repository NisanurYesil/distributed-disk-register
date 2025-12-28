package com.example.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ToleranceConfigReader {
    private final Properties props = new Properties();

    public ToleranceConfigReader() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("tolerans.conf")) {
            if (input == null) {
                throw new IOException("tolerans.conf bulunamadı!");
            }
            props.load(input);
        }
    }

    public int getRetryCount() {
        return Integer.parseInt(props.getProperty("retry", "1"));
    }

    public int getTimeoutMillis() {
        return Integer.parseInt(props.getProperty("timeout", "1000"));
    }

    public String getLogLevel() {
        return props.getProperty("logLevel", "WARN");
    }
}