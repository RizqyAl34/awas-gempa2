package com.example.gempa;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EarthquakeNotificationService extends Service {

    public static final String CHANNEL_KUAT   = "gempa_kuat_channel";
    public static final String CHANNEL_SEDANG = "gempa_sedang_channel";
    public static final String CHANNEL_RINGAN = "gempa_ringan_channel";

    // Poll setiap 3 menit agar lebih cepat dapat update
    private static final int    POLL_MS            = 3 * 60 * 1000;
    private static final String PREF_NAME          = "gempa_prefs";
    private static final String PREF_LAST_DATETIME = "last_datetime";
    private static final String PREF_LAST_DIRASAKAN= "last_dirasakan_datetime";

    private Handler  handler;
    private Runnable pollRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startPolling();
        return START_STICKY;
    }

    private void startPolling() {
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                // Poll dua endpoint sekaligus
                pollLatestEarthquake();
                pollGempaDirasakan();
                handler.postDelayed(this, POLL_MS);
            }
        };
        handler.post(pollRunnable);
    }

    // =========================================================
    // POLL 1: autogempa.json — gempa terbaru M≥5.0
    // =========================================================
    private void pollLatestEarthquake() {
        RetrofitClient.getApiService()
                .getLatestEarthquake()
                .enqueue(new Callback<EarthquakeResponse.AutoGempaResponse>() {
                    @Override
                    public void onResponse(Call<EarthquakeResponse.AutoGempaResponse> call,
                                           Response<EarthquakeResponse.AutoGempaResponse> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().Infogempa != null
                                && response.body().Infogempa.gempa != null) {
                            checkAndNotify(response.body().Infogempa.gempa,
                                    PREF_LAST_DATETIME);
                        }
                    }
                    @Override
                    public void onFailure(Call<EarthquakeResponse.AutoGempaResponse> call, Throwable t) {}
                });
    }

    // =========================================================
    // POLL 2: gempadirasakan.json — gempa dirasakan semua mag
    // =========================================================
    private void pollGempaDirasakan() {
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

                            List<EarthquakeResponse.GempaData> list =
                                    response.body().Infogempa.gempa;

                            if (!list.isEmpty()) {
                                // Cek gempa terbaru dari list ini
                                checkAndNotify(list.get(0), PREF_LAST_DIRASAKAN);
                            }
                        }
                    }
                    @Override
                    public void onFailure(Call<EarthquakeResponse.GempaTerkiniResponse> call, Throwable t) {}
                });
    }

    // =========================================================
    // CEK APAKAH GEMPA BARU → KIRIM NOTIFIKASI
    // =========================================================
    private void checkAndNotify(EarthquakeResponse.GempaData gempa, String prefKey) {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String lastDT    = prefs.getString(prefKey, "");
        String currentDT = gempa.DateTime != null ? gempa.DateTime : "";

        if (!currentDT.isEmpty() && !currentDT.equals(lastDT)) {
            prefs.edit().putString(prefKey, currentDT).apply();
            sendNotification(gempa);
        }
    }

    // =========================================================
    // KIRIM NOTIFIKASI — dengan HEADS-UP (pop up di layar)
    // =========================================================
    private void sendNotification(EarthquakeResponse.GempaData gempa) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        // Buka AlertsActivity saat notifikasi diklik
        Intent intent = new Intent(this, AlertsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, (int) System.currentTimeMillis(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String sev = gempa.getSeverityLevel();

        // Judul dan channel berdasarkan severity
        String title, channelId;
        int priority, color;

        switch (sev) {
            case "KUAT":
                title     = "🚨 GEMPA KUAT — M" + gempa.Magnitude;
                channelId = CHANNEL_KUAT;
                priority  = NotificationCompat.PRIORITY_MAX;
                color     = 0xFFC62828;
                break;
            case "SEDANG":
                title     = "⚡ Gempa Sedang — M" + gempa.Magnitude;
                channelId = CHANNEL_SEDANG;
                priority  = NotificationCompat.PRIORITY_HIGH;
                color     = 0xFFE64A19;
                break;
            default: // RINGAN
                title     = "ℹ Gempa Kecil — M" + gempa.Magnitude;
                channelId = CHANNEL_RINGAN;
                priority  = NotificationCompat.PRIORITY_DEFAULT;
                color     = 0xFF2E7D32;
                break;
        }

        // Teks singkat (collapsed)
        String contentText = gempa.getShortLocation()
                + " · " + (gempa.Kedalaman != null ? gempa.Kedalaman : "-")
                + " · " + (gempa.Jam != null ? gempa.Jam : "-");

        // Teks panjang (expanded)
        String bigText =
                "Magnitudo : M" + gempa.Magnitude + "\n"
                        + "Lokasi    : " + (gempa.Wilayah   != null ? gempa.Wilayah   : "-") + "\n"
                        + "Kedalaman : " + (gempa.Kedalaman != null ? gempa.Kedalaman : "-") + "\n"
                        + "Waktu     : " + gempa.getFormattedTime() + "\n"
                        + (gempa.Dirasakan != null && !gempa.Dirasakan.isEmpty()
                        ? "Dirasakan : " + gempa.Dirasakan : "");

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle(title)
                        .setContentText(contentText)
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(bigText)
                                .setBigContentTitle(title))
                        .setPriority(priority)
                        .setColor(color)
                        .setColorized(true)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        // ← INI yang membuat notifikasi muncul sebagai pop-up (heads-up)
                        .setFullScreenIntent(pendingIntent, sev.equals("KUAT"))
                        .setVibrate(new long[]{0, 400, 200, 400})
                        .setDefaults(NotificationCompat.DEFAULT_SOUND);

        // Tambah tombol aksi di notifikasi
        builder.addAction(
                android.R.drawable.ic_menu_mapmode,
                "Lihat Peta",
                PendingIntent.getActivity(this,
                        (int) System.currentTimeMillis() + 1,
                        new Intent(this, MapsActivity.class)
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    // =========================================================
    // BUAT NOTIFICATION CHANNEL
    // IMPORTANCE_HIGH = heads-up pop-up otomatis muncul
    // =========================================================
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;

            // Channel KUAT — importance MAX → pasti pop-up
            NotificationChannel kuat = new NotificationChannel(
                    CHANNEL_KUAT, "🚨 Gempa Kuat", NotificationManager.IMPORTANCE_HIGH);
            kuat.setDescription("Gempa M≥6.0 dari BMKG");
            kuat.enableVibration(true);
            kuat.setVibrationPattern(new long[]{0, 400, 200, 400, 200, 400});
            kuat.enableLights(true);
            kuat.setLightColor(0xFFC62828);
            manager.createNotificationChannel(kuat);

            // Channel SEDANG — importance HIGH → pop-up
            NotificationChannel sedang = new NotificationChannel(
                    CHANNEL_SEDANG, "⚡ Gempa Sedang", NotificationManager.IMPORTANCE_HIGH);
            sedang.setDescription("Gempa M4.0–5.9 dari BMKG");
            sedang.enableVibration(true);
            sedang.setVibrationPattern(new long[]{0, 300, 200, 300});
            manager.createNotificationChannel(sedang);

            // Channel RINGAN — importance DEFAULT → masuk notif bar biasa
            NotificationChannel ringan = new NotificationChannel(
                    CHANNEL_RINGAN, "ℹ Gempa Kecil", NotificationManager.IMPORTANCE_DEFAULT);
            ringan.setDescription("Gempa M<4.0 dari BMKG");
            manager.createNotificationChannel(ringan);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && pollRunnable != null)
            handler.removeCallbacks(pollRunnable);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}