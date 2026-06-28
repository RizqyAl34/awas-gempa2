package com.example.gempa;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface GeminiApiService {

    // Gemini 1.5 Flash — gratis, cepat, cukup untuk chatbot
    // Ganti YOUR_API_KEY dengan API key dari aistudio.google.com
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    Call<GeminiResponse> generateContent(
            @Query("key") String apiKey,
            @Body GeminiRequest request
    );
}