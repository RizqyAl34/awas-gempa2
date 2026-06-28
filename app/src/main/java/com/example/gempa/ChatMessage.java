package com.example.gempa;

// Model satu baris chat — bisa dari user atau bot
public class ChatMessage {

    public static final int TYPE_USER = 0;
    public static final int TYPE_BOT  = 1;
    public static final int TYPE_LOADING = 2; // bubble "mengetik..."

    private String message;
    private int type;
    private long timestamp;

    public ChatMessage(String message, int type) {
        this.message   = message;
        this.type      = type;
        this.timestamp = System.currentTimeMillis();
    }

    public String getMessage()   { return message; }
    public int    getType()      { return type; }
    public long   getTimestamp() { return timestamp; }

    public void setMessage(String msg) { this.message = msg; }
}