package com.example.bluetoothrssi2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.bluetoothrssi2.bluetooth.DeviceDiscoveryController;
import com.example.bluetoothrssi2.data.MeasurementFileSaver;
import com.example.bluetoothrssi2.data.MeasurementRepository;
import com.example.bluetoothrssi2.model.DiscoveredDevice;
import com.example.bluetoothrssi2.model.MeasurementSnapshot;
import com.example.bluetoothrssi2.model.RssiSample;
import com.example.bluetoothrssi2.service.MeasurementService;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity
        implements DeviceDiscoveryController.Listener, MeasurementRepository.Listener {
    private static final String STATE_TARGET_NAME = "target_name";
    private static final String STATE_TARGET_ADDRESS = "target_address";
    private static final String STATE_TARGET_TYPE = "target_type";
    private static final String STATE_TARGET_CLASS = "target_class";
    private static final int GRAPH_RANGE_SECONDS_1 = 30;
    private static final int GRAPH_RANGE_SECONDS_2 = 100;
    private static final float MEASUREMENT_BUTTON_HEIGHT_RATIO = 0.075f;

    private final MeasurementRepository repository = MeasurementRepository.getInstance();
    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BluetoothAdapter bluetoothAdapter;
    private DeviceDiscoveryController discoveryController;
    private Button targetButton;
    private Button startButton;
    private Button stopSaveButton;
    private TextView targetDeviceNameTextView;
    private TextView targetDeviceAddressTextView;
    private TextView targetDeviceTypeTextView;
    private TextView targetDeviceClassTextView;
    private TextView measurementTextView;
    private Spinner delaySpinner;
    private Spinner durationSpinner;
    private GraphView graph;
    private LineGraphSeries<DataPoint> rssiSeries;
    private String selectedTargetName = "";
    private String selectedTargetAddress = "";
    private String selectedTargetType = "";
    private String selectedTargetClass = "";
    private int renderedSampleCount;
    private int graphRangeMode;
    private boolean pendingStartAfterNotificationPermission;
    private MeasurementSnapshot pendingSaveSnapshot;
    private boolean saving;
    private boolean bluetoothStateReceiverRegistered;

    private ActivityResultLauncher<String[]> targetPermissionLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private ActivityResultLauncher<String> storagePermissionLauncher;

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                renderSnapshot(repository.snapshot());
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerPermissionLaunchers();
        setContentView(R.layout.activity_main);
        bindViews();
        configureMeasurementButtonDimensions();
        configureTitle();
        configureSystemInsets();
        configureSpinners();
        configureGraph();

        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager == null ? null : bluetoothManager.getAdapter();
        if (bluetoothAdapter != null) {
            discoveryController = new DeviceDiscoveryController(this, bluetoothAdapter, this);
        }

        if (savedInstanceState != null) {
            selectedTargetName = valueOrEmpty(savedInstanceState.getString(STATE_TARGET_NAME));
            selectedTargetAddress = valueOrEmpty(savedInstanceState.getString(STATE_TARGET_ADDRESS));
            selectedTargetType = valueOrEmpty(savedInstanceState.getString(STATE_TARGET_TYPE));
            selectedTargetClass = valueOrEmpty(savedInstanceState.getString(STATE_TARGET_CLASS));
        }
        updateTargetDisplay();

        targetButton.setOnClickListener(view -> onTargetClicked());
        startButton.setOnClickListener(view -> onStartClicked());
        stopSaveButton.setOnClickListener(view -> onStopSaveClicked());
        renderSnapshot(repository.snapshot());
    }

    private void bindViews() {
        targetButton = findViewById(R.id.targetButton);
        startButton = findViewById(R.id.startButton);
        stopSaveButton = findViewById(R.id.stopSaveButton);
        targetDeviceNameTextView = findViewById(R.id.targetDeviceNameTextView);
        targetDeviceAddressTextView = findViewById(R.id.targetDeviceAddressTextView);
        targetDeviceTypeTextView = findViewById(R.id.targetDeviceTypeTextView);
        targetDeviceClassTextView = findViewById(R.id.targetDeviceClassTextView);
        measurementTextView = findViewById(R.id.measurementTextView);
        delaySpinner = findViewById(R.id.delaySpinner);
        durationSpinner = findViewById(R.id.durationSpinner);
        graph = findViewById(R.id.rssiGraph);
    }

    private void configureMeasurementButtonDimensions() {
        int buttonHeight = Math.round(
                getResources().getDisplayMetrics().heightPixels
                        * MEASUREMENT_BUTTON_HEIGHT_RATIO);

        ViewGroup.LayoutParams startParams = startButton.getLayoutParams();
        startParams.height = buttonHeight;
        startButton.setLayoutParams(startParams);

        ViewGroup.LayoutParams stopSaveParams = stopSaveButton.getLayoutParams();
        stopSaveParams.height = buttonHeight;
        stopSaveButton.setLayoutParams(stopSaveParams);
    }

    private void configureTitle() {
        if (getSupportActionBar() == null) {
            return;
        }
        String title = getString(R.string.app_name)
                + " " + Build.MANUFACTURER
                + " " + Build.MODEL;
        getSupportActionBar().setTitle(title);
    }

    private void configureSystemInsets() {
        View root = findViewById(R.id.rootLayout);
        int initialBottomPadding = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            view.setPadding(
                    view.getPaddingLeft(),
                    view.getPaddingTop(),
                    view.getPaddingRight(),
                    initialBottomPadding + bottomInset);
            return insets;
        });
    }

    private void configureSpinners() {
        ArrayAdapter<CharSequence> delayAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.delay_options,
                android.R.layout.simple_spinner_item);
        delayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        delaySpinner.setAdapter(delayAdapter);
        delaySpinner.setSelection(0);

        ArrayAdapter<CharSequence> durationAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.duration_options,
                android.R.layout.simple_spinner_item);
        durationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        durationSpinner.setAdapter(durationAdapter);
        durationSpinner.setSelection(durationAdapter.getPosition("inf"));
    }

    @SuppressLint("ClickableViewAccessibility")
    private void configureGraph() {
        rssiSeries = new LineGraphSeries<>();
        rssiSeries.setTitle("RSSI [dBm]");
        rssiSeries.setColor(Color.MAGENTA);
        rssiSeries.setThickness(6);
        graph.addSeries(rssiSeries);

        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0.0);
        graph.getViewport().setMaxX(10.0);
        graph.getViewport().setScrollable(true);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(-100.0);
        graph.getViewport().setMaxY(0.0);
        graph.getGridLabelRenderer().setPadding(
                Math.round(16 * getResources().getDisplayMetrics().density));
        graph.getGridLabelRenderer().setHorizontalAxisTitle("Time [sec]");
        graph.getLegendRenderer().setVisible(true);
        graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);

        GestureDetector tapDetector = new GestureDetector(
                this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent event) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapUp(MotionEvent event) {
                        graphRangeMode = (graphRangeMode + 1) % 3;
                        updateGraphViewport(repository.snapshot().getSamples());
                        return true;
                    }
                });
        graph.setOnTouchListener((view, event) -> {
            boolean handled = tapDetector.onTouchEvent(event);
            if (handled && event.getActionMasked() == MotionEvent.ACTION_UP) {
                view.performClick();
            }
            return false;
        });
    }

    private void registerPermissionLaunchers() {
        targetPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                this::onTargetPermissionsResult);
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (!granted) {
                        showTopToast(getString(R.string.notification_permission_denied));
                    }
                    if (pendingStartAfterNotificationPermission) {
                        pendingStartAfterNotificationPermission = false;
                        startMeasurement();
                    }
                });
        storagePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted && pendingSaveSnapshot != null) {
                        saveMeasurement(pendingSaveSnapshot);
                    } else if (!granted) {
                        showTopToast(getString(R.string.storage_permission_required));
                    }
                    pendingSaveSnapshot = null;
                });
    }

    private void onTargetClicked() {
        if (bluetoothAdapter == null) {
            showTopToast(getString(R.string.bluetooth_not_supported));
            return;
        }
        if (!hasBluetoothPermissions()) {
            targetPermissionLauncher.launch(requiredBluetoothPermissions());
            return;
        }
        openTargetDialogIfBluetoothEnabled();
    }

    private void onTargetPermissionsResult(Map<String, Boolean> results) {
        if (hasBluetoothPermissions()) {
            openTargetDialogIfBluetoothEnabled();
            return;
        }
        String message = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? getString(R.string.bluetooth_permission_required)
                : getString(R.string.location_permission_required);
        targetDeviceNameTextView.setText(message);
        clearTargetDetailViews();
        showTopToast(message);
        renderSnapshot(repository.snapshot());
    }

    @SuppressLint("MissingPermission")
    private void openTargetDialogIfBluetoothEnabled() {
        try {
            if (!bluetoothAdapter.isEnabled()) {
                showTopToast(getString(R.string.bluetooth_enable_required));
                return;
            }
            if (discoveryController != null) {
                discoveryController.show();
            }
        } catch (SecurityException exception) {
            showTopToast(getString(R.string.bluetooth_permission_required));
        }
    }

    @Override
    public void onDeviceSelected(@NonNull DiscoveredDevice device) {
        selectedTargetName = device.getName();
        selectedTargetAddress = device.getAddress();
        selectedTargetType = device.getBluetoothType();
        selectedTargetClass = device.getBluetoothClass();
        if (repository.snapshot().isSaveAvailable()) {
            repository.discardPendingSave();
        }
        updateTargetDisplay();
        renderSnapshot(repository.snapshot());
    }

    @Override
    public void onBluetoothBecameUnavailable() {
        showTopToast(getString(R.string.bluetooth_enable_required));
        renderSnapshot(repository.snapshot());
    }

    private void onStartClicked() {
        if (!canStartMeasurement()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            pendingStartAfterNotificationPermission = true;
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        startMeasurement();
    }

    private void startMeasurement() {
        if (!canStartMeasurement()) {
            return;
        }
        renderedSampleCount = 0;
        graphRangeMode = 0;
        rssiSeries.resetData(new DataPoint[]{});
        measurementTextView.setText("");
        startButton.setEnabled(false);
        targetButton.setEnabled(false);
        delaySpinner.setEnabled(false);
        durationSpinner.setEnabled(false);

        int delaySeconds = Integer.parseInt(delaySpinner.getSelectedItem().toString());
        String durationValue = durationSpinner.getSelectedItem().toString();
        double durationSeconds = "inf".equals(durationValue)
                ? Double.POSITIVE_INFINITY
                : Double.parseDouble(durationValue);
        MeasurementService.start(
                this,
                selectedTargetName,
                selectedTargetAddress,
                selectedTargetType,
                selectedTargetClass,
                delaySeconds,
                durationSeconds);
    }

    private void onStopSaveClicked() {
        MeasurementSnapshot snapshot = repository.snapshot();
        if (snapshot.isSaveAvailable()) {
            requestSave(snapshot);
        } else if (snapshot.isActive()) {
            MeasurementService.stop(this);
        }
    }

    private void requestSave(@NonNull MeasurementSnapshot snapshot) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingSaveSnapshot = snapshot;
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        saveMeasurement(snapshot);
    }

    private void saveMeasurement(@NonNull MeasurementSnapshot snapshot) {
        if (saving || !snapshot.isSaveAvailable()) {
            return;
        }
        saving = true;
        renderSnapshot(snapshot);
        String baseName = MeasurementFileSaver.createBaseFileName(snapshot.getTargetName());
        String csvFileName = baseName + ".csv";
        String pngFileName = baseName + ".png";
        fileExecutor.execute(() -> {
            try {
                String savedPath = MeasurementFileSaver.saveCsv(
                        getApplicationContext(),
                        csvFileName,
                        snapshot.getSamples());
                mainHandler.post(() -> {
                    showTopToast(getString(R.string.saved_path, savedPath));
                    captureAndSaveScreenshot(pngFileName);
                });
            } catch (IOException exception) {
                mainHandler.post(() -> {
                    saving = false;
                    showTopToast(getString(R.string.save_failed));
                    renderSnapshot(repository.snapshot());
                });
            }
        });
    }

    private void captureAndSaveScreenshot(@NonNull String pngFileName) {
        Window window = getWindow();
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        if (size.x <= 0 || size.y <= 0) {
            finishScreenshotFailure();
            return;
        }
        Bitmap bitmap = Bitmap.createBitmap(size.x, size.y, Bitmap.Config.ARGB_8888);
        PixelCopy.request(window, bitmap, result -> {
            if (result != PixelCopy.SUCCESS) {
                bitmap.recycle();
                finishScreenshotFailure();
                return;
            }
            fileExecutor.execute(() -> {
                try {
                    MeasurementFileSaver.savePng(getApplicationContext(), pngFileName, bitmap);
                    mainHandler.post(() -> {
                        bitmap.recycle();
                        saving = false;
                        repository.markSaved();
                        renderSnapshot(repository.snapshot());
                    });
                } catch (IOException exception) {
                    bitmap.recycle();
                    mainHandler.post(this::finishScreenshotFailure);
                }
            });
        }, mainHandler);
    }

    private void finishScreenshotFailure() {
        saving = false;
        showTopToast(getString(R.string.screenshot_failed));
        renderSnapshot(repository.snapshot());
    }

    private boolean canStartMeasurement() {
        if (selectedTargetAddress.isEmpty() || !hasBluetoothPermissions() || bluetoothAdapter == null) {
            return false;
        }
        try {
            return bluetoothAdapter.isEnabled() && !repository.snapshot().isActive();
        } catch (SecurityException exception) {
            return false;
        }
    }

    private boolean hasBluetoothPermissions() {
        for (String permission : requiredBluetoothPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private String[] requiredBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            };
        }
        return new String[]{
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
        };
    }

    private void updateTargetDisplay() {
        if (selectedTargetAddress.isEmpty()) {
            targetDeviceNameTextView.setText(R.string.target_not_selected);
            clearTargetDetailViews();
        } else {
            targetDeviceNameTextView.setText(selectedTargetName);
            targetDeviceAddressTextView.setText(selectedTargetAddress);
            targetDeviceTypeTextView.setText(getString(
                    R.string.target_type_format,
                    selectedTargetType));
            targetDeviceClassTextView.setText(getString(
                    R.string.target_class_format,
                    selectedTargetClass));
        }
    }

    private void clearTargetDetailViews() {
        targetDeviceAddressTextView.setText("");
        targetDeviceTypeTextView.setText("");
        targetDeviceClassTextView.setText("");
    }

    @Override
    public void onMeasurementChanged(@NonNull MeasurementSnapshot snapshot) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> renderSnapshot(snapshot));
        } else {
            renderSnapshot(snapshot);
        }
    }

    private void renderSnapshot(@NonNull MeasurementSnapshot snapshot) {
        renderGraph(snapshot.getSamples());
        if (snapshot.hasSamples()) {
            measurementTextView.setText(getString(
                    R.string.measurement_format,
                    snapshot.getLatestRssi(),
                    snapshot.getMinRssi(),
                    snapshot.getMaxRssi(),
                    snapshot.getAverageRssi()));
        } else {
            measurementTextView.setText("");
        }

        boolean active = snapshot.isActive();
        boolean targetAvailable = canUseSelectedTarget();
        startButton.setEnabled(!saving && !active && targetAvailable);
        targetButton.setEnabled(!saving && !active);
        delaySpinner.setEnabled(!saving && !active);
        durationSpinner.setEnabled(!saving && !active);
        stopSaveButton.setText(snapshot.isSaveAvailable() ? R.string.save : R.string.stop);
        stopSaveButton.setEnabled(!saving && (active || snapshot.isSaveAvailable() || targetAvailable));
    }

    private boolean canUseSelectedTarget() {
        return !selectedTargetAddress.isEmpty()
                && bluetoothAdapter != null
                && hasBluetoothPermissions()
                && isBluetoothEnabled();
    }

    @SuppressLint("MissingPermission")
    private boolean isBluetoothEnabled() {
        try {
            return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
        } catch (SecurityException exception) {
            return false;
        }
    }

    private void renderGraph(@NonNull List<RssiSample> samples) {
        if (samples.isEmpty()) {
            if (renderedSampleCount != 0) {
                rssiSeries.resetData(new DataPoint[]{});
                renderedSampleCount = 0;
            }
            updateGraphViewport(samples);
            return;
        }

        if (renderedSampleCount == samples.size() - 1) {
            RssiSample sample = samples.get(samples.size() - 1);
            rssiSeries.appendData(
                    new DataPoint(sample.getElapsedSeconds(), sample.getRssi()),
                    false,
                    Integer.MAX_VALUE);
        } else if (renderedSampleCount != samples.size()) {
            DataPoint[] points = new DataPoint[samples.size()];
            for (int index = 0; index < samples.size(); index++) {
                RssiSample sample = samples.get(index);
                points[index] = new DataPoint(sample.getElapsedSeconds(), sample.getRssi());
            }
            rssiSeries.resetData(points);
        }
        renderedSampleCount = samples.size();
        updateGraphViewport(samples);
    }

    private void updateGraphViewport(@NonNull List<RssiSample> samples) {
        double latestX = samples.isEmpty()
                ? 0.0
                : samples.get(samples.size() - 1).getElapsedSeconds();
        double maximum = Math.max(10.0, latestX);
        double minimum;
        if (graphRangeMode == 1) {
            minimum = Math.max(0.0, maximum - GRAPH_RANGE_SECONDS_1);
        } else if (graphRangeMode == 2) {
            minimum = Math.max(0.0, maximum - GRAPH_RANGE_SECONDS_2);
        } else {
            minimum = 0.0;
        }
        graph.getViewport().setMinX(minimum);
        graph.getViewport().setMaxX(maximum);
        graph.onDataChanged(false, false);
    }

    private void showTopToast(@NonNull String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0);
        toast.show();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerBluetoothStateReceiver();
        MeasurementSnapshot snapshot = repository.snapshot();
        if (selectedTargetAddress.isEmpty() && !snapshot.getTargetAddress().isEmpty()) {
            selectedTargetName = snapshot.getTargetName();
            selectedTargetAddress = snapshot.getTargetAddress();
            selectedTargetType = snapshot.getTargetType();
            selectedTargetClass = snapshot.getTargetClass();
            updateTargetDisplay();
        }
        rssiSeries.resetData(new DataPoint[]{});
        renderedSampleCount = 0;
        repository.addListener(this);
    }

    @Override
    protected void onStop() {
        if (discoveryController != null) {
            discoveryController.stopScanningAndKeepDialog();
        }
        unregisterBluetoothStateReceiver();
        repository.removeListener(this);
        super.onStop();
    }

    private void registerBluetoothStateReceiver() {
        if (bluetoothStateReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(bluetoothStateReceiver, filter);
        }
        bluetoothStateReceiverRegistered = true;
    }

    private void unregisterBluetoothStateReceiver() {
        if (!bluetoothStateReceiverRegistered) {
            return;
        }
        try {
            unregisterReceiver(bluetoothStateReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        bluetoothStateReceiverRegistered = false;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(STATE_TARGET_NAME, selectedTargetName);
        outState.putString(STATE_TARGET_ADDRESS, selectedTargetAddress);
        outState.putString(STATE_TARGET_TYPE, selectedTargetType);
        outState.putString(STATE_TARGET_CLASS, selectedTargetClass);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (discoveryController != null) {
            discoveryController.dismiss();
        }
        if (isFinishing() && repository.snapshot().isActive()) {
            MeasurementService.stop(this);
        }
        fileExecutor.shutdown();
        super.onDestroy();
    }
}
