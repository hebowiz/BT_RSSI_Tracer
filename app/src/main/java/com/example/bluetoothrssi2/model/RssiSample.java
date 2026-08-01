package com.example.bluetoothrssi2.model;

public final class RssiSample {
    private final double elapsedSeconds;
    private final int rssi;

    public RssiSample(double elapsedSeconds, int rssi) {
        this.elapsedSeconds = elapsedSeconds;
        this.rssi = rssi;
    }

    public double getElapsedSeconds() {
        return elapsedSeconds;
    }

    public int getRssi() {
        return rssi;
    }
}
