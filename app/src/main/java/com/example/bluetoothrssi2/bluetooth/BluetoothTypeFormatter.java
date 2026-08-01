package com.example.bluetoothrssi2.bluetooth;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

public final class BluetoothTypeFormatter {
    private BluetoothTypeFormatter() {
    }

    @NonNull
    public static String format(int deviceType) {
        switch (deviceType) {
            case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                return "CLASSIC";
            case BluetoothDevice.DEVICE_TYPE_LE:
                return "LE";
            case BluetoothDevice.DEVICE_TYPE_DUAL:
                return "DUAL";
            case BluetoothDevice.DEVICE_TYPE_UNKNOWN:
            default:
                return "UNKNOWN";
        }
    }
}
