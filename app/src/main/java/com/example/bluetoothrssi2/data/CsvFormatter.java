package com.example.bluetoothrssi2.data;

import androidx.annotation.NonNull;

import com.example.bluetoothrssi2.model.RssiSample;

import java.util.List;
import java.util.Locale;

public final class CsvFormatter {
    private CsvFormatter() {
    }

    @NonNull
    public static String format(@NonNull List<RssiSample> samples) {
        StringBuilder csv = new StringBuilder("Time [sec],RSSI [dBm]\n");
        for (RssiSample sample : samples) {
            csv.append(String.format(
                    Locale.US,
                    "%.3f,%d\n",
                    sample.getElapsedSeconds(),
                    sample.getRssi()));
        }
        return csv.toString();
    }
}
