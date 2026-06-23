package com.ironie.einker

import android.Manifest
import android.R
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.ironie.einker.ui.theme.EinkerTheme
import org.opencv.android.OpenCVLoader


class MainActivity : ComponentActivity()  {

    val cardConnected = MutableLiveData(false)
    val hasPermissions = mutableStateOf(false)

    val accessibilityRunning = mutableStateOf(false)
    lateinit var bleScanner: BLEScanner
    val deviceAddress = MutableLiveData<String?>(null)

    //updates from the foreground service
    val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            if (p1 == null)
                return
            if (p1.action == EinkCardForegroundService.ACTION_BROADCAST_TO_ACTIVITY)
                if (p1.getBooleanExtra("StopService", false))
                {
                    cardConnected.postValue(false)
                    if (hasPermissions.value)
                    {
                        bleScanner.startScan(15)
                    }
                    val message = p1.getStringExtra("StopMessage")
                    Toast.makeText(this@MainActivity,
                        message ?: "Connection Stopped", Toast.LENGTH_LONG).show()
                }
                else
                {
                    val connected = p1.getBooleanExtra("cardConnection", false)
                    cardConnected.postValue(connected)
                    if (hasPermissions.value && connected)
                        bleScanner.stopScanning()

                }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        val adapter = getSystemService(BluetoothManager::class.java).adapter
        bleScanner = BLEScanner(this, adapter, deviceAddress)

        super.onCreate(savedInstanceState)
        setContent {
            val connectedState by cardConnected.observeAsState()
            val cardAddress by deviceAddress.observeAsState()
            EinkerTheme {
                val perms by remember { hasPermissions }
                val accessService by remember { accessibilityRunning }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                        .padding(20.dp, top = 40.dp)
                        .fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (cardAddress == null && connectedState == false)
                            ShowNoCardStatus()
                        else if (connectedState == false)
                            ShowCardFoundStatus()
                        else
                            ShowCardConnectedStatus()

                        Settings(cardConnected, deviceAddress, ::toggleService)
                    }
                    if (!perms) NoPermissionsCard()
                    else if (!accessService) ServiceNotRunning()
                }
            }
        }

        registerReceiver(receiver,
            IntentFilter(EinkCardForegroundService.ACTION_BROADCAST_TO_ACTIVITY),
            RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        if (hasPermissions.value)
            bleScanner.stopScanning()
    }

    val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    @Composable
    private fun NoPermissionsCard()
    {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            onClick = {
                permissionRequest.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
            }
        ) {
            Column(modifier = Modifier.padding(15.dp)) {
                Text("Required Permissions missing!",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.Yellow
                )

                Text("This app requires permissions to function. Please click here to enable them in the settings" +
                        "(Hint: Appear on top option also needs to be enabled)")
            }
        }
    }

    @Composable
    private fun ServiceNotRunning()
    {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
        ) {
            Column(modifier = Modifier.padding(15.dp)) {
                Text("Accessibility Not Running!",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.Yellow
                )

                Text("This app requires functions from android's accessibility." +
                        " Please click here, and navigate to 'Installed apps' to start it")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasPermissions.value = checkPermissions()
        accessibilityRunning.value = checkAccessibilityServiceRunning()
        if (hasPermissions.value)
            bleScanner.startScan(15)
    }

    private fun toggleService(useGrayscale: Boolean, useDither: Boolean,
                              invertColors: Boolean, refreshRate: Int,
                              btnFunction: ButtonFunction)
    {
        val service = Intent(this, EinkCardForegroundService::class.java)
        service.action = if (cardConnected.value == true) EinkCardForegroundService.ACTION_STOP_SERVICE
        else EinkCardForegroundService.ACTION_START_SERVICE
        service.putExtra("deviceAddress", deviceAddress.value)
        service.putExtra("useGrayscale", useGrayscale)
        service.putExtra("useDither", useDither)
        service.putExtra("invertColors", invertColors)
        service.putExtra("btnFunction", btnFunction)
        service.putExtra("fullRefreshRate", refreshRate)
        this.startForegroundService(service)
    }

    private fun checkAccessibilityServiceRunning(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).forEach {
            if (it.id.split('/')[1] == ".EinkCardForegroundService")
                return true
        }
        return false
    }

    private fun checkPermissions(): Boolean {
        //bluetooth
        if (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) != PackageManager.PERMISSION_GRANTED)
            return false

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED)
            return false

        //notifications
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED)
            return false

        //draw overlay
        if (!Settings.canDrawOverlays(this))
            return false

        return true
    }
}

