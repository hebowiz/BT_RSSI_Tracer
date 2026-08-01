package com.example.bluetoothrssi2.model;

import androidx.annotation.NonNull;

public final class DiscoveredDevice {
    private final String name;
    private final String address;
    private int rssi;

    public DiscoveredDevice(@NonNull String name, @NonNull String address, int rssi) {
        this.name = name;
        this.address = address;
        this.rssi = rssi;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getAddress() {
        return address;
    }

    public int getRssi() {
        return rssi;
    }

    public boolean updateRssiIfStronger(int candidateRssi) {
        if (candidateRssi <= rssi) {
            return false;
        }
        rssi = candidateRssi;
        return true;
    }
}
