package com.example.server;


public class CommandParser {
    public static Command parse(String line) {
        String[] parts = line.trim().split(" ", 3);
        if (parts[0].equalsIgnoreCase("SET") && parts.length == 3) {
            return new SetCommand(Integer.parseInt(parts[1]), parts[2]);
        } else if (parts[0].equalsIgnoreCase("GET") && parts.length == 2) {
            return new GetCommand(Integer.parseInt(parts[1]));
        } else {
            return (store) -> "ERROR: Geçersiz komut";
        }
    }
}

interface Command {
    String execute(MessageStore store);
}

class SetCommand implements Command {
    private final int id;
    private final String msg;
    public SetCommand(int id, String msg) { this.id = id; this.msg = msg; }

    @Override
    public String execute(MessageStore store) {
        return store.put(id, msg);
    }
}

class GetCommand implements Command {
    private final int id;
    public GetCommand(int id) { this.id = id; }

    @Override
    public String execute(MessageStore store) {
        return store.get(id);
    }
}