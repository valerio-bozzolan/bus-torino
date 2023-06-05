/*
 * Based on ZXing Android Embedded, Copyright 2021 ZXing Android Embedded authors.

 */

package it.reyboz.bustorino.middleware;

import android.content.Context;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class BarcodeScanContract extends ActivityResultContract<BarcodeScanOptions, IntentResult> {

    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, BarcodeScanOptions input) {
        return input.createScanIntent();
    }


    @Override
    public IntentResult parseResult(int resultCode, @Nullable Intent intent) {
        return IntentIntegrator.parseActivityResult(IntentIntegrator.REQUEST_CODE, resultCode, intent);
    }


}
