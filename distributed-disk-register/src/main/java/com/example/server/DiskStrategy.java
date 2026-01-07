package com.example.server;

import java.io.IOException;

public interface DiskStrategy {
    void writeToDisk(int id, String msg) throws IOException;
    String readFromDisk(int id) throws IOException;
}