package com.vipla.bato

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vipla.bato.ui.MainViewModel
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val vm: MainViewModel = viewModel()
                    val events by vm.events.collectAsStateWithLifecycle()
                    var input by remember { mutableStateOf("") }

                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("VIPLA BATO — Independent M0", style = MaterialTheme.typography.headlineSmall)
                        Text("Local STENO capture. No AI provider required.")

                        Row(Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("Порука") }
                            )
                            Spacer(Modifier.padding(4.dp))
                            Button(onClick = { vm.add(input); input = "" }) { Text("Упиши") }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text("Timeline", style = MaterialTheme.typography.titleMedium)

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(events, key = { it.id }) { event ->
                                Column {
                                    Text(event.rawText)
                                    Text(
                                        DateFormat.getDateTimeInstance().format(Date(event.startTs)) +
                                            " · segment " + event.segmentId.take(8),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
