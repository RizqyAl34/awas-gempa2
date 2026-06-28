package com.example.gempa;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatbotActivity extends AppCompatActivity {

    // Navbar
    private ImageView btnHome, btnMaps, btnAlerts;

    // Chat UI
    private RecyclerView     recyclerChat;
    private EditText         etInput;
    private ImageView        btnSend;
    private ChatAdapter      adapter;
    private List<ChatMessage> chatMessages = new ArrayList<>();

    // Riwayat percakapan untuk dikirim ke Gemini (multi-turn)
    private List<GeminiRequest.Content> conversationHistory = new ArrayList<>();

    // Data gempa terbaru (dipakai sebagai konteks ke Gemini)
    private EarthquakeResponse.GempaData latestGempa = null;
    private List<EarthquakeResponse.GempaData> recentGempaList = new ArrayList<>();

    // ====== Retry config untuk error sementara (503 / 500) ======
    private static final int MAX_RETRY = 2;
    private static final long RETRY_DELAY_MS = 1500L;
    private int retryAttempt = 0;
    private final Handler retryHandler = new Handler();

    // Quick reply suggestions
    private static final String[] QUICK_REPLIES = {
            "Gempa terbaru apa?",
            "Apakah aman di lokasi saya?",
            "Cara evakuasi yang benar?",
            "Apa itu skala richter?",
            "Tips siapkan tas darurat"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);

        initViews();
        setupNavbar();
        setupQuickReplies();
        setupRecyclerView();
        setupInputSend();

        // Fetch data gempa sebagai konteks chatbot
        fetchGempaContext();

        // Pesan sambutan dari bot
        addBotMessage("Halo! Saya asisten gempa AI yang terhubung langsung ke data BMKG. "
                + "Kamu bisa tanya:\n• Gempa terbaru di Indonesia\n"
                + "• Apakah lokasiku aman?\n• Tips keselamatan gempa\n\n"
                + "Sedang mengambil data gempa terbaru...");
    }

    // =========================================================
    // INIT VIEWS
    // =========================================================
    private void initViews() {
        recyclerChat = findViewById(R.id.recycler_chat);
        etInput      = findViewById(R.id.et_chat_input);
        btnSend      = findViewById(R.id.btn_send);
        btnHome      = findViewById(R.id.nav_home);
        btnMaps      = findViewById(R.id.nav_maps);
        btnAlerts    = findViewById(R.id.nav_alerts);
    }

    private void setupNavbar() {
        btnHome.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i); finish();
        });
        btnMaps.setOnClickListener(v ->
                startActivity(new Intent(this, MapsActivity.class)));
        btnAlerts.setOnClickListener(v ->
                startActivity(new Intent(this, AlertsActivity.class)));
    }

    // =========================================================
    // QUICK REPLY CHIPS
    // =========================================================
    private void setupQuickReplies() {
        int[] chipIds = {
                R.id.chip_1, R.id.chip_2, R.id.chip_3, R.id.chip_4, R.id.chip_5
        };
        for (int i = 0; i < chipIds.length && i < QUICK_REPLIES.length; i++) {
            TextView chip = findViewById(chipIds[i]);
            if (chip == null) continue;
            String text = QUICK_REPLIES[i];
            chip.setText(text);
            chip.setOnClickListener(v -> sendMessage(text));
        }
    }

    // =========================================================
    // RECYCLERVIEW
    // =========================================================
    private void setupRecyclerView() {
        adapter = new ChatAdapter(this, chatMessages);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true); // chat terbaru di bawah
        recyclerChat.setLayoutManager(lm);
        recyclerChat.setAdapter(adapter);
    }

    // =========================================================
    // INPUT & SEND
    // =========================================================
    private void setupInputSend() {
        btnSend.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (!text.isEmpty()) {
                retryAttempt = 0; // reset counter setiap pesan baru dari user
                sendMessage(text);
            }
        });

        // Enter key = send
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String text = etInput.getText().toString().trim();
                if (!text.isEmpty()) {
                    retryAttempt = 0;
                    sendMessage(text);
                }
                return true;
            }
            return false;
        });
    }

    // =========================================================
    // KIRIM PESAN
    // =========================================================
    private void sendMessage(String userText) {
        etInput.setText("");

        // Tampilkan bubble user hanya saat percobaan pertama (bukan saat retry otomatis)
        boolean isRetry = retryAttempt > 0;
        if (!isRetry) {
            addUserMessage(userText);
        }

        // Tampilkan loading bubble
        int loadingIndex = addLoadingMessage();

        // Tambah ke history multi-turn (hanya sekali, hindari duplikat saat retry)
        if (!isRetry) {
            conversationHistory.add(new GeminiRequest.Content("user",
                    buildPromptWithContext(userText)));
        }

        // Kirim ke Gemini
        GeminiRequest request = new GeminiRequest(new ArrayList<>(conversationHistory));

        GeminiClient.getApiService()
                .generateContent(GeminiClient.API_KEY, request)
                .enqueue(new Callback<GeminiResponse>() {
                    @Override
                    public void onResponse(Call<GeminiResponse> call,
                                           Response<GeminiResponse> response) {
                        removeLoadingMessage(loadingIndex);

                        if (response.isSuccessful() && response.body() != null) {
                            GeminiResponse geminiResp = response.body();

                            if (geminiResp.error != null) {
                                // Error dari Gemini (misal: API key salah)
                                String errMsg = "Error " + geminiResp.error.code
                                        + ": " + geminiResp.error.message;
                                addBotMessage(errMsg);
                                return;
                            }

                            String botText = geminiResp.getText();
                            if (botText != null && !botText.isEmpty()) {
                                addBotMessage(botText);
                                // Simpan jawaban bot ke history
                                conversationHistory.add(
                                        new GeminiRequest.Content("model", botText));
                                retryAttempt = 0; // sukses, reset counter
                            } else {
                                addBotMessage("Maaf, saya tidak mendapat respons. Coba lagi.");
                            }
                        } else {
                            handleErrorResponse(response, userText);
                        }
                    }

                    @Override
                    public void onFailure(Call<GeminiResponse> call, Throwable t) {
                        removeLoadingMessage(loadingIndex);
                        addBotMessage("Koneksi gagal: " + t.getMessage()
                                + "\nPastikan internet aktif.");
                    }
                });
    }

    // =========================================================
    // TANGANI ERROR RESPONSE SESUAI KODE STATUS HTTP
    // =========================================================
    private void handleErrorResponse(Response<GeminiResponse> response, String userText) {
        String errBody = "";
        try {
            if (response.errorBody() != null) {
                errBody = response.errorBody().string();
            }
        } catch (Exception ignored) {}

        int code = response.code();

        if (code == 400) {
            addBotMessage("API Key tidak valid atau format request salah (400). "
                    + "Pastikan API_KEY sudah benar di GeminiClient.java");
        } else if (code == 401 || code == 403) {
            addBotMessage("Akses ditolak (" + code + "). API Key mungkin salah, "
                    + "tidak punya izin, atau sudah dicabut.");
        } else if (code == 404) {
            addBotMessage("Model AI tidak ditemukan (404). Kemungkinan nama model "
                    + "di GeminiApiService.java sudah usang atau salah ketik. "
                    + "Coba ganti ke model yang masih aktif, misalnya gemini-2.5-flash.");
        } else if (code == 429) {
            addBotMessage("Batas penggunaan API tercapai (429). Tunggu beberapa "
                    + "menit lalu coba lagi.");
        } else if (code == 503 || code == 500 || code == 502 || code == 504) {
            // Error sementara di sisi server Google, coba ulang otomatis
            if (retryAttempt < MAX_RETRY) {
                retryAttempt++;
                addBotMessage("Server AI sedang sibuk (" + code + "). "
                        + "Mencoba ulang otomatis (" + retryAttempt + "/" + MAX_RETRY + ")...");
                retryHandler.postDelayed(() -> sendMessage(userText), RETRY_DELAY_MS);
            } else {
                addBotMessage("Server AI masih sibuk setelah " + MAX_RETRY
                        + "x percobaan. Silakan coba lagi beberapa saat.");
                retryAttempt = 0;
            }
        } else {
            addBotMessage("Gagal mendapat respons (kode " + code
                    + "). Coba lagi beberapa saat.");
        }
    }

    // =========================================================
    // BANGUN PROMPT DENGAN KONTEKS DATA BMKG
    // Setiap pesan user disertai data gempa terbaru dari BMKG
    // agar Gemini bisa menjawab berdasarkan kondisi real
    // =========================================================
    private String buildPromptWithContext(String userMessage) {
        StringBuilder context = new StringBuilder();

        context.append("[KONTEKS SISTEM]\n");
        context.append("Kamu adalah asisten AI khusus gempa bumi bernama 'AWAS GEMPA Bot'. ");
        context.append("Jawab dalam Bahasa Indonesia yang ramah dan mudah dipahami. ");
        context.append("Selalu prioritaskan keselamatan jiwa. ");
        context.append("Gunakan data BMKG berikut sebagai referensi jawaban:\n\n");

        // Gempa terbaru
        if (latestGempa != null) {
            context.append("GEMPA TERBARU (BMKG):\n");
            context.append("• Magnitudo : M").append(latestGempa.Magnitude).append("\n");
            context.append("• Lokasi    : ").append(latestGempa.Wilayah != null ? latestGempa.Wilayah : "-").append("\n");
            context.append("• Kedalaman : ").append(latestGempa.Kedalaman != null ? latestGempa.Kedalaman : "-").append("\n");
            context.append("• Waktu     : ").append(latestGempa.getFormattedTime()).append("\n");
            context.append("• Dirasakan : ").append(latestGempa.Dirasakan != null && !latestGempa.Dirasakan.isEmpty()
                    ? latestGempa.Dirasakan : "Belum ada laporan").append("\n");
            context.append("• Level     : ").append(latestGempa.getSeverityLevel()).append("\n\n");
        } else {
            context.append("(Data gempa terbaru belum tersedia)\n\n");
        }

        // 5 gempa terkini
        if (!recentGempaList.isEmpty()) {
            context.append("5 GEMPA TERKINI:\n");
            int max = Math.min(5, recentGempaList.size());
            for (int i = 0; i < max; i++) {
                EarthquakeResponse.GempaData g = recentGempaList.get(i);
                context.append(i + 1).append(". M").append(g.Magnitude)
                        .append(" — ").append(g.getShortLocation())
                        .append(" (").append(g.Tanggal != null ? g.Tanggal : "-").append(")\n");
            }
            context.append("\n");
        }

        context.append("[PERTANYAAN USER]\n");
        context.append(userMessage);

        return context.toString();
    }

    // =========================================================
    // FETCH DATA GEMPA UNTUK KONTEKS CHATBOT
    // =========================================================
    private void fetchGempaContext() {
        // Gempa terbaru
        RetrofitClient.getApiService()
                .getLatestEarthquake()
                .enqueue(new Callback<EarthquakeResponse.AutoGempaResponse>() {
                    @Override
                    public void onResponse(Call<EarthquakeResponse.AutoGempaResponse> call,
                                           Response<EarthquakeResponse.AutoGempaResponse> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().Infogempa != null) {
                            latestGempa = response.body().Infogempa.gempa;
                            // Update pesan sambutan dengan info gempa terbaru
                            updateWelcomeMessage();
                        }
                    }
                    @Override
                    public void onFailure(Call<EarthquakeResponse.AutoGempaResponse> call, Throwable t) {}
                });

        // 15 gempa terkini
        RetrofitClient.getApiService()
                .getRecentEarthquakes()
                .enqueue(new Callback<EarthquakeResponse.GempaTerkiniResponse>() {
                    @Override
                    public void onResponse(Call<EarthquakeResponse.GempaTerkiniResponse> call,
                                           Response<EarthquakeResponse.GempaTerkiniResponse> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().Infogempa != null
                                && response.body().Infogempa.gempa != null) {
                            recentGempaList = response.body().Infogempa.gempa;
                        }
                    }
                    @Override
                    public void onFailure(Call<EarthquakeResponse.GempaTerkiniResponse> call, Throwable t) {}
                });
    }

    // Update pesan pertama bot setelah data BMKG masuk
    private void updateWelcomeMessage() {
        if (chatMessages.isEmpty() || latestGempa == null) return;
        String updated = "Halo! Saya asisten gempa AI yang terhubung ke data BMKG.\n\n"
                + "📡 Gempa terbaru: M" + latestGempa.Magnitude
                + " di " + latestGempa.getShortLocation()
                + " (" + latestGempa.getFormattedTime() + ")\n\n"
                + "Tanya apa saja tentang gempa, keselamatan, atau kondisi terkini!";

        chatMessages.get(0).setMessage(updated);
        runOnUiThread(() -> adapter.notifyItemChanged(0));
    }

    // =========================================================
    // HELPERS — tambah bubble ke chat
    // =========================================================
    private void addUserMessage(String text) {
        chatMessages.add(new ChatMessage(text, ChatMessage.TYPE_USER));
        adapter.notifyItemInserted(chatMessages.size() - 1);
        recyclerChat.scrollToPosition(chatMessages.size() - 1);
    }

    private void addBotMessage(String text) {
        chatMessages.add(new ChatMessage(text, ChatMessage.TYPE_BOT));
        adapter.notifyItemInserted(chatMessages.size() - 1);
        recyclerChat.scrollToPosition(chatMessages.size() - 1);
    }

    private int addLoadingMessage() {
        chatMessages.add(new ChatMessage("...", ChatMessage.TYPE_LOADING));
        int index = chatMessages.size() - 1;
        adapter.notifyItemInserted(index);
        recyclerChat.scrollToPosition(index);
        return index;
    }

    private void removeLoadingMessage(int index) {
        if (index >= 0 && index < chatMessages.size()
                && chatMessages.get(index).getType() == ChatMessage.TYPE_LOADING) {
            chatMessages.remove(index);
            adapter.notifyItemRemoved(index);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Hentikan retry yang masih pending agar tidak crash setelah Activity ditutup
        retryHandler.removeCallbacksAndMessages(null);
    }
}