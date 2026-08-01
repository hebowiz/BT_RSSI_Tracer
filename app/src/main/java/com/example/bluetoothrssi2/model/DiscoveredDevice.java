package com.example.bluetoothrssi2.model;

import androidx.annotation.NonNull;

public final class DiscoveredDevice {
    private final String name;
    private final String address;
    private final String bluetoothClass;
    private int rssi;

    public DiscoveredDevice(
            @NonNull String name,
            @NonNull String address,
            @NonNull String bluetoothClass,
            int rssi) {
        this.name = name;
        this.address = address;
        this.bluetoothClass = bluetoothClass;
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

    @NonNull
    public String getBluetoothClass() {
        return bluetoothClass;
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
