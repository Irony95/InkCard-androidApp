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
    private val deviceAddress: String
) {
    sealed class BLEOperation
    class Connect: BLEOperation()
    class Disconnect: BLEOperation()
    class CharWrite(val service: UUID, val char: UUID, val data: ByteArray): BLEOperation()
    class MTURequest(val mtu: Int): BLEOperation()
    class DescriptorWrite(val service: UUID, val char: UUID, val descriptor: UUID, val value: ByteArray): BLEOperation()
    class DiscoverService: BLEOperation()

    private val MTU_SIZE = 517
    private val CHUNK_SIZE = MTU_SIZE - 5

    private val serviceUUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    private val imageUUID = UUID.fromString("cba1d466-344c-4be3-ab3f-189f80dd7518")
    private val btn1UUID = UUID.fromString("f78ebbff-c8b7-4107-93de-889a6a06d408")
    private val btn2UUID = UUID.fromString("ca73b3ba-39f6-4ab3-91ae-186dc9577d99")
    private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")


    var btn1Function: (() -> Unit)? = null
    var btn2Function: (() -> Unit)? = null
    var onDisconnect: (() -> Unit)? = null
    var onConnect: (() -> Unit)? = null
    var bluetoothGatt: BluetoothGatt? = null

    var deviceConnected = false

    private var operationQueue = ConcurrentLinkedQueue<BLEOperation>()
    private var pendingOperation: BLEOperation? = null

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (!hasBluetoothPermissions())
                return

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("test", "connected to device!")
                deviceConnected = true
                enqueueOperation(DiscoverService())
                enqueueOperation(MTURequest(MTU_SIZE))

                if (pendingOperation is Connect)
                    signalEndOfOperation()

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("test", "disconnected from device $status")
                onDisconnect?.invoke()
                deviceConnected = false

                if (pendingOperation is Disconnect)
                    signalEndOfOperation()
            }
        }
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        fun enableNotifications(char: UUID) {
            if (!hasBluetoothPermissions())
                return

            val characteristic = bluetoothGatt?.getService(serviceUUID)?.getCharacteristic(char)
            bluetoothGatt?.setCharacteristicNotification(characteristic, true)
            enqueueOperation(DescriptorWrite(serviceUUID,
                char,
                CCCD_UUID,
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            )
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            if (!hasBluetoothPermissions())
                return
            if (pendingOperation is DescriptorWrite)
                signalEndOfOperation()
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (!hasBluetoothPermissions())
                return

            super.onCharacteristicWrite(gatt, characteristic, status)
            if (pendingOperation is CharWrite)
                signalEndOfOperation()
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            if (!hasBluetoothPermissions())
                return

            Log.d("test", "MTU changed to $mtu with status ${status == BluetoothGatt.GATT_SUCCESS}")
            super.onMtuChanged(gatt, mtu, status)

            if (pendingOperation is MTURequest)
                signalEndOfOperation()
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (!hasBluetoothPermissions())
                return

            Log.d("test", "discovered services")
            enableNotifications(btn1UUID)
            enableNotifications(btn2UUID)
            onConnect?.invoke()
            if (pendingOperation is DiscoverService)
                signalEndOfOperation()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, chara: BluetoothGattCharacteristic, value: ByteArray) {
            super.onCharacteristicChanged(gatt, chara, value)
            when (chara.uuid)
            {
                btn1UUID -> btn1Function?.invoke()
                btn2UUID -> btn2Function?.invoke()
            }
        }


        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Synchronized
        private fun signalEndOfOperation() {
            if (!hasBluetoothPermissions())
                return

            pendingOperation = null
            if (operationQueue.isNotEmpty()) {
                performNextOperation()
            }
        }
    }
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Synchronized
    private fun enqueueOperation(func: BLEOperation) {
        if (!hasBluetoothPermissions())
            return

        operationQueue.add(func)
        if (pendingOperation == null)
            performNextOperation()

    }
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Synchronized
    private fun performNextOperation() {
        if (!hasBluetoothPermissions())
            return

        if (pendingOperation != null) {
            Log.d(
                "test",
                "function called when we still waiting on an operation, $pendingOperation"
            )
            return
        }
        val op = operationQueue.poll() ?: return
        pendingOperation = op
        when (op) {
            is Connect -> {
                val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
                bluetoothGatt = device.connectGatt(
                    context,
                    false,
                    bluetoothGattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )
            }

            is CharWrite -> {
                val char = bluetoothGatt?.getService(op.service)?.getCharacteristic(op.char)
                bluetoothGatt?.writeCharacteristic(char!!, op.data, WRITE_TYPE_NO_RESPONSE)
            }

            is DescriptorWrite -> {
                val char = bluetoothGatt?.getService(op.service)?.getCharacteristic(op.char)
                val descriptor = char!!.getDescriptor(op.descriptor)
                bluetoothGatt?.writeDescriptor(descriptor, op.value)
            }

            is Disconnect -> bluetoothGatt?.close()
            is MTURequest -> bluetoothGatt?.requestMtu(op.mtu)
            is DiscoverService -> bluetoothGatt?.discoverServices()
        }
    }
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice()
    {
        if (!hasBluetoothPermissions())
            return
        enqueueOperation(Connect())
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect()
    {
        if (!hasBluetoothPermissions())
            return
        enqueueOperation(Disconnect())
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendImage(image: ByteArray, imageType: Int)
    {
        if (!hasBluetoothPermissions())
            return
        //already have some data, dont start sending any more
        if (bluetoothGatt == null)
            return
        enqueueOperation(CharWrite(serviceUUID, imageUUID, byteArrayOf(imageType.toByte())))

        val chunks = (image.size / CHUNK_SIZE)+1
        for (i in 0 until chunks)
        {
            val bufferSize = if ((i+1)*CHUNK_SIZE > image.size) image.size % CHUNK_SIZE else CHUNK_SIZE
            val data = image.copyOfRange((i*CHUNK_SIZE), (i*CHUNK_SIZE + bufferSize))
            enqueueOperation(CharWrite(serviceUUID, imageUUID, data))
        }
    }

    fun hasBluetoothPermissions(): Boolean {
        //bluetooth
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED)
            return false

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED)
            return false
        return true
    }
}