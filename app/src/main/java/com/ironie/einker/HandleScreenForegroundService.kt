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
import com.ironie.einker.ImageHandler.Companion.convertToBW
import com.ironie.einker.ImageHandler.Companion.convertToBWByteArray
import com.ironie.einker.ImageHandler.Companion.cropAndScaleImage
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
    LR_TAP("Tap left and right"),
    REFRESH("Refresh screen"),
    SCROLL("Scroll top and bottom")
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

    val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    lateinit var bluetoothManager: BluetoothManager
    var bluetoothAdapter: BluetoothAdapter? = null
    var bleConn: BLEConn? = null

    lateinit var mView: View
    lateinit var mwindowManager: WindowManager
    var overviewIsFullscreen = false
    var overviewHidden = false

    var screenOverlay: ScreenOverlay? = null

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

    private fun setOverviewFullscreen(isFullscreen: Boolean) {
        if (isFullscreen) mView.findViewById<LinearLayout>(R.id.layout_buttons).visibility = View.GONE
        if (isFullscreen) overviewHidden = true

        val height = if (isFullscreen) WindowManager.LayoutParams.MATCH_PARENT
            else WindowManager.LayoutParams.WRAP_CONTENT
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP

        val text = if (isFullscreen) "Enable Touch" else "Disable Touch"
        mView.findViewById<Button>(R.id.btn_enable_screen).text = text
        mwindowManager.updateViewLayout(mView, params)
    }

    //invoked when startService() is called
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("test", "onstart")
        if (intent?.action.equals(ACTION_STOP_SERVICE))
            pauseService()

        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter

        screenOverlay = ScreenOverlay(this@HandleScreenForegroundService) { pauseService() }

        if (intent?.action.equals(ACTION_START_SERVICE)) {
            startForeground()

            val useGrayscale = intent?.getBooleanExtra("useGrayscale", false)
            val physicalBtnFunction = intent?.getSerializableExtra("btnFunction") as ButtonFunction
            Log.d("test", physicalBtnFunction.desc)
            screenOverlay!!.showOverlay()

            bleConn = BLEConn(
                this@HandleScreenForegroundService,
                bluetoothManager,
                {},
                {},
                { pauseService() }
            )

            scope.launch {
                bleConn?.checkBluetoothConnectPermission()
                bleConn?.connectToDevice()

                delay(100000)
            }


//            mmSocket = device!!.createRfcommSocketToServiceRecord(BTMODULEUUID)
//            mmSocket.let { socket ->
//                try {
//                    socket?.connect()
//                    val inStream: InputStream = socket!!.inputStream
//                    val outStream: OutputStream = socket.outputStream
//                    val buffer = ByteArray(1024)
//                    SERVICE_RUNNING = true
//                    scope.launch {
//                        while (SERVICE_RUNNING && socket.isConnected)
//                            loop(inStream, outStream, buffer)
//                    }
//                } catch (e : Exception) { stopSelf() }
//            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private var prevBtn1State = false
    private var prevBtn2State = false

    private suspend fun loop(inStream: InputStream, outStream: OutputStream, buffer: ByteArray) = withContext(Dispatchers.IO)
    {
        //blocking until command
        if (inStream.available() != 0)
        {

            val v = inStream.read()
            //received status
            if (v.toUByte().toInt() == 0xF1)
            {
                inStream.read(buffer)
                val btn1 = (buffer[0].toInt() and 0b10000) != 0
                val btn2 = (buffer[0].toInt() and 0b100000) != 0
                //button Pressed
                if ((btn1 && !prevBtn1State) || (btn2 && !prevBtn2State))
                {
                    val buttonPressed = if (btn1) 1 else 2

                    withContext(Dispatchers.Main)
                    {
                        val currentOverviewSetting = overviewIsFullscreen
                        setOverviewFullscreen(false)
                        delay(100)
                        performScreenFunction(buttonPressed, ButtonFunction.LR_TAP)
                        delay(500)
                        setOverviewFullscreen(currentOverviewSetting)
                    }
                    delay(100)
                    val pic = getScreenBitmap() ?: return@withContext
                    val bwMat = convertToBW(pic)
                    val formatted = cropAndScaleImage(bwMat, bwMat.width(), 0, 70)
//                    val cropped = cropSides(bwMat, top = 70, bottom = 150)
//                    val formatted = fitAndScaleImage(cropped)
                    val arr = convertToBWByteArray(formatted)
//                    ImageHandler.sendBWImage(arr, outStream, inStream)
                }
                broadcastToActivity(buffer)

                prevBtn1State = btn1
                prevBtn2State = btn2
            }
        }
        delay(50)
    }

    suspend fun performScreenFunction(button: Int, function: ButtonFunction): Boolean
    {
        val screenHeight = mwindowManager.currentWindowMetrics.bounds.height()
        val screenWidth = mwindowManager.currentWindowMetrics.bounds.width()

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
        val stroke = GestureDescription.StrokeDescription(taps, 0, 200, false)
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

    fun pauseService()
    {

        screenOverlay?.stopOverlay()
//        (mView.parent as ViewGroup).removeAllViews()

        scope.coroutineContext.cancelChildren()
        val intent = Intent()
        intent.action = ACTION_BROADCAST_TO_ACTIVITY
        intent.putExtra("StopService", true)
        sendBroadcast(intent)
        SERVICE_RUNNING = false
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

    fun broadcastToActivity(data: ByteArray)
    {
        val intent = Intent()
        intent.action = ACTION_BROADCAST_TO_ACTIVITY
        intent.putExtra("Data", data)
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