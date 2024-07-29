package com.example.multifitnessperipheral

import MultiFitnessPeripheral
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.example.multifitnessperipheral.ui.theme.MultiFitnessPeripheralTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var multiFitnessPeripheral: MultiFitnessPeripheral

    private var updateJob: Job? = null

    private fun startUpdates() {
        updateJob = lifecycleScope.launch {
            while (true) {
                multiFitnessPeripheral.updateIndoorBikeData(25.5f, 80, 150)
                multiFitnessPeripheral.updateHeartRate(130)
                multiFitnessPeripheral.updateCyclingPower(150)
                delay(1000) // Wait for 1 second before the next update
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        multiFitnessPeripheral = MultiFitnessPeripheral(this)

        lifecycleScope.launch {
            if (multiFitnessPeripheral.setupGattServer()) {
                multiFitnessPeripheral.startAdvertising()
                startUpdates()
            } else {
                Toast.makeText(this@MainActivity, "Failed to setup GATT server", Toast.LENGTH_LONG).show()
            }
        }

        enableEdgeToEdge()
        setContent {
            MultiFitnessPeripheralTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MultiFitnessPeripheralTheme {
        Greeting("Android")
    }
}