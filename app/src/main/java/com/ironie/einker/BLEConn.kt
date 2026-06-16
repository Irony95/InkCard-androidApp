package com.ironie.einker

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Queue
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

class BLEConn(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter,
    private val btn1Function: () -> Unit,
    private val btn2Function: () -> Unit,
    private val onDisconnect: () -> Unit,
) {


    private val MTU_SIZE = 517
    private val CHUNK_SIZE = MTU_SIZE - 5
    private val deviceName = "Eink Card"

    private val serviceUUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    private val imageUUID = UUID.fromString("cba1d466-344c-4be3-ab3f-189f80dd7518")
    private val btn1UUID = UUID.fromString("f78ebbff-c8b7-4107-93de-889a6a06d408")
    private val btn2UUID = UUID.fromString("ca73b3ba-39f6-4ab3-91ae-186dc9577d99")
    private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    var bluetoothGatt: BluetoothGatt? = null
    private var deviceAddress: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private var isSendingData = false
    var deviceConnected = false

    private var onConnectFunction: (() -> Unit)? = null
    private var imageQueue = ArrayDeque<Byte>()

    private var functionQueue = ConcurrentLinkedQueue<BLEFunction>()

    private val scanCallback: ScanCallback = object : ScanCallback() {
        @RequiresPermission(anyOf = (arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)))
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (result.device.name == deviceName)
            {
                Log.d("test", "device found!")
                deviceAddress = result.device.address
                stopScanning()
            }
        }
    }

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt?.requestMtu(MTU_SIZE)
                Log.d("test", "connceted to device $status")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                onDisconnect()
                deviceConnected = false
                deviceAddress = null
                Log.d("test", "disconnected from device $status")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (imageQueue.isEmpty())
            {
                isSendingData = false
                return
            }

            var byteCount = if (imageQueue.size > CHUNK_SIZE) CHUNK_SIZE else imageQueue.size
            var byteList = List(byteCount) { imageQueue.removeFirst() }
            val imgChar = gatt?.getService(serviceUUID)?.getCharacteristic(imageUUID)
            gatt?.writeCharacteristic(imgChar!!, byteList.toByteArray(), WRITE_TYPE_NO_RESPONSE)
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.d("test", "MTU changed to $mtu with status ${status == BluetoothGatt.GATT_SUCCESS}")
            super.onMtuChanged(gatt, mtu, status)
            gatt?.discoverServices()
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            Log.d("test", "discovered services")
            if (gatt?.getService(serviceUUID) != null)
            {
                enableNotifications(gatt, btn1UUID)
                //we need to make a queue
                //enableNotifications(gatt, btn2UUID)
                deviceConnected = true
                onConnectFunction?.invoke()
            }
        }
        fun enableNotifications(gatt: BluetoothGatt, char: UUID) {
            checkBluetoothConnectPermission()
            val service = gatt.getService(serviceUUID)
            val characteristic = service!!.getCharacteristic(char)
            gatt.setCharacteristicNotification(characteristic, true)

            val descriptor = characteristic.getDescriptor(CCCD_UUID)
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, chara: BluetoothGattCharacteristic, value: ByteArray) {
            super.onCharacteristicChanged(gatt, chara, value)
            Log.d("test", "changed")
            when (chara.uuid)
            {
                btn1UUID -> { btn1Function() }
                btn2UUID -> { btn2Function() }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)
            Log.d("test", value.toString())
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun scanForDevice(scanDuration: Long)
    {
        val filters = listOf(
            ScanFilter.Builder()
                .setDeviceName(deviceName) // Filter by device name
                .build()
        )

    // Define ScanSettings
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // Adjust based on use case
            .build()

        //start scanning for device
        if (!isScanning)
        {
            isScanning = true
            bluetoothLeScanner.startScan(filters, scanSettings, scanCallback)
            handler.postDelayed({ stopScanning() }, scanDuration*1000)
        }
        //restart Scan
        else
        {
            stopScanning()
            scanForDevice(scanDuration)
        }
    }

    fun foundDevice(): Boolean {
        return deviceAddress != null
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(onConnect: (() -> Unit)? = null) {
        val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
        bluetoothGatt = device.connectGatt(context, false, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE)
        onConnectFunction = onConnect
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScanning()
    {
        isScanning = false
        bluetoothLeScanner.stopScan(scanCallback)
    }


    fun disconnect()
    {
        checkBluetoothConnectPermission()
        deviceConnected = false
        deviceAddress = null
        bluetoothGatt?.close()
    }


    fun sendImage(image: ByteArray, imageType: Int, timeout: Int = 5000)
    {
        checkBluetoothConnectPermission()
        //already sending some data, do not send again to avoid messing up sequence
        if (isSendingData || bluetoothGatt == null)
            return
        val service = bluetoothGatt!!.getService(serviceUUID)
        val imageChar = service.getCharacteristic(imageUUID)

        imageQueue.addAll(image.toList())
//        val len = ByteBuffer
//            .allocate(Int.SIZE_BYTES)
//            .order(ByteOrder.LITTLE_ENDIAN)
//            .putInt(imageQueue.size)
//            .array()
        val type = byteArrayOf(imageType.toByte())
        //the rest of the data will be sent on callback
        Log.d("test", "written char")
        bluetoothGatt!!.writeCharacteristic(imageChar, type, WRITE_TYPE_NO_RESPONSE)
//        isSendingData = true
    }

    fun checkBluetoothConnectPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }
}