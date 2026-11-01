package com.nachehab.projectorremote

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.nachehab.projectorremote.ui.RemoteScreen
import com.nachehab.projectorremote.ui.RemoteTheme

class MainActivity : ComponentActivity() {

    private val viewModel: RemoteViewModel by viewModels()

    private val bluetoothPermissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            emptyArray()
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) {
                viewModel.onBluetoothPermissionsGranted()
            } else {
                Toast.makeText(this, "Bluetooth permission is needed for Bluetooth control", Toast.LENGTH_LONG).show()
            }
        }

    private val systemDialogLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.refreshPaired()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        ensureBluetoothPermissions()

        setContent {
            RemoteTheme {
                RemoteScreen(
                    viewModel = viewModel,
                    onRequestBluetoothPermission = ::ensureBluetoothPermissions,
                    onEnableBluetooth = {
                        launchSafely(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    },
                    onMakeDiscoverable = {
                        launchSafely(
                            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                        )
                    },
                    onToast = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasBluetoothPermissions()) viewModel.refreshPaired()
    }

    private fun hasBluetoothPermissions() = bluetoothPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureBluetoothPermissions() {
        if (hasBluetoothPermissions()) {
            viewModel.onBluetoothPermissionsGranted()
        } else {
            permissionLauncher.launch(bluetoothPermissions)
        }
    }

    private fun launchSafely(intent: Intent) {
        if (!hasBluetoothPermissions()) {
            ensureBluetoothPermissions()
            return
        }
        runCatching { systemDialogLauncher.launch(intent) }
            .onFailure { Toast.makeText(this, "Not available on this phone", Toast.LENGTH_SHORT).show() }
    }
}
