package com.example.bluetoothrssi2.data;

import static org.junit.Assert.assertEquals;

import com.example.bluetoothrssi2.model.RssiSample;

import org.junit.Test;

import java.util.Arrays;

public class CsvFormatterTest {
    @Test
    public void outputContainsOnlySpecifiedHeaderAndTwoColumns() {
        String csv = CsvFormatter.format(Arrays.asList(
                new RssiSample(0.0, -65),
                new RssiSample(1.2414, -67)));

        assertEquals(
                "Time [sec],RSSI [dBm]\n"
                        + "0.000,-65\n"
                        + "1.241,-67\n",
                csv);
    }
}
