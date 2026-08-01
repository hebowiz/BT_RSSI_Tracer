package com.example.bluetoothrssi2.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.example.bluetoothrssi2.model.RssiSample;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MeasurementFileSaver {
    public static final String OUTPUT_DIRECTORY = "BTClassicScanner";

    private MeasurementFileSaver() {
    }

    @NonNull
    public static String createBaseFileName(@NonNull String targetName) {
        String normalizedName = targetName.trim().isEmpty() ? "Unknown" : targetName.trim();
        normalizedName = normalizedName
                .replace(' ', '_')
                .replaceAll("[\\\\/:*?\"<>|]", "_");
        String timestamp = new SimpleDateFormat(
                "yyyyMMdd_HHmmss_SSS",
                Locale.getDefault()).format(new Date());
        return normalizedName + "_" + timestamp;
    }

    @NonNull
    public static String saveCsv(
            @NonNull Context context,
            @NonNull String fileName,
            @NonNull List<RssiSample> samples) throws IOException {
        byte[] bytes = CsvFormatter.format(samples).getBytes(StandardCharsets.UTF_8);
        return saveToDocuments(context, fileName, "text/csv", outputStream ->
                outputStream.write(bytes));
    }

    @NonNull
    public static String savePng(
            @NonNull Context context,
            @NonNull String fileName,
            @NonNull Bitmap bitmap) throws IOException {
        return saveToDocuments(context, fileName, "image/png", outputStream -> {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                throw new IOException("Unable to encode screenshot");
            }
        });
    }

    @NonNull
    private static String saveToDocuments(
            @NonNull Context context,
            @NonNull String fileName,
            @NonNull String mimeType,
            @NonNull StreamWriter writer) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveWithMediaStore(context, fileName, mimeType, writer);
        }
        return saveWithLegacyStorage(fileName, writer);
    }

    @NonNull
    @RequiresApi(Build.VERSION_CODES.Q)
    private static String saveWithMediaStore(
            @NonNull Context context,
            @NonNull String fileName,
            @NonNull String mimeType,
            @NonNull StreamWriter writer) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        values.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOCUMENTS + "/" + OUTPUT_DIRECTORY);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uri = resolver.insert(collection, values);
        if (uri == null) {
            throw new IOException("Unable to create MediaStore entry for " + fileName);
        }

        boolean published = false;
        try {
            OutputStream rawStream = resolver.openOutputStream(uri, "w");
            if (rawStream == null) {
                throw new IOException("Unable to open " + fileName);
            }
            try (OutputStream outputStream = rawStream) {
                writer.write(outputStream);
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (resolver.update(uri, values, null, null) == 0) {
                throw new IOException("Unable to publish " + fileName);
            }
            published = true;
            return logicalPath(fileName);
        } finally {
            if (!published) {
                resolver.delete(uri, null, null);
            }
        }
    }

    @NonNull
    private static String saveWithLegacyStorage(
            @NonNull String fileName,
            @NonNull StreamWriter writer) throws IOException {
        File directory = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                OUTPUT_DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create " + directory.getAbsolutePath());
        }
        File outputFile = new File(directory, fileName);
        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            writer.write(outputStream);
        }
        return logicalPath(fileName);
    }

    @NonNull
    private static String logicalPath(@NonNull String fileName) {
        return Environment.DIRECTORY_DOCUMENTS + "/" + OUTPUT_DIRECTORY + "/" + fileName;
    }

    private interface StreamWriter {
        void write(OutputStream outputStream) throws IOException;
    }
}
