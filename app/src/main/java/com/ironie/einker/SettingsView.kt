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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
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
import kotlin.math.round

@Composable
fun Settings(
    connectedLive: MutableLiveData<Boolean>,
    toggleService: (Boolean, Int, ButtonFunction) -> Unit)
{
    val connected: Boolean? by connectedLive.observeAsState()

    var useGrayscale by rememberSaveable { mutableStateOf(false) }
    var refreshRate by rememberSaveable { mutableIntStateOf(4) }

    val buttonSettings = ButtonFunction.entries
    var buttonAction by remember { mutableStateOf(buttonSettings[0]) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally

    ) {
        Text("Settings",
            modifier = Modifier
            .fillMaxWidth()
            .padding(top = 50.dp),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = Color.Magenta
            )
        Column(modifier = Modifier.padding(horizontal = 10.dp)) {
            GrayscaleSetting(useGrayscale, onGrayscaleChange = { useGrayscale = it})

            RefreshSlider(refreshRate, onChange = {refreshRate = round(1+it*7).toInt() })
            PhysicalButtonSettings(buttonSettings, buttonAction, onSelectChange = { buttonAction = it })
        }

        Button(
            modifier = Modifier.padding(top=100.dp),
            enabled = true,
            onClick = { toggleService(useGrayscale, refreshRate, buttonAction) }
        ) {
            Text("${if(connected == true) "Stop" else "Start"} Connection")
        }
    }
}

@Composable
fun GrayscaleSetting(useGrayscale: Boolean, onGrayscaleChange: (Boolean) -> Unit)
{
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Enable Grayscale coloring", fontWeight = FontWeight.Bold)
        Switch(
            modifier = Modifier.padding(horizontal = 5.dp),
            checked = useGrayscale,
            onCheckedChange = onGrayscaleChange
        )
    }
}

@Composable
fun RefreshSlider(refreshRate: Int, onChange: (Float) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Full refresh rate: $refreshRate", fontWeight = FontWeight.Bold)
        Slider(value = ((refreshRate-1)/7.0).toFloat(), onValueChange = onChange)
    }
}

@Composable
fun PhysicalButtonSettings(
    settings: List<ButtonFunction>,
    selected: ButtonFunction,
    onSelectChange: (ButtonFunction) -> Unit)
{
    Text("Physical Button Settings", textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)

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