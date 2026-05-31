package it.reyboz.bustorino.fragments

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.core.net.toUri
import it.reyboz.bustorino.R
import it.reyboz.bustorino.backend.utils
import it.reyboz.bustorino.middleware.BarcodeScanContract
import it.reyboz.bustorino.middleware.BarcodeScanOptions
import it.reyboz.bustorino.middleware.BarcodeScanUtils

//TODO: This might be probably implemented as interface
abstract class BarcodeFragment : ScreenBaseFragment(){

    private val barcodeLauncher = registerForActivityResult(BarcodeScanContract(), ActivityResultCallback {
            result ->
        if (result != null && result.contents != null) {
            //Toast.makeText(MyActivity.this, "Cancelled", Toast.LENGTH_LONG).show();
            val uri: Uri
            try {
                uri = result.contents.toUri() // this apparently prevents NullPointerException. Somehow.
            } catch (e: Exception) {
                Log.w("BusTO-BarcodeFragment","Cannot read QR code",e)
                if (context != null) Toast.makeText(
                    requireContext(),
                    R.string.no_qrcode, Toast.LENGTH_SHORT
                ).show()
                return@ActivityResultCallback
            }
            val busStopID = utils.getBusStopIDFromUri(uri)
            onQrScanSuccess(busStopID)
        } else {
            if (context != null) Toast.makeText(
                requireContext(), R.string.no_qrcode, Toast.LENGTH_SHORT
            ).show()
        }
    })

    abstract fun onQrScanSuccess(busIDToSearch: String)

    protected fun launchBarcodeScan() {
        val context = getContext() ?: return
        val scanOptions = BarcodeScanOptions()
        val intent = scanOptions.createScanIntent()
        if (!BarcodeScanUtils.checkTargetPackageExists(context, intent)) {
            BarcodeScanUtils.showDownloadDialog(context)
        } else {
            barcodeLauncher.launch(scanOptions)
        }
    }
}