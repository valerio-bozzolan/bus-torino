
/*
 * Based on ZXing Android Embedded, Copyright 2021 ZXing Android Embedded authors.

 */
package it.reyboz.bustorino.middleware;

import android.content.Intent;
import android.os.Bundle;


import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BarcodeScanOptions {

    public static final String BS_PACKAGE = "com.google.zxing.client.android";

    // supported barcode formats

    // Product Codes
    public static final String UPC_A = "UPC_A";
    public static final String UPC_E = "UPC_E";
    public static final String EAN_8 = "EAN_8";
    public static final String EAN_13 = "EAN_13";
    public static final String RSS_14 = "RSS_14";

    // Other 1D
    public static final String CODE_39 = "CODE_39";
    public static final String CODE_93 = "CODE_93";
    public static final String CODE_128 = "CODE_128";
    public static final String ITF = "ITF";

    public static final String RSS_EXPANDED = "RSS_EXPANDED";

    // 2D
    public static final String QR_CODE = "QR_CODE";
    public static final String DATA_MATRIX = "DATA_MATRIX";
    public static final String PDF_417 = "PDF_417";


    public static final Collection<String> PRODUCT_CODE_TYPES = list(UPC_A, UPC_E, EAN_8, EAN_13, RSS_14);
    public static final Collection<String> ONE_D_CODE_TYPES =
            list(UPC_A, UPC_E, EAN_8, EAN_13, RSS_14, CODE_39, CODE_93, CODE_128,
                    ITF, RSS_14, RSS_EXPANDED);

    public static final Collection<String> ALL_CODE_TYPES = null;

    private final Map<String, Object> moreExtras = new HashMap<>(3);

    private Collection<String> desiredBarcodeFormats;


    private int cameraId = 0;

    public BarcodeScanOptions() {

    }

    public Map<String, ?> getMoreExtras() {
        return moreExtras;
    }

    public final BarcodeScanOptions addExtra(String key, Object value) {
        moreExtras.put(key, value);
        return this;
    }

    public final BarcodeScanOptions setCameraID(int cameraID){
        this.cameraId = cameraID;
        return this;
    }

    /**
     * Set the desired barcode formats to scan.
     *
     * @param desiredBarcodeFormats names of {@code BarcodeFormat}s to scan for
     * @return this
     */
    public BarcodeScanOptions setDesiredBarcodeFormats(Collection<String> desiredBarcodeFormats) {
        this.desiredBarcodeFormats = desiredBarcodeFormats;
        return this;
    }

    /**
     * Set the desired barcode formats to scan.
     *
     * @param desiredBarcodeFormats names of {@code BarcodeFormat}s to scan for
     * @return this
     */
    public BarcodeScanOptions setDesiredBarcodeFormats(String... desiredBarcodeFormats) {
        this.desiredBarcodeFormats = Arrays.asList(desiredBarcodeFormats);
        return this;
    }


    /**
     * Create an scan intent with the specified options.
     *
     * @return the intent
     */
    public Intent createScanIntent() {
        Intent intentScan = new Intent(BS_PACKAGE + ".SCAN");
        intentScan.addCategory(Intent.CATEGORY_DEFAULT);

        // check which types of codes to scan for
        if (desiredBarcodeFormats != null) {
            // set the desired barcode types
            StringBuilder joinedByComma = new StringBuilder();
            for (String format : desiredBarcodeFormats) {
                if (joinedByComma.length() > 0) {
                    joinedByComma.append(',');
                }
                joinedByComma.append(format);
            }
            intentScan.putExtra("SCAN_FORMATS", joinedByComma.toString());
        }

        // check requested camera ID
        if (cameraId >= 0) {
            intentScan.putExtra("SCAN_CAMERA_ID", cameraId);
        }

        intentScan.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intentScan.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        attachMoreExtras(intentScan);
        return intentScan;
    }

    private static List<String> list(String... values) {
        return Collections.unmodifiableList(Arrays.asList(values));
    }

    private void attachMoreExtras(Intent intent) {
        for (Map.Entry<String, Object> entry : moreExtras.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            // Kind of hacky
            if (value instanceof Integer) {
                intent.putExtra(key, (Integer) value);
            } else if (value instanceof Long) {
                intent.putExtra(key, (Long) value);
            } else if (value instanceof Boolean) {
                intent.putExtra(key, (Boolean) value);
            } else if (value instanceof Double) {
                intent.putExtra(key, (Double) value);
            } else if (value instanceof Float) {
                intent.putExtra(key, (Float) value);
            } else if (value instanceof Bundle) {
                intent.putExtra(key, (Bundle) value);
            } else if (value instanceof int[]) {
                intent.putExtra(key, (int[]) value);
            } else if (value instanceof long[]) {
                intent.putExtra(key, (long[]) value);
            } else if (value instanceof boolean[]) {
                intent.putExtra(key, (boolean[]) value);
            } else if (value instanceof double[]) {
                intent.putExtra(key, (double[]) value);
            } else if (value instanceof float[]) {
                intent.putExtra(key, (float[]) value);
            } else if (value instanceof String[]) {
                intent.putExtra(key, (String[]) value);
            } else {
                intent.putExtra(key, value.toString());
            }
        }
    }
}