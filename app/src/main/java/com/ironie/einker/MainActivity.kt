package com.ironie.einker

import android.Manifest
import android.R
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
    //update data here to display card status
    val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            if (p1 == null)
                return
            if (p1.action == HandleScreenForegroundService.ACTION_BROADCAST_TO_ACTIVITY)
                if (p1.getBooleanExtra("StopService", false))
                {
                    cardConnected.postValue(false)
                    val message = p1.getStringExtra("StopMessage")
                    Toast.makeText(this@MainActivity,
                        message ?: "Connection Stopped", Toast.LENGTH_LONG).show()
                }
                else
                {
                    cardConnected.value = p1.getBooleanExtra("Connected", false)
                }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContent {
            EinkerTheme {
                val perms by remember { hasPermissions }

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
                        if (cardConnected.value == false)
                            ShowNoCardStatus()
                        else
                            ShowCardStatus()

                        Settings(cardConnected, ::toggleService)
                    }
                    if (!perms) NoPermissionsCard()
                }
            }
        }

        registerReceiver(receiver,
            IntentFilter(HandleScreenForegroundService.ACTION_BROADCAST_TO_ACTIVITY),
            RECEIVER_NOT_EXPORTED)
    }

    override fun onPostResume() {
        super.onPostResume()
        hasPermissions.value = checkPermissions()
    }


    @Composable
    fun ShowNoCardStatus()
    {
        Column(
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            Text(
                "Card Not Connected",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 30.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = Color.Red
            )

            Text("Select the correct device, give correct permissions and start the connection",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }

    @Composable
    fun ShowCardStatus()
    {
        Column(
            modifier = Modifier.padding(bottom = 40.dp)
        ) {
            Text(
                "Card Detected",
                modifier = Modifier
                    .fillMaxWidth(),
                fontSize = 30.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = Color.Green
            )

            Text(
                "MCU connected",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }


    @Composable
    private fun NoPermissionsCard()
    {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            onClick = {  }
        ) {
            Column(modifier = Modifier.padding(15.dp)) {
                Text("Required Permissions missing!",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.Yellow
                )

                Text("This app requires permissions to function. Please click here to enable them in the settings")
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    private fun toggleService(useGrayscale: Boolean, refreshRate: Int, btnFunction: ButtonFunction): Unit
    {
        val service = Intent(this, HandleScreenForegroundService::class.java)
        service.action = if (cardConnected.value == true) HandleScreenForegroundService.ACTION_STOP_SERVICE
        else HandleScreenForegroundService.ACTION_START_SERVICE
        service.putExtra("useGrayscale", useGrayscale)
        service.putExtra("btnFunction", btnFunction)
        service.putExtra("fullRefreshRate", refreshRate)
        this.startForegroundService(service)
    }

    private fun checkPermissions(): Boolean {
        //bluetooth
        if (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
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

