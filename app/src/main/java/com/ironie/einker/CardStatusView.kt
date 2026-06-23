package com.ironie.einker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ShowNoCardStatus()
{
    Column(
        modifier = Modifier.padding(bottom = 10.dp)
    ) {
        Text(
            "Card Not Found",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 30.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = Color.Red
        )

        Text("Power on the device with either wireless power, or via USB-C",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

@Composable
fun ShowCardFoundStatus()
{
    Column(
        modifier = Modifier.padding(bottom = 10.dp)
    ) {
        Text(
            "Card Found",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 30.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = Color(169, 158, 0)
        )

        Text("Start the connection by clicking the button below!",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

@Composable
fun ShowCardConnectedStatus()
{
    Column(
        modifier = Modifier.padding(bottom = 10.dp)
    ) {
        Text(
            "Card Connected",
            modifier = Modifier
                .fillMaxWidth(),
            fontSize = 30.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = Color.Green
        )

        Text(
            "click on the buttons on the device.\n Use the overlay on top to enable/disable screen touches",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}