package com.ironie.einker

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.delay

class BLEScanner(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter,
    private val deviceAddress: MutableLiveData<String?>
) {
    private val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    private val deviceName = "Eink Card"
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    private val scanCallback: ScanCallback = object : ScanCallback() {
        @RequiresPermission(anyOf = (arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)))
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (result.device.name == deviceName && deviceAddress.value == null)
                deviceAddress.postValue(result.device.address)

        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan(rescanDuration: Long)
    {
        isScanning = true
        scanForDevice(rescanDuration)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun scanForDevice(rescanDuration: Long)
    {
        if (!hasBluetoothPermissions())
            return

        val filters = listOf(
            ScanFilter.Builder()
                .setDeviceName(deviceName) // Filter by device name
                .build()
        )

        // Define ScanSettings
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothLeScanner.startScan(filters, scanSettings, scanCallback)
        handler.postDelayed(
            {
                deviceAddress.postValue(null)
                bluetoothLeScanner.stopScan(scanCallback)
                if (isScanning)
                    scanForDevice(rescanDuration)
            }, rescanDuration*1000)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScanning()
    {
        if (!hasBluetoothPermissions())
            return
        isScanning = false
        bluetoothLeScanner.stopScan(scanCallback)
    }

    fun hasBluetoothPermissions(): Boolean {
        var hasPerms = true
        //bluetooth
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED)
            hasPerms = false

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED)
            hasPerms = false
        return hasPerms
    }
}