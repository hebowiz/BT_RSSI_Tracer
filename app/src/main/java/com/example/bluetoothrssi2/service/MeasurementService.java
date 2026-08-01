package com.example.bluetoothrssi2.service;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.bluetoothrssi2.MainActivity;
import com.example.bluetoothrssi2.R;
import com.example.bluetoothrssi2.data.MeasurementRepository;

public final class MeasurementService extends Service {
    private static final String TAG = "MeasurementService";
    private static final String ACTION_START = "com.example.bluetoothrssi2.action.START_MEASUREMENT";
    private static final String ACTION_STOP = "com.example.bluetoothrssi2.action.STOP_MEASUREMENT";
    private static final String EXTRA_TARGET_NAME = "target_name";
    private static final String EXTRA_TARGET_ADDRESS = "target_address";
    private static final String EXTRA_TARGET_CLASS = "target_class";
    private static final String EXTRA_DELAY_SECONDS = "delay_seconds";
    private static final String EXTRA_DURATION_SECONDS = "duration_seconds";
    private static final String NOTIFICATION_CHANNEL_ID = "rssi_measurement";
    private static final int NOTIFICATION_ID = 1001;
    private static final long INQUIRY_RESTART_DELAY_MS = 150L;
    private static final long WAKE_LOCK_TIMEOUT_MS = 6L * 60L * 60L * 1_000L;
    private static final long WAKE_LOCK_RENEWAL_MS = 5L * 60L * 60L * 1_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MeasurementRepository repository = MeasurementRepository.getInstance();
    private final Runnable beginMeasurementRunnable = this::beginMeasurement;
    private final Runnable durationRunnable = () -> stopMeasurement(true);
    private final Runnable restartInquiryRunnable = this::startInquiry;
    private final Runnable renewWakeLockRunnable = this::renewWakeLock;

    private BluetoothAdapter bluetoothAdapter;
    private PowerManager.WakeLock wakeLock;
    private String targetName = "";
    private String targetAddress = "";
    private String targetClass = "";
    private int delaySeconds;
    private double durationSeconds = Double.POSITIVE_INFINITY;
    private long measurementStartElapsedRealtime;
    private boolean receiverRegistered;
    private boolean sessionActive;

    public static void start(
            Context context,
            String targetName,
            String targetAddress,
            String targetClass,
            int delaySeconds,
            double durationSeconds) {
        Intent intent = new Intent(context, MeasurementService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_TARGET_NAME, targetName)
                .putExtra(EXTRA_TARGET_ADDRESS, targetAddress)
                .putExtra(EXTRA_TARGET_CLASS, targetClass)
                .putExtra(EXTRA_DELAY_SECONDS, delaySeconds)
                .putExtra(EXTRA_DURATION_SECONDS, durationSeconds);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, MeasurementService.class).setAction(ACTION_STOP);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager == null ? null : bluetoothManager.getAdapter();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(intent.getAction())) {
            stopMeasurement(false);
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(intent.getAction())) {
            startSession(intent);
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startSession(Intent intent) {
        if (sessionActive) {
            return;
        }
        targetName = valueOrEmpty(intent.getStringExtra(EXTRA_TARGET_NAME));
        targetAddress = valueOrEmpty(intent.getStringExtra(EXTRA_TARGET_ADDRESS));
        targetClass = valueOrEmpty(intent.getStringExtra(EXTRA_TARGET_CLASS));
        delaySeconds = Math.max(0, intent.getIntExtra(EXTRA_DELAY_SECONDS, 0));
        durationSeconds = intent.getDoubleExtra(EXTRA_DURATION_SECONDS, Double.POSITIVE_INFINITY);

        startForeground(NOTIFICATION_ID, buildNotification(
                delaySeconds > 0
                        ? getString(R.string.measurement_notification_waiting)
                        : getString(R.string.measurement_notification_active, targetName)));

        if (bluetoothAdapter == null || targetAddress.isEmpty() || !hasBluetoothPermissions()) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return;
        }

        sessionActive = true;
        repository.beginSession(targetName, targetAddress, targetClass);
        acquireWakeLock();
        handler.postDelayed(beginMeasurementRunnable, delaySeconds * 1_000L);
    }

    private void beginMeasurement() {
        if (!sessionActive) {
            return;
        }
        if (!isBluetoothReady()) {
            stopMeasurement(false);
            return;
        }
        measurementStartElapsedRealtime = SystemClock.elapsedRealtime();
        repository.markMeasuring();
        registerReceiver();
        updateNotification(getString(R.string.measurement_notification_active, targetName));
        if (Double.isFinite(durationSeconds)) {
            handler.postDelayed(durationRunnable, Math.max(0L, (long) (durationSeconds * 1_000.0)));
        }
        vibrate(500L);
        startInquiry();
    }

    @SuppressLint("MissingPermission")
    private void startInquiry() {
        handler.removeCallbacks(restartInquiryRunnable);
        if (!sessionActive || repository.snapshot().getState()
                != com.example.bluetoothrssi2.model.MeasurementState.MEASURING) {
            return;
        }
        if (!isBluetoothReady()) {
            stopMeasurement(false);
            return;
        }
        try {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            if (!bluetoothAdapter.startDiscovery()) {
                handler.postDelayed(restartInquiryRunnable, 1_000L);
            }
        } catch (SecurityException exception) {
            Log.e(TAG, "Unable to start Bluetooth inquiry", exception);
            stopMeasurement(false);
        }
    }

    private void scheduleNextInquiry() {
        handler.removeCallbacks(restartInquiryRunnable);
        if (sessionActive) {
            handler.postDelayed(restartInquiryRunnable, INQUIRY_RESTART_DELAY_MS);
        }
    }

    @SuppressLint("MissingPermission")
    private void stopMeasurement(boolean durationReached) {
        if (!sessionActive) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return;
        }
        sessionActive = false;
        handler.removeCallbacks(beginMeasurementRunnable);
        handler.removeCallbacks(durationRunnable);
        handler.removeCallbacks(restartInquiryRunnable);
        try {
            if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
        } catch (SecurityException ignored) {
        }
        unregisterReceiver();
        releaseWakeLock();
        repository.finishSession();
        vibrate(1_000L);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        Log.d(TAG, durationReached ? "Measurement duration reached" : "Measurement stopped");
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
            registerReceiver(measurementReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(measurementReceiver, filter);
        }
        receiverRegistered = true;
    }

    private void unregisterReceiver() {
        if (!receiverRegistered) {
            return;
        }
        try {
            unregisterReceiver(measurementReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        receiverRegistered = false;
    }

    private final BroadcastReceiver measurementReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                handleFoundTarget(intent);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                scheduleNextInquiry();
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    stopMeasurement(false);
                }
            }
        }
    };

    @SuppressLint("MissingPermission")
    private void handleFoundTarget(Intent intent) {
        BluetoothDevice device;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        } else {
            device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        }
        if (device == null) {
            return;
        }
        try {
            if (!targetAddress.equals(device.getAddress())) {
                return;
            }
            int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
            if (rssi == Short.MIN_VALUE) {
                return;
            }
            double elapsedSeconds =
                    (SystemClock.elapsedRealtime() - measurementStartElapsedRealtime) / 1_000.0;
            repository.addSample(elapsedSeconds, rssi);
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            scheduleNextInquiry();
        } catch (SecurityException exception) {
            Log.e(TAG, "Unable to read target RSSI", exception);
            stopMeasurement(false);
        }
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private boolean isBluetoothReady() {
        try {
            return bluetoothAdapter != null && hasBluetoothPermissions() && bluetoothAdapter.isEnabled();
        } catch (SecurityException exception) {
            return false;
        }
    }

    private void acquireWakeLock() {
        PowerManager powerManager = getSystemService(PowerManager.class);
        if (powerManager == null) {
            return;
        }
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                getPackageName() + ":RssiMeasurement");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
        handler.postDelayed(renewWakeLockRunnable, WAKE_LOCK_RENEWAL_MS);
    }

    private void renewWakeLock() {
        if (!sessionActive || wakeLock == null) {
            return;
        }
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
        handler.postDelayed(renewWakeLockRunnable, WAKE_LOCK_RENEWAL_MS);
    }

    private void releaseWakeLock() {
        handler.removeCallbacks(renewWakeLockRunnable);
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.measurement_notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String message) {
        Intent activityIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(message)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNotification(String message) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(message));
        }
    }

    private void vibrate(long durationMillis) {
        Vibrator vibrator = getSystemService(Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        vibrator.vibrate(VibrationEffect.createOneShot(
                durationMillis,
                VibrationEffect.DEFAULT_AMPLITUDE));
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopMeasurement(false);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if (sessionActive) {
            sessionActive = false;
            handler.removeCallbacksAndMessages(null);
            try {
                if (bluetoothAdapter != null && hasBluetoothPermissions()
                        && bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }
            } catch (SecurityException ignored) {
            }
            unregisterReceiver();
            releaseWakeLock();
            repository.finishSession();
        }
        super.onDestroy();
    }
}
