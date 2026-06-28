package com.example.gempa;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AlertsActivity extends AppCompatActivity {

    // Navbar
    private ImageView btnHome, btnChatbot, btnMaps, btnAlerts;

    // Header info
    private TextView tvUpdateTime, tvTotalCount, tvStrongCount;

    // List gempa
    private RecyclerView recyclerAlerts;
    private ProgressBar progressBar;
    private EarthquakeAlertAdapter adapter;
    private List<EarthquakeResponse.GempaData> gempaList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alerts);

        initViews();
        setupRecyclerView();
        setupClickListeners();
        startNotificationService(); // Mulai service notifikasi background
        fetchAlerts();              // Fetch data untuk ditampilkan di list
    }

    // =========================================================
    // INIT VIEWS
    // =========================================================
    private void initViews() {
        btnHome    = findViewById(R.id.nav_home);
        btnChatbot = findViewById(R.id.nav_chatbot);
        btnMaps    = findViewById(R.id.nav_maps);
        btnAlerts  = findViewById(R.id.nav_alerts);

        tvUpdateTime  = findViewById(R.id.tv_alerts_update_time);
        tvTotalCount  = findViewById(R.id.tv_total_count);
        tvStrongCount = findViewById(R.id.tv_strong_count);

        recyclerAlerts = findViewById(R.id.recycler_alerts);
        progressBar    = findViewById(R.id.progress_bar);
    }

    // =========================================================
    // SETUP RECYCLERVIEW
    // =========================================================
    private void setupRecyclerView() {
        adapter = new EarthquakeAlertAdapter(this, gempaList, gempa -> {
            // Klik item → tampilkan detail via Toast (bisa diganti BottomSheet nanti)
            String detail = "M" + gempa.Magnitude
                    + " — " + gempa.Wilayah
                    + "\nKedalaman: " + (gempa.Kedalaman != null ? gempa.Kedalaman : "-")
                    + "\nWaktu: " + gempa.getFormattedTime()
                    + (gempa.Dirasakan != null && !gempa.Dirasakan.isEmpty()
                    ? "\nDirasakan: " + gempa.Dirasakan : "");
            Toast.makeText(this, detail, Toast.LENGTH_LONG).show();
        });

        recyclerAlerts.setLayoutManager(new LinearLayoutManager(this));
        recyclerAlerts.setAdapter(adapter);
    }

    // =========================================================
    // CLICK LISTENERS
    // =========================================================
    private void setupClickListeners() {
        btnHome.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });

        btnMaps.setOnClickListener(v -> {
            startActivity(new Intent(this, MapsActivity.class));
            finish();
        });

        btnChatbot.setOnClickListener(v ->
                startActivity(new Intent(this, ChatbotActivity.class)));

        btnAlerts.setOnClickListener(v ->
                Toast.makeText(this, "Anda sudah di halaman Riwayat", Toast.LENGTH_SHORT).show());
    }

    // =========================================================
    // FETCH 15 GEMPA TERKINI DARI BMKG → ISI RECYCLERVIEW
    // =========================================================
    // Flag untuk sinkronisasi dua request sekaligus
    private boolean loadedTerkini   = false;
    private boolean loadedDirasakan = false;

    private void fetchAlerts() {
        showLoading(true);
        loadedTerkini   = false;
        loadedDirasakan = false;
        gempaList.clear();

        // Request 1: gempaterkini.json (M≥5.0)
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
                            addUniqueGempa(response.body().Infogempa.gempa);
                        }
                        loadedTerkini = true;
                        checkBothLoaded();
                    }
                    @Override
                    public void onFailure(Call<EarthquakeResponse.GempaTerkiniResponse> call, Throwable t) {
                        loadedTerkini = true;
                        checkBothLoaded();
                    }
                });

        // Request 2: gempadirasakan.json (semua magnitude termasuk kecil)
        RetrofitClient.getApiService()
                .getGempaDirasakan()
                .enqueue(new Callback<EarthquakeResponse.GempaTerkiniResponse>() {
                    @Override
                    public void onResponse(Call<EarthquakeResponse.GempaTerkiniResponse> call,
                                           Response<EarthquakeResponse.GempaTerkiniResponse> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().Infogempa != null
                                && response.body().Infogempa.gempa != null) {
                            addUniqueGempa(response.body().Infogempa.gempa);
                        }
                        loadedDirasakan = true;
                        checkBothLoaded();
                    }
                    @Override
                    public void onFailure(Call<EarthquakeResponse.GempaTerkiniResponse> call, Throwable t) {
                        loadedDirasakan = true;
                        checkBothLoaded();
                    }
                });
    }

    // Tambah gempa ke list tanpa duplikat (cek berdasarkan DateTime)
    private void addUniqueGempa(List<EarthquakeResponse.GempaData> incoming) {
        for (EarthquakeResponse.GempaData g : incoming) {
            boolean duplicate = false;
            for (EarthquakeResponse.GempaData existing : gempaList) {
                if (g.DateTime != null && g.DateTime.equals(existing.DateTime)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) gempaList.add(g);
        }
    }

    // Tampilkan UI setelah kedua request selesai
    private void checkBothLoaded() {
        if (loadedTerkini && loadedDirasakan) {
            showLoading(false);
            if (!gempaList.isEmpty()) {
                // Urutkan: terbaru di atas (berdasarkan DateTime desc)
                gempaList.sort((a, b) -> {
                    String da = a.DateTime != null ? a.DateTime : "";
                    String db = b.DateTime != null ? b.DateTime : "";
                    return db.compareTo(da);
                });
                adapter.notifyDataSetChanged();
                updateSummary(gempaList);
                updateTime();
            } else {
                showError("Tidak ada data gempa");
            }
        }
    }

    // =========================================================
    // UPDATE RINGKASAN: total + jumlah gempa kuat
    // =========================================================
    private void updateSummary(List<EarthquakeResponse.GempaData> list) {
        if (tvTotalCount != null)
            tvTotalCount.setText(list.size() + " Gempa");

        int strongCount = 0;
        for (EarthquakeResponse.GempaData g : list) {
            if ("KUAT".equals(g.getSeverityLevel())) strongCount++;
        }
        if (tvStrongCount != null)
            tvStrongCount.setText(String.valueOf(strongCount));
    }

    private void updateTime() {
        if (tvUpdateTime == null) return;
        String t = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        tvUpdateTime.setText("Diperbarui pukul " + t + " WIB · Sumber: BMKG");
    }

    // =========================================================
    // MULAI SERVICE NOTIFIKASI BACKGROUND
    // =========================================================
    private void startNotificationService() {
        // Pakai startService biasa — bukan startForegroundService
        // (foreground service butuh startForeground() dalam 5 detik → force close)
        Intent serviceIntent = new Intent(this, EarthquakeNotificationService.class);
        startService(serviceIntent);
    }

    // =========================================================
    // HELPERS
    // =========================================================
    private void showLoading(boolean loading) {
        if (progressBar != null)
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (recyclerAlerts != null)
            recyclerAlerts.setVisibility(loading ? View.GONE : View.VISIBLE);
    }

    private void showError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        if (tvUpdateTime != null) tvUpdateTime.setText("Gagal memuat data");
    }
}