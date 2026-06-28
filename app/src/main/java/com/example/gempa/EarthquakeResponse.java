package com.example.gempa;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class EarthquakeResponse {

    // --------------------------------------------------------
    // autogempa.json — 1 gempa terbaru M≥5.0
    // --------------------------------------------------------
    public static class AutoGempaResponse {
        @SerializedName("Infogempa")
        public InfoGempa Infogempa;
    }

    public static class InfoGempa {
        @SerializedName("gempa")
        public GempaData gempa;
    }

    // --------------------------------------------------------
    // gempaterkini.json / gempadirasakan.json — list gempa
    // Struktur JSON keduanya sama persis
    // --------------------------------------------------------
    public static class GempaTerkiniResponse {
        @SerializedName("Infogempa")
        public InfoGempaList Infogempa;
    }

    public static class InfoGempaList {
        @SerializedName("gempa")
        public List<GempaData> gempa;
    }

    // --------------------------------------------------------
    // Model 1 data gempa — field sesuai JSON BMKG
    // --------------------------------------------------------
    public static class GempaData {

        @SerializedName("Tanggal")
        public String Tanggal;

        @SerializedName("Jam")
        public String Jam;

        @SerializedName("DateTime")
        public String DateTime;

        @SerializedName("Coordinates")
        public String Coordinates;

        @SerializedName("Lintang")
        public String Lintang;

        @SerializedName("Bujur")
        public String Bujur;

        @SerializedName("Magnitude")
        public String Magnitude;

        @SerializedName("Kedalaman")
        public String Kedalaman;

        @SerializedName("Wilayah")
        public String Wilayah;

        @SerializedName("Dirasakan")
        public String Dirasakan;

        // Magnitude sebagai float, return 0 jika gagal
        public float getMagnitudeValue() {
            try { return Float.parseFloat(Magnitude); }
            catch (Exception e) { return 0f; }
        }

        // Semua magnitude ditampilkan, tidak ada filter minimum
        // KUAT >= 6.0 | SEDANG 4.0-5.9 | RINGAN < 4.0
        public String getSeverityLevel() {
            float mag = getMagnitudeValue();
            if (mag >= 6.0f) return "KUAT";
            else if (mag >= 4.0f) return "SEDANG";
            else return "RINGAN";
        }

        // Kata terakhir dari Wilayah
        public String getShortLocation() {
            if (Wilayah == null || Wilayah.isEmpty()) return "Tidak diketahui";
            String[] parts = Wilayah.trim().split("\\s+");
            return parts[parts.length - 1];
        }

        // "15 Jun 2025 · 14:23 WIB"
        public String getFormattedTime() {
            String t = (Tanggal != null) ? Tanggal : "-";
            String j = (Jam != null) ? Jam : "-";
            return t + " \u00b7 " + j;
        }
    }
}