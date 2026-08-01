package com.example.bluetoothrssi2.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DiscoveredDeviceTest {
    @Test
    public void rssiOnlyChangesWhenNewValueIsStronger() {
        DiscoveredDevice device = new DiscoveredDevice(
                "Headset",
                "00:11:22:33:44:55",
                -70);

        assertFalse(device.updateRssiIfStronger(-80));
        assertEquals(-70, device.getRssi());
        assertTrue(device.updateRssiIfStronger(-50));
        assertEquals(-50, device.getRssi());
    }
}
