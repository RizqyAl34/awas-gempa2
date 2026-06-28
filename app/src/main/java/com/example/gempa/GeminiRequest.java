package com.example.gempa;

import com.google.gson.annotations.SerializedName;
import java.util.List;

// ============================================================
// Model request untuk Gemini API
// Struktur: { "contents": [ { "parts": [ { "text": "..." } ] } ] }
// ============================================================

public class GeminiRequest {

    @SerializedName("contents")
    public List<Content> contents;

    @SerializedName("generationConfig")
    public GenerationConfig generationConfig;

    public GeminiRequest(List<Content> contents) {
        this.contents = contents;
        this.generationConfig = new GenerationConfig();
    }

    public static class Content {
        @SerializedName("role")
        public String role; // "user" atau "model"

        @SerializedName("parts")
        public List<Part> parts;

        public Content(String role, String text) {
            this.role = role;
            this.parts = List.of(new Part(text));
        }
    }

    public static class Part {
        @SerializedName("text")
        public String text;

        public Part(String text) {
            this.text = text;
        }
    }

    public static class GenerationConfig {
        @SerializedName("maxOutputTokens")
        public int maxOutputTokens = 1024;

        @SerializedName("temperature")
        public float temperature = 0.7f;
    }
}