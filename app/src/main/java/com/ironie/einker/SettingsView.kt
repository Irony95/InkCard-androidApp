package com.ironie.einker

import android.R
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.RadioButton
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
import androidx.lifecycle.MutableLiveData
import kotlin.experimental.and

@Composable
fun Settings(
    connectedLive: MutableLiveData<Boolean>,
    cardData: MutableLiveData<ByteArray?>,
    toggleService: (Boolean, ButtonFunction) -> Unit)
{
    var useGrayscale by rememberSaveable { mutableStateOf(false) }
    val connected: Boolean? by connectedLive.observeAsState()
    val cardDataState by cardData.observeAsState()

    val buttonSettings = ButtonFunction.entries
    var buttonAction by remember { mutableStateOf(buttonSettings[0]) }

    Column(
        modifier = Modifier
            .padding(20.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally

    ) {
        if (cardDataState == null)
            ShowNoCardStatus()
        else
            ShowCardStatus(cardDataState!!)

        GrayscaleSetting(useGrayscale, onGrayscaleChange = { useGrayscale = it})
        PhysicalButtonSettings(buttonSettings, buttonAction, onSelectChange = { buttonAction = it })

        Button(
            modifier = Modifier.padding(top=100.dp),
            enabled = true,
            onClick = { toggleService(useGrayscale, buttonAction) }
        ) {
            Text("${if(connected == true) "Stop" else "Start"} Connection")
        }
    }
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
fun ShowCardStatus(cardData: ByteArray)
{
    val voltage = (cardData[0] and 0b1111) + cardData[1]/100f
    val btn1 = (cardData[0].toInt() and 0b10000) != 0
    val btn2 = (cardData[0].toInt() and 0b100000) != 0

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
            "MCU: ${voltage}V",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        )
        {
            Text("Button 1: ${if (btn1) "Down" else "Up"}")
            Text("Button 2: ${if (btn2) "Down" else "Up"}")
        }
    }
}

@Composable
fun BluetoothDeviceSelect()
{

}

@Composable
fun GrayscaleSetting(useGrayscale: Boolean, onGrayscaleChange: (Boolean) -> Unit)
{
    Row(
        modifier = Modifier.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
            modifier = Modifier.padding(horizontal = 5.dp),
            checked = useGrayscale,
            onCheckedChange = onGrayscaleChange
        )

        Text("Enable Grayscale coloring")
    }
    Text(
        "Images will display in 4 color grayscale, however refreshes will take longer",
        modifier = Modifier
        .padding(horizontal = 10.dp)
        .padding(bottom = 40.dp),
        color=Color.DarkGray,
        textAlign = TextAlign.Center
    )
}

@Composable
fun PhysicalButtonSettings(
    settings: List<ButtonFunction>,
    selected: ButtonFunction,
    onSelectChange: (ButtonFunction) -> Unit)
{
    Text("Physical Button Settings",
        fontSize = 20.sp,
        textAlign = TextAlign.Center
        )

    Column(modifier = Modifier.padding(vertical = 1.dp)) {
        settings.forEach { action ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .selectable(
                        selected = (action == selected),
                        onClick = { onSelectChange(action) }
                    )
                    .padding(horizontal = 16.dp)
            ) {
                RadioButton(
                    selected = (action == selected),
                    onClick = { onSelectChange(action) },
                )
                Text(text = action.desc, textAlign = TextAlign.Center, modifier = Modifier.fillMaxHeight())
            }
        }
    }
}