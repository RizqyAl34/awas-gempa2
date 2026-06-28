package com.example.gempa;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

// ============================================================
// FILE: EarthquakeAlertAdapter.java
// Adapter untuk RecyclerView di AlertsActivity
// ============================================================

public class EarthquakeAlertAdapter extends
        RecyclerView.Adapter<EarthquakeAlertAdapter.ViewHolder> {

    private final Context context;
    private final List<EarthquakeResponse.GempaData> dataList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(EarthquakeResponse.GempaData gempa);
    }

    public EarthquakeAlertAdapter(Context context,
                                  List<EarthquakeResponse.GempaData> dataList,
                                  OnItemClickListener listener) {
        this.context = context;
        this.dataList = dataList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_earthquake_alert, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EarthquakeResponse.GempaData gempa = dataList.get(position);
        String sev = gempa.getSeverityLevel();

        // Warna teks magnitudo sesuai severity
        int color;
        switch (sev) {
            case "KUAT":   color = Color.parseColor("#C62828"); break;
            case "SEDANG": color = Color.parseColor("#E64A19"); break;
            default:       color = Color.parseColor("#2E7D32"); break;
        }

        // Magnitudo besar di kiri
        holder.tvMagnitude.setText(gempa.Magnitude != null ? gempa.Magnitude : "-");
        holder.tvMagnitude.setTextColor(color);

        // Badge label: KUAT / SEDANG / RINGAN
        holder.tvSeverityBadge.setText(sev);
        holder.tvSeverityBadge.setTextColor(color);

        // Label waktu relatif (hanya tampilkan Jam)
        holder.tvTime.setText(gempa.Jam != null ? gempa.Jam : "-");

        // Lokasi singkat
        holder.tvLocation.setText(
                gempa.Wilayah != null ? gempa.Wilayah : "Tidak diketahui");

        // Deskripsi detail
        String desc = "Kedalaman " + (gempa.Kedalaman != null ? gempa.Kedalaman : "-")
                + " · " + (gempa.Tanggal != null ? gempa.Tanggal : "-");
        if (gempa.Dirasakan != null && !gempa.Dirasakan.isEmpty()) {
            desc += "\nDirasakan: " + gempa.Dirasakan;
        }
        holder.tvDescription.setText(desc);

        // Klik item
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(gempa);
        });
    }

    @Override
    public int getItemCount() {
        return dataList != null ? dataList.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMagnitude, tvSeverityBadge, tvTime, tvLocation, tvDescription;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMagnitude     = itemView.findViewById(R.id.tv_item_magnitude);
            tvSeverityBadge = itemView.findViewById(R.id.tv_item_severity);
            tvTime          = itemView.findViewById(R.id.tv_item_time);
            tvLocation      = itemView.findViewById(R.id.tv_item_location);
            tvDescription   = itemView.findViewById(R.id.tv_item_description);
        }
    }
}