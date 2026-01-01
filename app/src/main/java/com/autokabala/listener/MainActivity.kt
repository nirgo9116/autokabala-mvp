package com.autokabala.listener

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.autokabala.listener.ui.theme.AutoKabalaListenerTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("AutoKabalaNL", "MAIN ACTIVITY CREATED")

        enableEdgeToEdge()
        setContent {
            AutoKabalaListenerTheme {

                var isListenerActive by remember { mutableStateOf(true) }

                fun startListener() {
                    isListenerActive = true
                }

                fun stopListener() {
                    isListenerActive = false
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        Greeting(name = "AutoKabala")

                        // כותרת

                        Text(
                                text = "Listener Control",
                        style = MaterialTheme.typography.headlineSmall
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // סטטוס

                        Text(

                            text = "Listener status: " + if (isListenerActive) {
                                "Active"
                            } else {
                                "Inactive"
                            }
                        )

                        // כפתור

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(onClick = { isListenerActive = !isListenerActive }) {
                            Text(if (isListenerActive) "Disable listener" else "Enable listener")
                        }


                    }


                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)


}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AutoKabalaListenerTheme {
        Greeting("Android")
    }
}
