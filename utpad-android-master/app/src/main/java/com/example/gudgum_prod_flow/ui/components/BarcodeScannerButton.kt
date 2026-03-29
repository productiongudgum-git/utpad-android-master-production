package com.example.gudgum_prod_flow.ui.components

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.gudgum_prod_flow.ui.scanner.BarcodeScannerActivity
import com.example.gudgum_prod_flow.ui.scanner.BarcodeScannerExtras

@Composable
fun BarcodeScannerButton(
    modifier: Modifier = Modifier,
    prompt: String = "Scan barcode",
    onBarcodeScanned: (String) -> Unit,
    onScanError: (String) -> Unit = {},
) {
    val context = LocalContext.current

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanned = result.data?.getStringExtra(BarcodeScannerExtras.ResultBarcodeValue)
            if (!scanned.isNullOrBlank()) {
                onBarcodeScanned(scanned)
            } else {
                onScanError("No barcode detected")
            }
        } else {
            onScanError("Scan cancelled")
        }
    }

    IconButton(
        modifier = modifier,
        onClick = {
            val intent = Intent(context, BarcodeScannerActivity::class.java).apply {
                putExtra(BarcodeScannerExtras.PromptText, prompt)
            }
            scanLauncher.launch(intent)
        },
    ) {
        Icon(
            imageVector = Icons.Outlined.QrCodeScanner,
            contentDescription = "Scan barcode",
        )
    }
}
