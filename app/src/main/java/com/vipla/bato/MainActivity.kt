package com.vipla.bato

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import com.vipla.bato.voice.RemoteBatoVoiceEngine
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
    val runtime by vm.runtimeValues.collectAsStateWithLifecycle()
    val lastAssistant by vm.lastAssistant.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var ttsEnabled by remember { mutableStateOf(true) }
    val voiceEngine = remember { RemoteBatoVoiceEngine(context) }
    DisposableEffect(Unit) { onDispose { voiceEngine.shutdown() } }
    LaunchedEffect(lastAssistant) {
        lastAssistant?.takeIf { ttsEnabled }?.let { text -> voiceEngine.speak(text) { r -> vm.recordVoiceState(r.status, "VOICE_PROVIDER=${r.provider}|VOICE_MODEL=${r.model}|VOICE_ID=${r.voiceId}|VOICE_LOCALE=${r.locale}|VOICE_PROVENANCE=${r.provenance}|VOICE_FALLBACK=${r.fallback}|TTS_HTTP_STATUS=${r.httpStatus}|${r.evidence}|NORMALIZED=${r.normalizedText}") } }
    }

    Scaffold(
        topBar = { Surface(shadowElevation = 4.dp) { Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("VIPLA / BATO COCKPIT", fontWeight = FontWeight.Bold)
            Text("Android v0.4.1 · remote BATO voice · external effects are never inferred", style = MaterialTheme.typography.bodySmall)
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
                BatoTab.VAULT -> VaultScreen(vm)
                BatoTab.SEARCH -> SearchScreen(vm)
                BatoTab.DIAGNOSTICS -> DiagnosticsScreen(vm, states, log, runtime)
            }
        }
    }
}

@Composable
private fun ConversationScreen(vm: MainViewModel, events: List<StenoEvent>, ttsEnabled: Boolean, setTts: (Boolean) -> Unit) {
    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var speechError by remember { mutableStateOf<String?>(null) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val recognitionAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val recognizer = remember(recognitionAvailable) { if (recognitionAvailable) SpeechRecognizer.createSpeechRecognizer(context) else null }
    DisposableEffect(recognizer) { onDispose { recognizer?.destroy() } }

    val startListening = startListening@{
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listening = false
            speechError = "Microphone permission is not granted. Tap MIC to request it."
            vm.recordMicState("PERMISSION_REQUIRED", speechError!!)
            return@startListening
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context) || recognizer == null) {
            listening = false
            speechError = "BLOCKED: no Android speech recognition service is installed"
            vm.recordMicState("BLOCKED", speechError!!)
            return@startListening
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true; speechError = null; vm.recordMicState("RECORDING", "Android SpeechRecognizer opened the in-app microphone") }
            override fun onBeginningOfSpeech() { vm.recordMicState("AUDIO_DETECTED", "SpeechRecognizer reported beginning of speech") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false; vm.recordMicState("CAPTURE_COMPLETE", "Audio capture ended; waiting for transcription") }
            override fun onError(error: Int) {
                recognizer.cancel()
                listening = false
                val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                speechError = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Nisam razumeo, pokušaj ponovo."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard — tap MIC and try again."
                    SpeechRecognizer.ERROR_AUDIO -> "Audio capture error — check the microphone and try again."
                    SpeechRecognizer.ERROR_CLIENT -> "Speech recognition session ended — tap MIC to retry."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing — tap MIC to grant it."
                    SpeechRecognizer.ERROR_NETWORK -> "Speech recognition network error — check the connection and retry."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network timeout — try again."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer was busy — it is ready to retry."
                    SpeechRecognizer.ERROR_SERVER -> "Speech recognition service error — try again."
                    else -> "Speech recognition failed (code $error) — try again."
                }
                vm.recordMicState(if (recoverable) "READY_RETRY" else "FAIL", "SpeechRecognizer code=$error; ${speechError!!}")
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) { vm.recordMicState("TRANSCRIBED", text); vm.sendMessage(text, "IN_APP_MICROPHONE") }
                else { speechError = "Nisam razumeo, pokušaj ponovo."; vm.recordMicState("READY_RETRY", speechError!!) }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.cancel()
        speechError = null
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sr-RS")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        })
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { vm.recordMicState("PERMISSION_GRANTED", "RECORD_AUDIO granted at runtime"); startListening() }
        else { speechError = "RECORD_AUDIO permission denied"; vm.recordMicState("BLOCKED", speechError!!) }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("BATO conversation", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BATO voice · Serbian normalized", Modifier.weight(1f)); Switch(checked = ttsEnabled, onCheckedChange = setTts)
        }
        Text("BATO bridge: ${vm.endpoint()}", style = MaterialTheme.typography.labelSmall)
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
                if (listening) { recognizer?.stopListening(); listening = false; vm.recordMicState("STOP_REQUESTED", "User stopped in-app capture") }
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening()
                else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }, enabled = !busy, modifier = Modifier.padding(start = 6.dp)) { Text(if (listening) "■ STOP" else "🎙 MIC") }
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
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Text(spec.code, fontWeight = FontWeight.Bold); Text(spec.name, Modifier.padding(start = 10.dp).weight(1f)); Text(light); Button(onClick = { vm.executeControl(spec) }, modifier = Modifier.padding(start = 6.dp)) { Text("RUN") } }
                }
            }
        }
    }
    selected?.let { spec -> ControlDetail(spec, map[spec.code], onDismiss = { selected = null }, onExecute = { vm.executeControl(spec) }, onTest = { vm.testOne(spec) }) }
}

@Composable
private fun ControlDetail(spec: ControlSpec, state: ControlState?, onDismiss: () -> Unit, onExecute: () -> Unit, onTest: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { Row { Button(onClick = onExecute) { Text("Execute") }; Button(onClick = onTest, modifier = Modifier.padding(start = 6.dp)) { Text("Behavior test") } } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("${spec.code} · ${spec.name}") }, text = { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("CODED: YES")
            Text("INTEGRATED: YES — ${state?.handler ?: "not executed on this install"}")
            Text("FORCE-FAIL: ${if (state?.forcedBlockPass == true) "PASS" else "NOT RUN ON THIS INSTALL"}")
            Text("EFFECT-PROVEN: ${if (state?.effectProven == true) "YES" else "NO"}")
            Text("LIGHT: ${state?.light ?: "AMBER"}")
            Text("Observable result: ${state?.observableResult ?: "not executed"}")
            Text("Evidence: ${state?.evidence ?: spec.evidence}")
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
private fun VaultScreen(vm: MainViewModel) {
    val objects by vm.knowledge.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(10.dp)) { Text("Vault / Riznica · persisted objects", style = MaterialTheme.typography.titleLarge); LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) { items(objects, key = { it.key }) { item -> Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF252B36)) { Column(Modifier.fillMaxWidth().padding(10.dp)) { Text("${item.layer} · ${item.key}", fontWeight = FontWeight.Bold); Text(item.content); Text(DateFormat.getDateTimeInstance().format(Date(item.updatedAt)), style = MaterialTheme.typography.labelSmall) } } } } }
}

@Composable
private fun SearchScreen(vm: MainViewModel) {
    var query by remember { mutableStateOf("") }
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val knowledgeResults by vm.knowledgeSearchResults.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(10.dp)) { Text("Search STENO + Riznica", style = MaterialTheme.typography.titleLarge); Row { OutlinedTextField(query, { query = it }, Modifier.weight(1f), label = { Text("Exact or partial text") }); Button({ vm.search(query) }, Modifier.padding(start = 6.dp)) { Text("Search") } }; LazyColumn { items(knowledgeResults, key = { it.key }) { Text("RIZNICA · ${it.layer} · ${it.key}\n${it.content}\nPROVENANCE=LOCAL_RIZNICA", Modifier.fillMaxWidth().padding(8.dp)) }; items(results, key = { "s-${it.id}" }) { Text("STENO:${it.id} · ${it.role}\n${it.rawText}\nSOURCE=${if(it.providerState.contains("IMPORTED")) "IMPORTED_STENO" else "LOCAL_STENO"} · ${it.providerState}", Modifier.fillMaxWidth().padding(8.dp)) } } }
}

@Composable
private fun DiagnosticsScreen(vm: MainViewModel, states: List<ControlState>, log: List<CockpitEvent>, runtime: List<RuntimeValue>) {
    val summary by vm.selfTestSummary.collectAsStateWithLifecycle()
    var showContext by remember { mutableStateOf(false) }
    val contextBundle = runtime.firstOrNull { it.key == "LAST_CONTEXT_BUNDLE" }?.value
    val green = states.count { it.light == "GREEN" }; val amber = (51 - states.count { it.light == "DARK" } - green).coerceAtLeast(0); val dark = states.count { it.light == "DARK" }
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Text("Diagnostics / self-test / event log", style = MaterialTheme.typography.titleLarge)
        Text("GREEN=$green · AMBER=$amber · DARK=$dark")
        Text(summary)
        Button(onClick = vm::testAll, modifier = Modifier.padding(vertical = 8.dp)) { Text("Testiraj 51 kontrolu: PASS + forced BLOCK") }
        Button(onClick = { showContext = true }, enabled = contextBundle != null) { Text("Inspect exact context bundle") }
        Text("Local tests never prove external/Windows/live effects.", color = Color(0xFFFFC857), fontWeight = FontWeight.Bold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 8.dp)) { items(log, key = { it.id }) { e -> Surface(color = Color(0xFF20252B), shape = RoundedCornerShape(7.dp)) { Column(Modifier.fillMaxWidth().padding(8.dp)) { Text("${e.controlCode ?: "SYS"} · ${e.eventType} · ${e.result}", fontWeight = FontWeight.Bold); Text(e.evidence); Text(DateFormat.getDateTimeInstance().format(Date(e.timestamp)), style = MaterialTheme.typography.labelSmall) } } } }
    }
    if (showContext) AlertDialog(onDismissRequest = { showContext=false }, confirmButton = { TextButton(onClick={showContext=false}){Text("Close")} }, title={Text("LAST_CONTEXT_BUNDLE")}, text={Text(contextBundle ?: "No provider context captured")})
}
