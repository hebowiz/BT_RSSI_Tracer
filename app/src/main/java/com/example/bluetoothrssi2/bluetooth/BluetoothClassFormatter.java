package com.example.bluetoothrssi2.bluetooth;

import android.bluetooth.BluetoothClass;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class BluetoothClassFormatter {
    private BluetoothClassFormatter() {
    }

    @NonNull
    public static String format(@Nullable BluetoothClass bluetoothClass) {
        if (bluetoothClass == null) {
            return "Unknown";
        }
        return formatMajorDeviceClass(bluetoothClass.getMajorDeviceClass());
    }

    @NonNull
    static String formatMajorDeviceClass(int majorDeviceClass) {
        switch (majorDeviceClass) {
            case BluetoothClass.Device.Major.MISC:
                return "Miscellaneous";
            case BluetoothClass.Device.Major.COMPUTER:
                return "Computer";
            case BluetoothClass.Device.Major.PHONE:
                return "Phone";
            case BluetoothClass.Device.Major.NETWORKING:
                return "Networking";
            case BluetoothClass.Device.Major.AUDIO_VIDEO:
                return "Audio/Video";
            case BluetoothClass.Device.Major.PERIPHERAL:
                return "Peripheral";
            case BluetoothClass.Device.Major.IMAGING:
                return "Imaging";
            case BluetoothClass.Device.Major.WEARABLE:
                return "Wearable";
            case BluetoothClass.Device.Major.TOY:
                return "Toy";
            case BluetoothClass.Device.Major.HEALTH:
                return "Health";
            case BluetoothClass.Device.Major.UNCATEGORIZED:
                return "Uncategorized";
            default:
                return "Unknown";
        }
    }
}
