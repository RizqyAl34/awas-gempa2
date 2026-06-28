package com.example.gempa;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_LOCATION = 100;
    private static final int REQ_NOTIF    = 101;

    // Radius bahaya (km)
    private static final double RADIUS_MERAH   = 100.0;
    private static final double RADIUS_ORANYE  = 300.0;

    // Views
    private ImageView btnChatbot, btnHome, btnMaps, btnAlerts;
    private View bottomNav;

    private MaterialCardView statusCard;
    private TextView tvHeroTitle, tvHeroDescription;

    private TextView tvGlobalCount, tvGlobalDesc;

    private MaterialCardView recentEarthquakeCard;
    private TextView tvQuakeMag, tvQuakeLocation, tvQuakeDepth, tvQuakeTime, tvSeverityBadge;

    private MaterialCardView cardAlertContainer;
    private TextView tvAlertTag, tvAlertTitle, tvAlertSubtitle, tvAlertDescription;

    private TextView tvUpdateTime;

    // Lokasi user
    private Location userLocation = null;

    // LocationManager bawaan Android (TIDAK butuh Google Play Services)
    private LocationManager locationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        initViews();
        setupClickListeners();
        requestNotificationPermission();
        requestLocationAndFetch();
    }

    // =========================================================
    // LOKASI — pakai LocationManager bawaan Android
    // Tidak butuh Google Maps SDK / Google Play Services
    // =========================================================
    private void requestLocationAndFetch() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            return;
        }
        getLastKnownLocation();
    }

    private void getLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            fetchEarthquakeData();
            return;
        }

        // Coba GPS dulu, lalu network provider
        Location gpsLoc  = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location netLoc  = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        if (gpsLoc != null) {
            userLocation = gpsLoc;
        } else if (netLoc != null) {
            userLocation = netLoc;
        }

        // Jika belum ada lokasi, minta update sekali
        if (userLocation == null) {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER,
                        new LocationListener() {
                            @Override
                            public void onLocationChanged(@NonNull Location location) {
                                userLocation = location;
                                // Update hero card jika data gempa sudah ada
                                if (latestGempa != null) updateHeroStatusWithLocation(latestGempa);
                            }
                        }, null);
            } else if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER,
                        new LocationListener() {
                            @Override
                            public void onLocationChanged(@NonNull Location location) {
                                userLocation = location;
                                if (latestGempa != null) updateHeroStatusWithLocation(latestGempa);
                            }
                        }, null);
            }
        }

        // Langsung fetch gempa (tidak perlu tunggu GPS)
        fetchEarthquakeData();
    }

    // Simpan data gempa agar bisa dipakai saat lokasi baru dapat
    private EarthquakeResponse.GempaData latestGempa = null;

    // =========================================================
    // HITUNG JARAK USER → GEMPA (km)
    // =========================================================
    private double calculateDistanceKm(EarthquakeResponse.GempaData gempa) {
        if (userLocation == null || gempa.Coordinates == null) return Double.MAX_VALUE;
        try {
            String[] parts  = gempa.Coordinates.split(",");
            double quakeLat = Double.parseDouble(parts[0].trim());
            double quakeLon = Double.parseDouble(parts[1].trim());

            float[] result = new float[1];
            Location.distanceBetween(
                    userLocation.getLatitude(), userLocation.getLongitude(),
                    quakeLat, quakeLon, result);
            return result[0] / 1000.0; // meter → km
        } catch (Exception e) {
            return Double.MAX_VALUE;
        }
    }

    // =========================================================
    // UPDATE HERO CARD — KOMBINASI JARAK + MAGNITUDE
    //
    // MERAH  (BAHAYA) : KUAT + < 100 km
    // ORANYE (WASPADA): KUAT + < 300 km  ATAU  SEDANG + < 300 km
    // HIJAU  (AMAN)   : semua kondisi lain
    // =========================================================
    private void updateHeroStatusWithLocation(EarthquakeResponse.GempaData gempa) {
        if (gempa == null || statusCard == null) return;

        String  sev    = gempa.getSeverityLevel();
        double  distKm = calculateDistanceKm(gempa);
        boolean hasLoc = userLocation != null;

        String distText = hasLoc && distKm < Double.MAX_VALUE
                ? String.format(Locale.getDefault(), "%.0f km dari lokasi Anda", distKm)
                : "jarak tidak diketahui";

        // Tentukan level bahaya
        String level;
        if (!hasLoc) {
            // Tidak ada GPS → fallback ke severity saja
            level = sev.equals("KUAT") ? "BAHAYA"
                    : sev.equals("SEDANG") ? "WASPADA"
                    : "AMAN";
        } else if (sev.equals("KUAT") && distKm <= RADIUS_MERAH) {
            level = "BAHAYA";
        } else if ((sev.equals("KUAT") || sev.equals("SEDANG")) && distKm <= RADIUS_ORANYE) {
            level = "WASPADA";
        } else {
            level = "AMAN";
        }

        switch (level) {
            case "BAHAYA":
                statusCard.setCardBackgroundColor(getColor(R.color.alert_kuat));
                if (tvHeroTitle != null)
                    tvHeroTitle.setText("⚠ BAHAYA!");
                if (tvHeroDescription != null)
                    tvHeroDescription.setText(
                            "Gempa M" + gempa.Magnitude
                                    + " di " + gempa.getShortLocation()
                                    + " — " + distText + ". SEGERA EVAKUASI!");
                break;

            case "WASPADA":
                statusCard.setCardBackgroundColor(getColor(R.color.alert_sedang));
                if (tvHeroTitle != null)
                    tvHeroTitle.setText("⚡ Waspada");
                if (tvHeroDescription != null)
                    tvHeroDescription.setText(
                            "Gempa M" + gempa.Magnitude
                                    + " di " + gempa.getShortLocation()
                                    + " — " + distText + ". Pantau kondisi.");
                break;

            default: // AMAN
                statusCard.setCardBackgroundColor(getColor(R.color.status_safe));
                if (tvHeroTitle != null)
                    tvHeroTitle.setText("✓ Anda Aman");
                if (tvHeroDescription != null)
                    tvHeroDescription.setText(
                            "Gempa M" + gempa.Magnitude
                                    + " di " + gempa.getShortLocation()
                                    + " — " + distText + ". Area Anda aman.");
                break;
        }
    }

    // =========================================================
    // PERMISSION CALLBACK
    // =========================================================
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastKnownLocation();
            } else {
                Toast.makeText(this, "Izin lokasi ditolak. Status jarak tidak tersedia.",
                        Toast.LENGTH_SHORT).show();
                fetchEarthquakeData();
            }
        }
    }

    // =========================================================
    // MINTA PERMISSION NOTIFIKASI (Android 13+)
    // =========================================================
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            }
        }
    }

    // =========================================================
    // INIT VIEWS
    // =========================================================
    private void initViews() {
        btnChatbot = findViewById(R.id.nav_chatbot);
        btnHome    = findViewById(R.id.nav_home);
        btnMaps    = findViewById(R.id.nav_maps);
        btnAlerts  = findViewById(R.id.nav_alerts);
        bottomNav  = findViewById(R.id.bottom_nav);

        statusCard        = findViewById(R.id.status_card);
        tvHeroTitle       = findViewById(R.id.hero_title);
        tvHeroDescription = findViewById(R.id.tv_hero_description);

        tvGlobalCount = findViewById(R.id.tv_global_count);
        tvGlobalDesc  = findViewById(R.id.tv_global_desc);

        recentEarthquakeCard = findViewById(R.id.earthquake_card_example);
        tvQuakeMag      = findViewById(R.id.tv_quake_magnitude);
        tvQuakeLocation = findViewById(R.id.tv_quake_location);
        tvQuakeDepth    = findViewById(R.id.tv_quake_depth);
        tvQuakeTime     = findViewById(R.id.tv_quake_time);
        tvSeverityBadge = findViewById(R.id.tv_severity_badge);

        cardAlertContainer = findViewById(R.id.card_alert_container);
        tvAlertTag         = findViewById(R.id.tv_alert_tag);
        tvAlertTitle       = findViewById(R.id.tv_alert_title);
        tvAlertSubtitle    = findViewById(R.id.tv_alert_subtitle);
        tvAlertDescription = findViewById(R.id.tv_alert_description);

        tvUpdateTime = findViewById(R.id.tv_update_time);

        if (cardAlertContainer != null) cardAlertContainer.setVisibility(View.GONE);
    }

    // =========================================================
    // CLICK LISTENERS
    // =========================================================
    private void setupClickListeners() {
        btnChatbot.setOnClickListener(v ->
                startActivity(new Intent(this, ChatbotActivity.class)));
        btnMaps.setOnClickListener(v ->
                startActivity(new Intent(this, MapsActivity.class)));
        btnAlerts.setOnClickListener(v -> {
            try { startActivity(new Intent(this, AlertsActivity.class)); }
            catch (Exception e) {
                Toast.makeText(this, "Halaman Alerts belum tersedia", Toast.LENGTH_SHORT).show();
            }
        });
        if (recentEarthquakeCard != null)
            recentEarthquakeCard.setOnClickListener(v ->
                    Toast.makeText(this, "Detail gempa terbaru", Toast.LENGTH_SHORT).show());
        if (bottomNav != null) bottomNav.setOnClickListener(v -> {});
    }

    // =========================================================
    // FETCH DATA BMKG
    // =========================================================
    private void fetchEarthquakeData() {
        if (tvGlobalCount != null) tvGlobalCount.setText("...");
        if (tvUpdateTime != null)  tvUpdateTime.setText("Memuat data BMKG...");

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
                            updateLatestEarthquakeCard(latestGempa);
                            updateAlertCard(latestGempa);
                            updateHeroStatusWithLocation(latestGempa);
                        } else {
                            showError("Respons BMKG tidak valid");
                        }
                    }
                    @Override
                    public void onFailure(Call<EarthquakeResponse.AutoGempaResponse> call, Throwable t) {
                        showError("Gagal: " + t.getMessage());
                    }
                });

        RetrofitClient.getApiService()
                .getRecentEarthquakes()
                .enqueue(new Callback<EarthquakeResponse.GempaTerkiniResponse>() {
                    @Override
                    public void onResponse(Call<EarthquakeResponse.GempaTerkiniResponse> call,
                                           Response<EarthquakeResponse.GempaTerkiniResponse> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().Infogempa != null) {
                            updateGlobalActivityCard(response.body().Infogempa.gempa);
                            updateUpdateTime();
                        }
                    }
                    @Override
                    public void onFailure(Call<EarthquakeResponse.GempaTerkiniResponse> call, Throwable t) {}
                });
    }

    private void updateLatestEarthquakeCard(EarthquakeResponse.GempaData gempa) {
        if (gempa == null) return;
        if (tvQuakeMag != null)      tvQuakeMag.setText("M " + gempa.Magnitude);
        if (tvQuakeLocation != null) tvQuakeLocation.setText(gempa.Wilayah != null ? gempa.Wilayah : "-");
        if (tvQuakeDepth != null)    tvQuakeDepth.setText("Kedalaman: " + (gempa.Kedalaman != null ? gempa.Kedalaman : "-"));
        if (tvQuakeTime != null)     tvQuakeTime.setText(gempa.getFormattedTime());
        if (tvSeverityBadge != null) {
            String sev = gempa.getSeverityLevel();
            tvSeverityBadge.setText(sev);
            switch (sev) {
                case "KUAT":   tvSeverityBadge.setBackgroundResource(R.drawable.bg_severity_high); break;
                case "SEDANG": tvSeverityBadge.setBackgroundResource(R.drawable.bg_severity_medium); break;
                default:       tvSeverityBadge.setBackgroundResource(R.drawable.bg_severity_low); break;
            }
        }
    }

    private void updateAlertCard(EarthquakeResponse.GempaData gempa) {
        if (gempa == null || cardAlertContainer == null) return;
        String sev = gempa.getSeverityLevel();
        if (sev.equals("RINGAN")) { cardAlertContainer.setVisibility(View.GONE); return; }
        cardAlertContainer.setVisibility(View.VISIBLE);
        if (sev.equals("KUAT")) {
            cardAlertContainer.setCardBackgroundColor(getColor(R.color.alert_kuat));
            if (tvAlertTag != null)   tvAlertTag.setText("● CRITICAL ALERT");
            if (tvAlertTitle != null) tvAlertTitle.setText("EARTHQUAKE\nDETECTED");
            if (tvAlertSubtitle != null) { tvAlertSubtitle.setVisibility(View.VISIBLE); tvAlertSubtitle.setText("✦ EVACUATE IMMEDIATELY"); }
            if (tvAlertDescription != null)
                tvAlertDescription.setText("Gempa kuat M" + gempa.Magnitude + " di " + gempa.Wilayah + ". Segera evakuasi.");
        } else {
            cardAlertContainer.setCardBackgroundColor(getColor(R.color.alert_sedang));
            if (tvAlertTag != null)   tvAlertTag.setText("● ACTIVE ALERT");
            if (tvAlertTitle != null) tvAlertTitle.setText("Prepare for\nImpact");
            if (tvAlertSubtitle != null) tvAlertSubtitle.setVisibility(View.GONE);
            if (tvAlertDescription != null)
                tvAlertDescription.setText("Gempa M" + gempa.Magnitude + " di " + gempa.Wilayah + ". Amankan barang sekitar.");
        }
    }

    private void updateGlobalActivityCard(List<EarthquakeResponse.GempaData> list) {
        if (list == null || tvGlobalCount == null) return;
        String today = new SimpleDateFormat("dd MMM yyyy", new Locale("id", "ID")).format(new Date());
        int countToday = 0;
        for (EarthquakeResponse.GempaData g : list)
            if (g.Tanggal != null && g.Tanggal.equalsIgnoreCase(today)) countToday++;
        int display  = countToday > 0 ? countToday : list.size();
        String label = countToday > 0 ? " Hari Ini" : " Terkini";
        tvGlobalCount.setText(display + label);
        if (tvGlobalDesc != null)
            tvGlobalDesc.setText("Data resmi BMKG \u00b7 " + list.size() + " gempa terakhir");
    }

    private void updateUpdateTime() {
        if (tvUpdateTime == null) return;
        String t = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        tvUpdateTime.setText("Diperbarui pukul " + t + " WIB");
    }

    private void showError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        if (tvUpdateTime != null) tvUpdateTime.setText("Gagal memuat data");
    }
}