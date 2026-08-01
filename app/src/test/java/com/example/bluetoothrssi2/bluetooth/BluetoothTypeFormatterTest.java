package com.example.bluetoothrssi2.bluetooth;

import static org.junit.Assert.assertEquals;

import android.bluetooth.BluetoothDevice;

import org.junit.Test;

public class BluetoothTypeFormatterTest {
    @Test
    public void formatsSupportedDeviceTypes() {
        assertEquals("CLASSIC", BluetoothTypeFormatter.format(BluetoothDevice.DEVICE_TYPE_CLASSIC));
        assertEquals("LE", BluetoothTypeFormatter.format(BluetoothDevice.DEVICE_TYPE_LE));
        assertEquals("DUAL", BluetoothTypeFormatter.format(BluetoothDevice.DEVICE_TYPE_DUAL));
        assertEquals("UNKNOWN", BluetoothTypeFormatter.format(BluetoothDevice.DEVICE_TYPE_UNKNOWN));
    }
}
