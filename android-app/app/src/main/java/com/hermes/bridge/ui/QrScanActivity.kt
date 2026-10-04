package com.hermes.bridge.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.zxing.integration.android.IntentIntegrator

/**
 * Full-screen QR scanner. Uses ZXing's embedded capture activity so we don't
 * have to hand-roll a camera preview. On a successful scan it returns the raw
 * text via [EXTRA_QR_RESULT]; the caller parses it as a ConnectPayload.
 */
class QrScanActivity : ComponentActivity() {

    private var scannerStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScannerPrompt(
                        onScan = { startScan() },
                        onCancel = { finishWith(null) },
                    )
                }
            }
        }
        if (savedInstanceState == null) startScan()
    }

    private fun startScan() {
        if (scannerStarted) return
        scannerStarted = true
        IntentIntegrator(this)
            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setPrompt("Hướng camera vào mã QR trên trang Hermes Remote")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
            .initiateScan()
    }

    @Deprecated("ZXing returns via onActivityResult on the embedded flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents != null && resultCode == Activity.RESULT_OK) {
                finishWith(result.contents)
            } else {
                // User backed out of the scanner; allow a retry.
                scannerStarted = false
            }
        } else {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun finishWith(text: String?) {
        val out = Intent()
        if (text != null) out.putExtra(EXTRA_QR_RESULT, text)
        setResult(if (text != null) Activity.RESULT_OK else Activity.RESULT_CANCELED, out)
        finish()
    }

    companion object {
        const val EXTRA_QR_RESULT = "qr_result"
    }
}

@androidx.compose.runtime.Composable
private fun ScannerPrompt(onScan: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Quét QR để kết nối", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Chạy `npx hermes-remote` trên máy tính, rồi quét mã QR trên trang web mở ra.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text("Mở camera") }
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) { Text("Quay lại") }
    }
}
