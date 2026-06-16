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
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PixelFormat
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.coroutines.resume


enum class ButtonFunction(val desc: String) {
    REFRESH("Refresh screen only"),
    LR_TAP("Tap left and right"),
    SCROLL("Scroll top and bottom")
}

enum class ImageUpdateType(val desc: Int) {
    BLACK_WHITE_FAST(1),
    BLACK_WHITE(2),
    FOUR_GRAY_WHITE(3),
    FOUR_GRAY(4),
}

/*
* https://developer.android.com/reference/android/accessibilityservice/AccessibilityService.html
* accessibility service cannot be started from in-app, so we need to maintain the service and never kill the service.
* This also means that when not running, we need to minimize the performance impact of the service.
* */
@SuppressLint("AccessibilityPolicy")
class HandleScreenForegroundService : AccessibilityService() {
    companion object {
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_BROADCAST_TO_ACTIVITY = "BROADCAST_CARD"
        var SERVICE_RUNNING = false
    }
    val searchTimeout = 5
    val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    lateinit var bluetoothManager: BluetoothManager
    var bluetoothAdapter: BluetoothAdapter? = null
    var bleConn: BLEConn? = null
    var screenOverlay: ScreenOverlay? = null
    var imagesSent = 0
    var refreshRate = 1


    //invoked when service is created, i.e. setup
    override fun onCreate() {
        super.onCreate()
        Log.d("test", "oncreate")

        if (OpenCVLoader.initLocal()) {
            Log.i("test", "OpenCV loaded successfully")
        } else {
            Log.e("test", "OpenCV initialization failed!")
            (Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG)).show()
            return
        }
        createNotificationChannel()
    }

    //invoked when startService() is called
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("test", "onstartCommand")
        if (intent?.action.equals(ACTION_STOP_SERVICE))
            pauseService()

        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter

        screenOverlay = ScreenOverlay(this@HandleScreenForegroundService) { pauseService() }

        if (intent?.action.equals(ACTION_START_SERVICE)) {
            startForeground()

            val useGrayscale = intent?.getBooleanExtra("useGrayscale", false)
            refreshRate = intent?.getIntExtra("fullRefreshRate", 1)!!
            val physicalBtnFunction = intent.getSerializableExtra("btnFunction", ButtonFunction::class.java)
            Log.d("test", "refresh rate at $refreshRate")
            Log.d("test", physicalBtnFunction!!.desc)
            screenOverlay!!.showOverlay()

            suspend fun btn1Pressed()
            {
                if (bleConn?.deviceConnected == false)
                    return
                Log.d("test", "btn1 pressed")

                        withContext(Dispatchers.Main) {
                            val overlayIsFullscreen = screenOverlay!!.overviewIsFullscreen
                            screenOverlay?.setOverviewFullscreen(false)
                            delay(100)
                            performScreenFunction(1, ButtonFunction.LR_TAP)
                            delay(500)
                            screenOverlay?.setOverviewFullscreen(overlayIsFullscreen)
                        }

                val pic = getScreenBitmap()
                val arr = ImageHandler.formatAndConvert(pic!!)
                val addition = if (imagesSent % refreshRate == 0) 1 else 0
                Log.d("test", "length of image bytes: ${arr.size}")
                bleConn?.sendImage(arr, 0x02)
                imagesSent++
            }

            suspend fun btn2Pressed()
            {
                if (bleConn?.deviceConnected == false)
                    return
                Log.d("test", "btn2 pressed")

                    withContext(Dispatchers.Main) {
                        val overlayIsFullscreen = screenOverlay!!.overviewIsFullscreen
                        screenOverlay?.setOverviewFullscreen(false)
                        delay(100)
                        performScreenFunction(2, ButtonFunction.LR_TAP)
                        delay(500)
                        screenOverlay?.setOverviewFullscreen(overlayIsFullscreen)
                        }

                val pic = getScreenBitmap()
                val arr = ImageHandler.formatAndConvert(pic!!)
                val addition = if (imagesSent % refreshRate == 0) 1 else 0
                Log.d("test", "length of image bytes: ${arr.size}")
                bleConn?.sendImage(arr, 0x02)
                imagesSent++
            }


            bleConn = BLEConn(
                this@HandleScreenForegroundService,
                bluetoothAdapter!!,
                { scope.launch { btn1Pressed() } },
                { scope.launch { btn2Pressed() } },
                { pauseService("Device connection lost") }
            )

            //start search for card device
            scope.launch {
                val currTime = System.currentTimeMillis()
                bleConn?.checkBluetoothConnectPermission()
                bleConn?.scanForDevice( 30)
                while (!(bleConn!!.foundDevice())) {
                    if (System.currentTimeMillis() - currTime > searchTimeout*1000)
                    {
                        Log.d("test", "lah")
                        pauseService("Could not find card, is it powered on?")
                        return@launch
                    }
                    delay(1)
                }
                bleConn?.connectToDevice() {
                    scope.launch {
                        val pic = getScreenBitmap()
                        val arr = ImageHandler.formatAndConvert(pic!!)
                        bleConn?.sendImage(arr, ImageUpdateType.BLACK_WHITE.desc)
                    }
                }

                imagesSent = 0
                SERVICE_RUNNING = true

                broadcastToActivity()
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    suspend fun performScreenFunction(button: Int, function: ButtonFunction): Boolean
    {
        val screenHeight = screenOverlay!!.windowManager.currentWindowMetrics.bounds.height()
        val screenWidth = screenOverlay!!.windowManager.currentWindowMetrics.bounds.width()

        when (function) {
            ButtonFunction.LR_TAP -> {
                if (button == 1)
                    performTap(20f, screenHeight/2f)
                else
                    performTap(screenWidth-20f, screenHeight/2f)
            }
            else -> Log.d("test", "what??")
        }

        return true
    }

    suspend fun performTap(x: Float, y: Float): Boolean = suspendCancellableCoroutine { continuation ->
        val taps = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(taps, 0, 100, false)
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

    private suspend fun getScreenBitmap(): Bitmap? = suspendCancellableCoroutine { continuation ->
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(p0: ScreenshotResult) {
                val bitmap = Bitmap.wrapHardwareBuffer(p0.hardwareBuffer, p0.colorSpace)
                continuation.resume(bitmap)
            }

            override fun onFailure(p0: Int) {
                continuation.resume(null)
                Log.d("test", "Failed to take picture with error code $p0")
            }
        })
    }

    private fun startForeground()
    {
        try {
            val service = Intent(this, HandleScreenForegroundService::class.java)
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
           Log.d("test", "cannot start service??")
        }
    }

    override fun onAccessibilityEvent(p0: AccessibilityEvent?) { }
    override fun onInterrupt() { }

    fun broadcastToActivity()
    {
        val intent = Intent()
        intent.action = ACTION_BROADCAST_TO_ACTIVITY
        intent.putExtra("Connected", SERVICE_RUNNING)
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