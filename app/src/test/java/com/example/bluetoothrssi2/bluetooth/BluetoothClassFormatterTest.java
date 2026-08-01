package com.example.bluetoothrssi2.bluetooth;

import static org.junit.Assert.assertEquals;

import android.bluetooth.BluetoothClass;

import org.junit.Test;

public class BluetoothClassFormatterTest {
    @Test
    public void formatsKnownMajorClass() {
        assertEquals(
                "Audio/Video",
                BluetoothClassFormatter.formatMajorDeviceClass(
                        BluetoothClass.Device.Major.AUDIO_VIDEO));
    }

    @Test
    public void formatsUnknownMajorClass() {
        assertEquals("Unknown", BluetoothClassFormatter.formatMajorDeviceClass(-1));
    }
}
