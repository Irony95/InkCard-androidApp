package com.ironie.einker

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BLEConn(
    private val bleContext: Context,
    private val bluetoothManager: BluetoothManager,
    private val btn1Function: () -> Unit,
    private val btn2Function: () -> Unit,
    private val onDisconnect: () -> Unit,
) {
    private var context: Context? = null

    private val serviceUUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    private lateinit var service: BluetoothGattService
    private val imageUUID = UUID.fromString("cba1d466-344c-4be3-ab3f-189f80dd7518")
    private lateinit var imageChara: BluetoothGattCharacteristic
    private val btn1UUID = UUID.fromString("f78ebbff-c8b7-4107-93de-889a6a06d408")
    private lateinit var btn1Chara: BluetoothGattCharacteristic
    private val btn2UUID = UUID.fromString("ca73b3ba-39f6-4ab3-91ae-186dc9577d99")
    private lateinit var btn2Chara: BluetoothGattCharacteristic
    private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    var gattServer: BluetoothGattServer? = null
    var gattAdvertiser: BluetoothLeAdvertiser? = null
    var cardDevice: BluetoothDevice? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d("test", "start success")
            super.onStartSuccess(settingsInEffect)
        }

        override fun onStartFailure(errorCode: Int) {
            Log.d("test", "start failed error code $errorCode")
            super.onStartFailure(errorCode)
        }
    }

    val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int
        ) {
            Log.d("test", "new device connected")
            cardDevice = device
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            Log.d("test", "chara read req")
            // Send data back to the client
//            gattServer!!.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.value)
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice?, requestId: Int,
            characteristic: BluetoothGattCharacteristic?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            Log.d("test", "chara got written")
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice()
    {
        context = bleContext
        val bluetoothAdapter = bluetoothManager.adapter
        bluetoothAdapter.name = "Eink Server"
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)

        service = BluetoothGattService(serviceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        imageChara = BluetoothGattCharacteristic(imageUUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val cccd = BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        imageChara.addDescriptor(cccd)
        service.addCharacteristic(imageChara)

        btn1Chara = BluetoothGattCharacteristic(btn1UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(btn1Chara)

        btn2Chara = BluetoothGattCharacteristic(btn2UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(btn2Chara)

        gattServer!!.addService(service)
        gattAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(serviceUUID))
            .build()
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        gattAdvertiser!!.startAdvertising(settings, data, scanResponse, advertiseCallback)

        while (cardDevice == null) { }
        Log.d("test", "connceted")
        gattServer.
//        gattServer?.notifyCharacteristicChanged(cardDevice!!, imageChara, false, byteArrayOf(0x01))
    }

    fun disconnect()
    {
        checkBluetoothConnectPermission()
        gattAdvertiser?.stopAdvertising(advertiseCallback)
        gattServer?.connectedDevices?.forEach {
            gattServer!!.cancelConnection(it)
        }
        gattServer?.close()
    }

    private val CHUNK_SIZE = 511
    private val CHUNK_OK = 0xFF
    fun sendBWImage(image: ByteArray, outStream: OutputStream, inStream: InputStream)
    {
        outStream.write(0x01)
        while (inStream.read().toUByte().toInt() != CHUNK_OK) { /* wait for OK */ }
        var bytesSent = 0
        while (bytesSent < image.size)
        {
            val currentChunkSize = minOf(CHUNK_SIZE, image.size - bytesSent)
            outStream.write(image, bytesSent, currentChunkSize)
            outStream.flush()

            if (currentChunkSize == CHUNK_SIZE) {
                while (inStream.read().toUByte().toInt() != CHUNK_OK) { /* Wait */ }
            }
            bytesSent += currentChunkSize
        }
    }


    fun checkBluetoothConnectPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            bleContext,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }
}