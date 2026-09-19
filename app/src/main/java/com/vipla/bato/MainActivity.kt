package com.vipla.bato

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vipla.bato.cockpit.*
import com.vipla.bato.data.*
import com.vipla.bato.ui.MainViewModel
import java.text.DateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF62D9A5))) { BatoApp() } }
    }
}

enum class BatoTab(val title: String) {
    CONVERSATION("BATO"), COCKPIT("C01–C51"), TIMELINE("STENO"), VAULT("Riznica"), SEARCH("Search"), DIAGNOSTICS("Diagnostics")
}

@Composable
fun BatoApp(vm: MainViewModel = viewModel()) {
    var tab by remember { mutableStateOf(BatoTab.CONVERSATION) }
    val events by vm.events.collectAsStateWithLifecycle()
    val states by vm.states.collectAsStateWithLifecycle()
    val log by vm.cockpitLog.collectAsStateWithLifecycle()
    val lastAssistant by vm.lastAssistant.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var ttsEnabled by remember { mutableStateOf(true) }
    val tts = remember { TextToSpeech(context) { } }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    LaunchedEffect(lastAssistant) {
        lastAssistant?.takeIf { ttsEnabled }?.let { tts.speak(it, TextToSpeech.QUEUE_FLUSH, null, "bato-response") }
    }

    Scaffold(
        topBar = { Surface(shadowElevation = 4.dp) { Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("VIPLA / BATO COCKPIT", fontWeight = FontWeight.Bold)
            Text("Android v0.2 · local-first · external effects are never inferred", style = MaterialTheme.typography.bodySmall)
        } } },
        bottomBar = { NavigationBar {
            BatoTab.entries.forEach { item -> NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Text(item.title.take(2)) }, label = { Text(item.title, maxLines = 1) }) }
        } }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                BatoTab.CONVERSATION -> ConversationScreen(vm, events, ttsEnabled) { ttsEnabled = it }
                BatoTab.COCKPIT -> CockpitScreen(vm, states)
                BatoTab.TIMELINE -> TimelineScreen(events)
                BatoTab.VAULT -> VaultScreen()
                BatoTab.SEARCH -> SearchScreen(vm)
                BatoTab.DIAGNOSTICS -> DiagnosticsScreen(vm, states, log)
            }
        }
    }
}

@Composable
private fun ConversationScreen(vm: MainViewModel, events: List<StenoEvent>, ttsEnabled: Boolean, setTts: (Boolean) -> Unit) {
    var input by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf(vm.endpoint()) }
    var listening by remember { mutableStateOf(false) }
    var speechError by remember { mutableStateOf<String?>(null) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val recognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    DisposableEffect(recognizer) { onDispose { recognizer.destroy() } }

    val startListening = {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true; speechError = null }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) { listening = false; speechError = "Microphone/transcription error $error" }
            override fun onResults(results: Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) vm.sendMessage(text, "IN_APP_MICROPHONE")
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sr-RS")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        })
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else speechError = "RECORD_AUDIO permission blocked"
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("BATO conversation", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Spoken response / TTS", Modifier.weight(1f)); Switch(checked = ttsEnabled, onCheckedChange = setTts)
        }
        OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Provider-neutral backend endpoint") }, supportingText = { Text("Blank = correctly BLOCKED; no fake AI response") })
        Button(onClick = { vm.saveEndpoint(endpoint) }) { Text("Save bridge endpoint") }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(events.reversed().takeLast(100), key = { it.id }) { event ->
                Surface(color = when (event.role) { "USER" -> Color(0xFF173B33); "ASSISTANT" -> Color(0xFF20334A); else -> Color(0xFF4A321C) }, shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) { Text(event.role, fontWeight = FontWeight.Bold); Text(event.rawText); Text(event.providerState, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        speechError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), label = { Text("Message") })
            Button(onClick = { vm.sendMessage(input); input = "" }, enabled = !busy, modifier = Modifier.padding(start = 6.dp)) { Text("Send") }
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening()
                else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }, enabled = !listening && !busy, modifier = Modifier.padding(start = 6.dp)) { Text(if (listening) "…" else "🎙 MIC") }
        }
    }
}

@Composable
private fun CockpitScreen(vm: MainViewModel, states: List<ControlState>) {
    var selected by remember { mutableStateOf<ControlSpec?>(null) }
    val map = states.associateBy { it.code }
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Cockpit C01–C51", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); Button(onClick = vm::testAll) { Text("Testiraj 51 kontrolu") } }
        Text("GREEN = live effect proven · AMBER = local wired/tested, external effect unproven · DARK = local failure", style = MaterialTheme.typography.bodySmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(CockpitCatalog.controls, key = { it.code }) { spec ->
                val state = map[spec.code]
                val light = state?.light ?: "AMBER"
                Surface(shape = RoundedCornerShape(8.dp), color = when (light) { "GREEN" -> Color(0xFF145A32); "DARK" -> Color(0xFF242424); else -> Color(0xFF6A4D13) }, modifier = Modifier.fillMaxWidth().clickable { selected = spec }) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Text(spec.code, fontWeight = FontWeight.Bold); Text(spec.name, Modifier.padding(start = 10.dp).weight(1f)); Text(light) }
                }
            }
        }
    }
    selected?.let { spec -> ControlDetail(spec, map[spec.code], onDismiss = { selected = null }, onTest = { vm.testOne(spec) }) }
}

@Composable
private fun ControlDetail(spec: ControlSpec, state: ControlState?, onDismiss: () -> Unit, onTest: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { Button(onClick = onTest) { Text("Run PASS + FORCE-BLOCK") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("${spec.code} · ${spec.name}") }, text = { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("CODED: YES")
            Text("INTEGRATED: YES — Android LocalGuardEngine + persisted state/event log")
            Text("FORCE-FAIL: ${if (state?.forcedBlockPass == true) "PASS" else "NOT RUN ON THIS INSTALL"}")
            Text("EFFECT-PROVEN: ${if (state?.effectProven == true) "YES" else "NO"}")
            Text("LIGHT: ${state?.light ?: "AMBER"}")
            Text("Evidence: ${spec.evidence}")
            Text("Next physical/live test: ${spec.nextPhysicalTest}")
        } })
}

@Composable
private fun TimelineScreen(events: List<StenoEvent>) = Column(Modifier.fillMaxSize().padding(10.dp)) {
    Text("Timeline / durable local STENO", style = MaterialTheme.typography.titleLarge)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) { items(events, key = { it.id }) { e ->
        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF20252B)) { Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Text("${e.role} · ${DateFormat.getDateTimeInstance().format(Date(e.startTs))}", fontWeight = FontWeight.Bold); Text(e.rawText); Text("session ${e.sessionId.take(8)} · segment ${e.segmentId.take(8)} · ${e.providerState}", style = MaterialTheme.typography.labelSmall)
        } }
    } }
}

@Composable
private fun VaultScreen() {
    val hooks = listOf(
        "STENO WAL" to "ACTIVE local Room WAL; raw events committed before remote call",
        "FAST GRAPH" to "HOOK READY; provider/context adapter receives graph capability flag",
        "Riznica knowledge vault" to "HOOK READY; local-first bridge schema present",
        "Active Serbian Lexicon" to "HOOK READY; semantic layer declared, corpus not bundled",
        "Serbian Grammar Graph" to "HOOK READY; grammar layer declared, corpus not bundled",
        "Temporal/session/segment metadata" to "ACTIVE; every STENO row carries timestamps, session and segment IDs"
    )
    Column(Modifier.fillMaxSize().padding(10.dp)) { Text("Vault / Riznica architecture", style = MaterialTheme.typography.titleLarge); LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) { items(hooks) { (name, status) -> Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF252B36)) { Column(Modifier.fillMaxWidth().padding(10.dp)) { Text(name, fontWeight = FontWeight.Bold); Text(status) } } } } }
}

@Composable
private fun SearchScreen(vm: MainViewModel) {
    var query by remember { mutableStateOf("") }
    val results by vm.searchResults.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(10.dp)) { Text("Search local STENO", style = MaterialTheme.typography.titleLarge); Row { OutlinedTextField(query, { query = it }, Modifier.weight(1f), label = { Text("Exact or partial text") }); Button({ vm.search(query) }, Modifier.padding(start = 6.dp)) { Text("Search") } }; LazyColumn { items(results, key = { it.id }) { Text("${it.role}: ${it.rawText}", Modifier.fillMaxWidth().padding(8.dp)) } } }
}

@Composable
private fun DiagnosticsScreen(vm: MainViewModel, states: List<ControlState>, log: List<CockpitEvent>) {
    val summary by vm.selfTestSummary.collectAsStateWithLifecycle()
    val green = states.count { it.light == "GREEN" }; val amber = (51 - states.count { it.light == "DARK" } - green).coerceAtLeast(0); val dark = states.count { it.light == "DARK" }
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Text("Diagnostics / self-test / event log", style = MaterialTheme.typography.titleLarge)
        Text("GREEN=$green · AMBER=$amber · DARK=$dark")
        Text(summary)
        Button(onClick = vm::testAll, modifier = Modifier.padding(vertical = 8.dp)) { Text("Testiraj 51 kontrolu: PASS + forced BLOCK") }
        Text("Local tests never prove external/Windows/live effects.", color = Color(0xFFFFC857), fontWeight = FontWeight.Bold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 8.dp)) { items(log, key = { it.id }) { e -> Surface(color = Color(0xFF20252B), shape = RoundedCornerShape(7.dp)) { Column(Modifier.fillMaxWidth().padding(8.dp)) { Text("${e.controlCode ?: "SYS"} · ${e.eventType} · ${e.result}", fontWeight = FontWeight.Bold); Text(e.evidence); Text(DateFormat.getDateTimeInstance().format(Date(e.timestamp)), style = MaterialTheme.typography.labelSmall) } } } }
    }
}
