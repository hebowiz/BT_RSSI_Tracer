package com.example.bluetoothrssi2.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bluetoothrssi2.model.MeasurementSnapshot;
import com.example.bluetoothrssi2.model.MeasurementState;

import org.junit.Test;

public class MeasurementRepositoryTest {
    @Test
    public void sessionCalculatesStatisticsAndBecomesSaveable() {
        MeasurementRepository repository = new MeasurementRepository();

        repository.beginSession("Headset", "00:11:22:33:44:55");
        repository.markMeasuring();
        repository.addSample(0.125, -70);
        repository.addSample(1.250, -50);
        repository.finishSession();

        MeasurementSnapshot snapshot = repository.snapshot();
        assertEquals(MeasurementState.STOPPED, snapshot.getState());
        assertEquals(2, snapshot.getSamples().size());
        assertEquals(-50, snapshot.getLatestRssi());
        assertEquals(-70, snapshot.getMinRssi());
        assertEquals(-50, snapshot.getMaxRssi());
        assertEquals(-60.0, snapshot.getAverageRssi(), 0.001);
        assertTrue(snapshot.isSaveAvailable());
    }

    @Test
    public void selectingAnotherTargetCanDiscardSaveEligibilityWithoutClearingData() {
        MeasurementRepository repository = new MeasurementRepository();
        repository.beginSession("Old", "00:00:00:00:00:01");
        repository.markMeasuring();
        repository.addSample(0.5, -65);
        repository.finishSession();

        repository.discardPendingSave();

        MeasurementSnapshot snapshot = repository.snapshot();
        assertFalse(snapshot.isSaveAvailable());
        assertEquals(1, snapshot.getSamples().size());
    }
}
