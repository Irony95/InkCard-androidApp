package com.ironie.einker

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Path
import android.util.Log
import android.view.Display
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import kotlin.coroutines.resume

enum class ButtonFunction(val desc: String) {
    REFRESH("Refresh screen only"),
    LR_TAP("Tap left and right"),
    SCROLL("Scroll top and bottom")
}

enum class ImageUpdateType(val desc: Int) {
    BLACK_WHITE_FAST(1),
    BLACK_WHITE(2),
    FOUR_GRAY_REG_ONE(3),
    FOUR_GRAY_REG_TWO(4),
}

/*
* https://developer.android.com/reference/android/accessibilityservice/AccessibilityService.html
* accessibility service cannot be started from in-app, so we need to maintain the service and never kill the service.
* This also means that when not running, we need to minimize the performance impact of the service.
* */
@SuppressLint("AccessibilityPolicy")
class EinkCardForegroundService : AccessibilityService() {
    companion object {
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_BROADCAST_TO_ACTIVITY = "BROADCAST_CARD"
        var SERVICE_RUNNING = false
    }
    val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    lateinit var bluetoothManager: BluetoothManager
    var bluetoothAdapter: BluetoothAdapter? = null
    var bleConn: BLEConn? = null
    var screenOverlay: ScreenOverlay? = null
    var imagesSent = 0
    var refreshRate = 1

    var useDither = true
    var useGrayscale = false
    var invertColors = false


    //invoked when service is created, i.e. setup
    override fun onCreate() {
        super.onCreate()

        if (!OpenCVLoader.initLocal()) {
            (Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG)).show()
            return
        }
        createNotificationChannel()
    }

    //invoked when startService() is called
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action.equals(ACTION_STOP_SERVICE))
            pauseService()

        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter

        screenOverlay = ScreenOverlay(this@EinkCardForegroundService) { pauseService() }

        if (intent?.action.equals(ACTION_START_SERVICE)) {
            startForeground()
            if (intent == null)
                return super.onStartCommand(intent, flags, startId)

            val deviceAddress = intent.getStringExtra("deviceAddress")

            useGrayscale = intent.getBooleanExtra("useGrayscale", false)
            useDither = intent.getBooleanExtra("useDither", true)
            invertColors = intent.getBooleanExtra("invertColors", false)
            refreshRate = intent.getIntExtra("fullRefreshRate", 1)
            val physicalBtnFunction = intent.getSerializableExtra("btnFunction", ButtonFunction::class.java)

            suspend fun btn1Pressed()
            {
                if (bleConn?.deviceConnected == false)
                    return

                withContext(Dispatchers.Main)
                {
                    performScreenFunction(1, physicalBtnFunction!!)
                    delay(200)
                    updateScreen()
                }

            }

            suspend fun btn2Pressed()
            {
                if (bleConn?.deviceConnected == false)
                    return

                withContext(Dispatchers.Main)
                {
                    performScreenFunction(2, physicalBtnFunction!!)
                    delay(200)
                    updateScreen()
                }
            }


            bleConn = BLEConn(this@EinkCardForegroundService, bluetoothAdapter!!, deviceAddress!!)
            bleConn!!.btn1Function = { scope.launch { btn1Pressed() } }
            bleConn!!.btn2Function = { scope.launch { btn2Pressed() } }
            bleConn!!.onDisconnect = { pauseService("Device connection lost") }
            bleConn!!.onConnect = {
                imagesSent = 0
                scope.launch { withContext(Dispatchers.Main) { updateScreen() } }
                SERVICE_RUNNING = true
                broadcastToActivity()
            }

            scope.launch {
                bleConn?.connectToDevice()
            }
            screenOverlay!!.showOverlay()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    suspend fun performScreenFunction(button: Int, function: ButtonFunction)
    {
        val scrollPercent = 0.80f

        if (function == ButtonFunction.REFRESH)
            return
        val screenHeight = screenOverlay!!.windowManager.currentWindowMetrics.bounds.height().toFloat()
        val screenWidth = screenOverlay!!.windowManager.currentWindowMetrics.bounds.width().toFloat()

        screenOverlay?.waitSetTouchable(true)
        delay(10)
        when (function) {
            ButtonFunction.LR_TAP -> {
                if (button == 1)
                    performGesture(Path().apply {
                        moveTo(20f, screenHeight/2f)
                    }, 100)
                else
                    performGesture(Path().apply {
                        moveTo(screenWidth-20f, screenHeight/2f)
                    }, 100)
            }
            ButtonFunction.SCROLL -> {
                if (button == 1)
                    performGesture(Path().apply {
                        moveTo(screenWidth/2, screenHeight*(scrollPercent))
                        lineTo(screenWidth/2, screenHeight*(1.0f-scrollPercent))
                    }, 1000)
                else
                    performGesture(Path().apply {
                        moveTo(screenWidth/2, screenHeight*(1.0f-scrollPercent))
                        lineTo(screenWidth/2, screenHeight*(scrollPercent))
                    }, 1000)
            }
            else -> Log.d("test", "what??")
        }
        screenOverlay?.waitSetTouchable(false)
    }

    suspend fun performGesture(path: Path, duration: Long): Boolean = suspendCancellableCoroutine { continuation ->
        val stroke = GestureDescription.StrokeDescription(path, 0, duration, false)
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(stroke)

        dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                if (continuation.isActive) continuation.resume(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                if (continuation.isActive) continuation.resume(false)
            }
        }, null)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun pauseService(message: String? = null)
    {
        SERVICE_RUNNING = false
        screenOverlay?.stopOverlay()

        scope.coroutineContext.cancelChildren()
        val intent = Intent()
        intent.action = ACTION_BROADCAST_TO_ACTIVITY
        intent.putExtra("StopService", true)
        if (message != null)
            intent.putExtra("StopMessage", message)
        sendBroadcast(intent)
        SERVICE_RUNNING = false
        broadcastToActivity()
        stopForeground(STOP_FOREGROUND_REMOVE)

        bleConn?.disconnect()
        bleConn = null
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun updateScreen()
    {
        screenOverlay?.setHideOverlay(true)
        delay(10)
        val pic = getScreenBitmap()
        screenOverlay?.setHideOverlay(false)


        if (useGrayscale)
        {
            val (arr1, arr2) = ImageProcesses.formatAndConvert4Gray(pic!!, useDither, invertColors)
            bleConn?.sendImage(arr1, ImageUpdateType.FOUR_GRAY_REG_ONE.desc)
            bleConn?.sendImage(arr2, ImageUpdateType.FOUR_GRAY_REG_TWO.desc)
            imagesSent++
        }
        else
        {
            val arr = ImageProcesses.formatAndConvertBW(pic!!, useDither, invertColors)
            val refreshType = if (imagesSent % refreshRate == 0) ImageUpdateType.BLACK_WHITE else ImageUpdateType.BLACK_WHITE_FAST
            bleConn?.sendImage(arr, refreshType.desc)
            imagesSent++
        }
    }


    private suspend fun getScreenBitmap(): Bitmap? = suspendCancellableCoroutine { continuation ->
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(p0: ScreenshotResult) {
                val bitmap = Bitmap.wrapHardwareBuffer(p0.hardwareBuffer, p0.colorSpace)
                continuation.resume(bitmap)
            }

            override fun onFailure(p0: Int) {
                continuation.resume(null)
                Toast.makeText(this@EinkCardForegroundService,
                    "failed to take screenshot with code $p0", Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun startForeground()
    {
        try {
            val service = Intent(this, EinkCardForegroundService::class.java)
                .apply { action = ACTION_STOP_SERVICE }
            val pendingIntent = PendingIntent.getService(this, 0, service, PendingIntent.FLAG_IMMUTABLE)

            val notification = NotificationCompat.Builder(this, "CHANNEL_ID")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("E-ink Card")
                .setContentText("E-ink card is currently running and displaying over other apps. Click below to stop")
                .addAction(R.drawable.outline_adjust_24, "Stop", pendingIntent)
                .build()
            ServiceCompat.startForeground(
                /* service = */ this,
                /* id = */ 100, // Cannot be 0
                /* notification = */ notification,
                /* foregroundServiceType = */
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } catch (e: Exception) {
            Toast.makeText(this@EinkCardForegroundService,
                "unable to start service with exception ${e.message}", Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onAccessibilityEvent(p0: AccessibilityEvent?) { }
    override fun onInterrupt() { }

    fun broadcastToActivity()
    {
        val intent = Intent()
        intent.action = ACTION_BROADCAST_TO_ACTIVITY
        intent.putExtra("cardConnection", SERVICE_RUNNING)
        sendBroadcast(intent)
    }

    private fun createNotificationChannel()
    {
        val channel = NotificationChannel(
            "CHANNEL_ID",
            "einker",
            NotificationManager.IMPORTANCE_HIGH,
        )
        channel.description = "einker foreground service notification"

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        Toast.makeText(this, "Service stopped, something went wrong",
            Toast.LENGTH_SHORT).show()
        super.onDestroy()
    }
}