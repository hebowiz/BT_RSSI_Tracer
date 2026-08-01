package com.example.bluetoothrssi2.bluetooth;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.example.bluetoothrssi2.R;
import com.example.bluetoothrssi2.model.DiscoveredDevice;
import com.example.bluetoothrssi2.ui.DeviceListAdapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DeviceDiscoveryController {
    public interface Listener {
        void onDeviceSelected(@NonNull DiscoveredDevice device);

        void onBluetoothBecameUnavailable();
    }

    private static final long SCAN_LIMIT_MS = 60_000L;
    private static final long RESTART_DELAY_MS = 150L;

    private final Activity activity;
    private final BluetoothAdapter bluetoothAdapter;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<DiscoveredDevice> devices = new ArrayList<>();
    private final Map<String, DiscoveredDevice> devicesByAddress = new LinkedHashMap<>();
    private final Runnable timeoutRunnable = this::finishScanning;
    private final Runnable restartRunnable = this::startInquiry;

    private AlertDialog dialog;
    private DeviceListAdapter listAdapter;
    private View scanningIndicator;
    private boolean receiverRegistered;
    private boolean scanning;

    public DeviceDiscoveryController(
            @NonNull Activity activity,
            @NonNull BluetoothAdapter bluetoothAdapter,
            @NonNull Listener listener) {
        this.activity = activity;
        this.bluetoothAdapter = bluetoothAdapter;
        this.listener = listener;
    }

    @SuppressLint("MissingPermission")
    public void show() {
        dismiss();
        devices.clear();
        devicesByAddress.clear();

        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_device_scan, null, false);
        scanningIndicator = content.findViewById(R.id.scanningIndicator);
        ListView listView = content.findViewById(R.id.deviceListView);
        listAdapter = new DeviceListAdapter(activity, devices);
        listView.setAdapter(listAdapter);

        dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.select_target)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            DiscoveredDevice selected = devices.get(position);
            stopScanning();
            listener.onDeviceSelected(selected);
            if (dialog != null) {
                dialog.dismiss();
            }
        });
        dialog.setOnDismissListener(ignored -> {
            stopScanning();
            devices.clear();
            devicesByAddress.clear();
            listAdapter = null;
            scanningIndicator = null;
            dialog = null;
        });
        dialog.show();

        registerReceiver();
        scanning = true;
        scanningIndicator.setVisibility(View.VISIBLE);
        handler.postDelayed(timeoutRunnable, SCAN_LIMIT_MS);
        startInquiry();
    }

    public void dismiss() {
        if (dialog != null) {
            dialog.dismiss();
        } else {
            stopScanning();
        }
    }

    public void stopScanningAndKeepDialog() {
        stopScanning();
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    @SuppressLint("MissingPermission")
    private void startInquiry() {
        handler.removeCallbacks(restartRunnable);
        if (!scanning || dialog == null || !dialog.isShowing()) {
            return;
        }
        try {
            if (!bluetoothAdapter.isEnabled()) {
                listener.onBluetoothBecameUnavailable();
                dismiss();
                return;
            }
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            if (!bluetoothAdapter.startDiscovery()) {
                handler.postDelayed(restartRunnable, 1_000L);
            }
        } catch (SecurityException exception) {
            listener.onBluetoothBecameUnavailable();
            dismiss();
        }
    }

    @SuppressLint("MissingPermission")
    private void finishScanning() {
        if (!scanning) {
            return;
        }
        scanning = false;
        handler.removeCallbacks(restartRunnable);
        try {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
        } catch (SecurityException ignored) {
        }
        unregisterReceiver();
        if (scanningIndicator != null) {
            scanningIndicator.setVisibility(View.INVISIBLE);
        }
    }

    @SuppressLint("MissingPermission")
    private void stopScanning() {
        handler.removeCallbacks(timeoutRunnable);
        handler.removeCallbacks(restartRunnable);
        scanning = false;
        try {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
        } catch (SecurityException ignored) {
        }
        unregisterReceiver();
        if (scanningIndicator != null) {
            scanningIndicator.setVisibility(View.INVISIBLE);
        }
    }

    private void registerReceiver() {
        if (receiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            activity.registerReceiver(receiver, filter);
        }
        receiverRegistered = true;
    }

    private void unregisterReceiver() {
        if (!receiverRegistered) {
            return;
        }
        try {
            activity.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
        }
        receiverRegistered = false;
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                handleFoundDevice(intent);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (scanning) {
                    handler.postDelayed(restartRunnable, RESTART_DELAY_MS);
                }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    listener.onBluetoothBecameUnavailable();
                    dismiss();
                }
            }
        }
    };

    @SuppressLint("MissingPermission")
    private void handleFoundDevice(Intent intent) {
        BluetoothDevice bluetoothDevice;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        } else {
            bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        }
        if (bluetoothDevice == null) {
            return;
        }

        try {
            String address = bluetoothDevice.getAddress();
            if (address == null || address.isEmpty()) {
                return;
            }
            int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
            if (rssi == Short.MIN_VALUE) {
                return;
            }
            DiscoveredDevice existing = devicesByAddress.get(address);
            if (existing != null) {
                if (existing.updateRssiIfStronger(rssi) && listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
                return;
            }

            String name = bluetoothDevice.getName();
            if (name == null || name.trim().isEmpty()) {
                name = activity.getString(R.string.unknown_device);
            }
            BluetoothClass bluetoothClass;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bluetoothClass = intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_CLASS,
                        BluetoothClass.class);
            } else {
                bluetoothClass = intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS);
            }
            if (bluetoothClass == null) {
                bluetoothClass = bluetoothDevice.getBluetoothClass();
            }
            DiscoveredDevice discovered = new DiscoveredDevice(
                    name,
                    address,
                    BluetoothTypeFormatter.format(bluetoothDevice.getType()),
                    BluetoothClassFormatter.format(bluetoothClass),
                    rssi);
            devicesByAddress.put(address, discovered);
            devices.add(discovered);
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        } catch (SecurityException exception) {
            listener.onBluetoothBecameUnavailable();
            dismiss();
        }
    }
}
