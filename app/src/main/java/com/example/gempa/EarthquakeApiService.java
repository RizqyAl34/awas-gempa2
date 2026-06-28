package com.example.gempa;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface EarthquakeApiService {

    // --------------------------------------------------------
    // Endpoint 1: 1 gempa terbaru M≥5.0 (untuk hero card)
    // --------------------------------------------------------
    @GET("DataMKG/TEWS/autogempa.json")
    Call<EarthquakeResponse.AutoGempaResponse> getLatestEarthquake();

    // --------------------------------------------------------
    // Endpoint 2: 15 gempa terkini M≥5.0
    // --------------------------------------------------------
    @GET("DataMKG/TEWS/gempaterkini.json")
    Call<EarthquakeResponse.GempaTerkiniResponse> getRecentEarthquakes();

    // --------------------------------------------------------
    // Endpoint 3: Gempa yang dirasakan (semua magnitude)
    // URL: https://data.bmkg.go.id/DataMKG/TEWS/gempadirasakan.json
    // Ini berisi gempa yang dilaporkan dirasakan masyarakat,
    // termasuk gempa kecil di bawah M5.0
    // --------------------------------------------------------
    @GET("DataMKG/TEWS/gempadirasakan.json")
    Call<EarthquakeResponse.GempaTerkiniResponse> getGempaDirasakan();

    // --------------------------------------------------------
    // Endpoint 4: BMKG FDSN Web Service — SEMUA gempa
    // tanpa batas magnitude, data real-time lengkap
    // Base URL berbeda: https://bmkg-content-inatews.storage.googleapis.com/
    // Kita pakai endpoint publik BMKG yang lain:
    // https://data.bmkg.go.id/DataMKG/TEWS/index.json
    // --------------------------------------------------------
    @GET("DataMKG/TEWS/index.json")
    Call<EarthquakeResponse.GempaTerkiniResponse> getAllEarthquakes();
}