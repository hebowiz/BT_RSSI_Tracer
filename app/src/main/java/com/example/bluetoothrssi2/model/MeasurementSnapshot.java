package com.example.bluetoothrssi2.model;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

public final class MeasurementSnapshot {
    private final MeasurementState state;
    private final String targetName;
    private final String targetAddress;
    private final String targetType;
    private final String targetClass;
    private final List<RssiSample> samples;
    private final int latestRssi;
    private final int minRssi;
    private final int maxRssi;
    private final double averageRssi;
    private final boolean saveAvailable;

    public MeasurementSnapshot(
            @NonNull MeasurementState state,
            @NonNull String targetName,
            @NonNull String targetAddress,
            @NonNull String targetType,
            @NonNull String targetClass,
            @NonNull List<RssiSample> samples,
            int latestRssi,
            int minRssi,
            int maxRssi,
            double averageRssi,
            boolean saveAvailable) {
        this.state = state;
        this.targetName = targetName;
        this.targetAddress = targetAddress;
        this.targetType = targetType;
        this.targetClass = targetClass;
        this.samples = Collections.unmodifiableList(samples);
        this.latestRssi = latestRssi;
        this.minRssi = minRssi;
        this.maxRssi = maxRssi;
        this.averageRssi = averageRssi;
        this.saveAvailable = saveAvailable;
    }

    @NonNull
    public MeasurementState getState() {
        return state;
    }

    @NonNull
    public String getTargetName() {
        return targetName;
    }

    @NonNull
    public String getTargetAddress() {
        return targetAddress;
    }

    @NonNull
    public String getTargetType() {
        return targetType;
    }

    @NonNull
    public String getTargetClass() {
        return targetClass;
    }

    @NonNull
    public List<RssiSample> getSamples() {
        return samples;
    }

    public boolean hasSamples() {
        return !samples.isEmpty();
    }

    public int getLatestRssi() {
        return latestRssi;
    }

    public int getMinRssi() {
        return minRssi;
    }

    public int getMaxRssi() {
        return maxRssi;
    }

    public double getAverageRssi() {
        return averageRssi;
    }

    public boolean isActive() {
        return state == MeasurementState.DELAYING || state == MeasurementState.MEASURING;
    }

    public boolean isSaveAvailable() {
        return saveAvailable;
    }
}
