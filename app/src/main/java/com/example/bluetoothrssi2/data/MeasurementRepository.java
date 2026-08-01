package com.example.bluetoothrssi2.data;

import androidx.annotation.NonNull;

import com.example.bluetoothrssi2.model.MeasurementSnapshot;
import com.example.bluetoothrssi2.model.MeasurementState;
import com.example.bluetoothrssi2.model.RssiSample;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MeasurementRepository {
    public interface Listener {
        void onMeasurementChanged(@NonNull MeasurementSnapshot snapshot);
    }

    private static final MeasurementRepository INSTANCE = new MeasurementRepository();

    private final List<RssiSample> samples = new ArrayList<>();
    private final Set<Listener> listeners = new LinkedHashSet<>();
    private MeasurementState state = MeasurementState.IDLE;
    private String targetName = "";
    private String targetAddress = "";
    private String targetType = "";
    private String targetClass = "";
    private long rssiSum;
    private int latestRssi;
    private int minRssi;
    private int maxRssi;
    private boolean saveAvailable;

    public static MeasurementRepository getInstance() {
        return INSTANCE;
    }

    public synchronized void addListener(@NonNull Listener listener) {
        listeners.add(listener);
        listener.onMeasurementChanged(createSnapshot());
    }

    public synchronized void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    public synchronized void beginSession(
            @NonNull String name,
            @NonNull String address,
            @NonNull String bluetoothType,
            @NonNull String bluetoothClass) {
        samples.clear();
        targetName = name;
        targetAddress = address;
        targetType = bluetoothType;
        targetClass = bluetoothClass;
        rssiSum = 0L;
        latestRssi = 0;
        minRssi = 0;
        maxRssi = 0;
        saveAvailable = false;
        state = MeasurementState.DELAYING;
        notifyListeners();
    }

    public synchronized void markMeasuring() {
        if (state != MeasurementState.DELAYING) {
            return;
        }
        state = MeasurementState.MEASURING;
        notifyListeners();
    }

    public synchronized void addSample(double elapsedSeconds, int rssi) {
        if (state != MeasurementState.MEASURING) {
            return;
        }
        samples.add(new RssiSample(elapsedSeconds, rssi));
        latestRssi = rssi;
        rssiSum += rssi;
        if (samples.size() == 1) {
            minRssi = rssi;
            maxRssi = rssi;
        } else {
            minRssi = Math.min(minRssi, rssi);
            maxRssi = Math.max(maxRssi, rssi);
        }
        notifyListeners();
    }

    public synchronized void finishSession() {
        if (state == MeasurementState.IDLE || state == MeasurementState.STOPPED) {
            return;
        }
        state = MeasurementState.STOPPED;
        saveAvailable = !samples.isEmpty();
        notifyListeners();
    }

    public synchronized void discardPendingSave() {
        if (!saveAvailable) {
            return;
        }
        saveAvailable = false;
        notifyListeners();
    }

    public synchronized void markSaved() {
        if (!saveAvailable) {
            return;
        }
        saveAvailable = false;
        notifyListeners();
    }

    @NonNull
    public synchronized MeasurementSnapshot snapshot() {
        return createSnapshot();
    }

    private MeasurementSnapshot createSnapshot() {
        double average = samples.isEmpty() ? 0.0 : (double) rssiSum / samples.size();
        return new MeasurementSnapshot(
                state,
                targetName,
                targetAddress,
                targetType,
                targetClass,
                new ArrayList<>(samples),
                latestRssi,
                minRssi,
                maxRssi,
                average,
                saveAvailable);
    }

    private void notifyListeners() {
        MeasurementSnapshot snapshot = createSnapshot();
        List<Listener> currentListeners = new ArrayList<>(listeners);
        for (Listener listener : currentListeners) {
            listener.onMeasurementChanged(snapshot);
        }
    }
}
