package com.example.gempa;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MapsActivity extends AppCompatActivity {

    private ImageView btnHome, btnChatbot, btnAlerts;
    private MapView map = null;

    // Detail card — semua ID sesuai activity_maps.xml
    private MaterialCardView detailCard;
    private TextView tvAlertLabel;   // alert_label
    private TextView tvLocationName; // location_name
    private TextView tvMagnitude;    // magnitude_text
    private TextView tvDepth;        // tv_quake_depth
    private TextView tvTime;         // tv_quake_time

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // WAJIB sebelum setContentView
        Configuration.getInstance().load(this,
                PreferenceManager.getDefaultSharedPreferences(this));

        setContentView(R.layout.activity_maps);

        // Inisialisasi peta
        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);

        // Posisi awal: Indonesia
        map.getController().setZoom(5.0);
        map.getController().setCenter(new GeoPoint(-2.5489, 118.0149));

        // Inisialisasi detail card views
        detailCard      = findViewById(R.id.detail_card);
        tvAlertLabel    = findViewById(R.id.alert_label);
        tvLocationName  = findViewById(R.id.location_name);
        tvMagnitude     = findViewById(R.id.magnitude_text);
        tvDepth         = findViewById(R.id.tv_quake_depth);
        tvTime          = findViewById(R.id.tv_quake_time);

        // Inisialisasi navbar
        btnHome    = findViewById(R.id.nav_home);
        btnChatbot = findViewById(R.id.nav_chatbot);
        btnAlerts  = findViewById(R.id.nav_alerts);

        btnHome.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });
        btnChatbot.setOnClickListener(v ->
                startActivity(new Intent(this, ChatbotActivity.class)));
        btnAlerts.setOnClickListener(v ->
                startActivity(new Intent(this, AlertsActivity.class)));

        // Fetch data BMKG & plot marker
        fetchAndPlotEarthquakes();
    }

    // =========================================================
    // FETCH 15 GEMPA TERKINI DARI BMKG → PLOT MARKER
    // =========================================================
    private void fetchAndPlotEarthquakes() {
        if (tvLocationName != null) tvLocationName.setText("Mengambil data BMKG...");

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

                            List<EarthquakeResponse.GempaData> list =
                                    response.body().Infogempa.gempa;

                            // Plot semua marker
                            for (EarthquakeResponse.GempaData gempa : list) {
                                addMarker(gempa);
                            }
                            map.invalidate(); // refresh peta

                            // Detail card = gempa terbaru (index 0)
                            if (!list.isEmpty()) {
                                updateDetailCard(list.get(0));
                                // Geser kamera ke gempa terbaru
                                GeoPoint latest = parseCoordinates(list.get(0).Coordinates);
                                if (latest != null) {
                                    map.getController().animateTo(latest);
                                    map.getController().setZoom(7.0);
                                }
                            }

                        } else {
                            Toast.makeText(MapsActivity.this,
                                    "Gagal memuat data gempa", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<EarthquakeResponse.GempaTerkiniResponse> call,
                                          Throwable t) {
                        Toast.makeText(MapsActivity.this,
                                "Koneksi gagal: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // =========================================================
    // TAMBAH MARKER KE PETA
    // =========================================================
    private void addMarker(EarthquakeResponse.GempaData gempa) {
        GeoPoint point = parseCoordinates(gempa.Coordinates);
        if (point == null) return;

        Marker marker = new Marker(map);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        marker.setTitle("M" + gempa.Magnitude + " — " + gempa.getShortLocation());
        marker.setSnippet(gempa.Wilayah + "\n" + gempa.getFormattedTime());
        marker.setIcon(createMarkerIcon(gempa.getSeverityLevel(), gempa.Magnitude));

        // Klik marker → update detail card
        marker.setOnMarkerClickListener((m, mapView) -> {
            updateDetailCard(gempa);
            m.showInfoWindow();
            return true;
        });

        map.getOverlays().add(marker);
    }

    // =========================================================
    // BUAT ICON MARKER LINGKARAN BERWARNA + TEKS MAGNITUDE
    // =========================================================
    private Drawable createMarkerIcon(String severity, String magnitudeStr) {
        int color;
        int sizeDp;

        switch (severity) {
            case "KUAT":
                color = Color.parseColor("#C62828");
                sizeDp = 52;
                break;
            case "SEDANG":
                color = Color.parseColor("#E64A19");
                sizeDp = 40;
                break;
            default: // RINGAN
                color = Color.parseColor("#2E7D32");
                sizeDp = 30;
                break;
        }

        float density = getResources().getDisplayMetrics().density;
        int sizePx = (int) (sizeDp * density);

        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        // Lingkaran luar (transparan — efek pulse)
        Paint outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerPaint.setColor(color);
        outerPaint.setAlpha(80);
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, outerPaint);

        // Lingkaran dalam (solid)
        Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerPaint.setColor(color);
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 3.2f, innerPaint);

        // Teks magnitude di tengah
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(sizePx / 3.8f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        float textY = sizePx / 2f - (textPaint.descent() + textPaint.ascent()) / 2;
        canvas.drawText(magnitudeStr != null ? magnitudeStr : "?",
                sizePx / 2f, textY, textPaint);

        return new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
    }

    // =========================================================
    // UPDATE DETAIL CARD DI BAWAH LAYAR
    // =========================================================
    private void updateDetailCard(EarthquakeResponse.GempaData gempa) {
        if (gempa == null) return;
        String sev = gempa.getSeverityLevel();

        // Warna sesuai severity
        int color;
        switch (sev) {
            case "KUAT":   color = Color.parseColor("#C62828"); break;
            case "SEDANG": color = Color.parseColor("#E64A19"); break;
            default:       color = Color.parseColor("#2E7D32"); break;
        }

        if (tvAlertLabel != null) {
            String label;
            switch (sev) {
                case "KUAT":   label = "⚠ CRITICAL ALERT"; break;
                case "SEDANG": label = "● ACTIVE ALERT"; break;
                default:       label = "● INFO GEMPA"; break;
            }
            tvAlertLabel.setText(label);
            tvAlertLabel.setTextColor(color);
        }

        if (tvLocationName != null)
            tvLocationName.setText(gempa.Wilayah != null ? gempa.Wilayah : "-");

        if (tvMagnitude != null) {
            tvMagnitude.setText(gempa.Magnitude != null ? gempa.Magnitude : "-");
            tvMagnitude.setTextColor(color);
        }

        if (tvDepth != null)
            tvDepth.setText("Kedalaman: " + (gempa.Kedalaman != null ? gempa.Kedalaman : "-"));

        if (tvTime != null)
            tvTime.setText(gempa.getFormattedTime());
    }

    // =========================================================
    // PARSE KOORDINAT BMKG: "-8.52,115.65" → GeoPoint
    // =========================================================
    private GeoPoint parseCoordinates(String coords) {
        if (coords == null || coords.isEmpty()) return null;
        try {
            String[] parts = coords.split(",");
            double lat = Double.parseDouble(parts[0].trim());
            double lon = Double.parseDouble(parts[1].trim());
            return new GeoPoint(lat, lon);
        } catch (Exception e) {
            return null;
        }
    }

    // OSM lifecycle
    @Override
    public void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }
}