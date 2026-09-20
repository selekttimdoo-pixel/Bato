package com.vipla.bato.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vipla.bato.ai.*
import com.vipla.bato.cockpit.*
import com.vipla.bato.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    companion object { const val DEFAULT_ENDPOINT = "https://bato-sigma.vercel.app/api/bato" }
    private val repo = StenoRepository(AppDatabase.get(app).stenoDao())
    private val prefs = app.getSharedPreferences("bato_provider", Context.MODE_PRIVATE)

    val events = repo.events.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val cockpitLog = repo.cockpitLog.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val states = repo.controlStates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val runtimeValues = repo.runtimeValues.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val knowledge = repo.knowledge.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _lastAssistant = MutableStateFlow<String?>(null)
    val lastAssistant = _lastAssistant.asStateFlow()
    private val _selfTestSummary = MutableStateFlow("Not run in this installation")
    val selfTestSummary = _selfTestSummary.asStateFlow()
    private val _searchResults = MutableStateFlow<List<StenoEvent>>(emptyList())
    val searchResults = _searchResults.asStateFlow()
    private val _knowledgeSearchResults = MutableStateFlow<List<KnowledgeObject>>(emptyList())
    val knowledgeSearchResults = _knowledgeSearchResults.asStateFlow()

    init {
        viewModelScope.launch {
            repo.putKnowledge("steno-wal", "STENO WAL", "Durable local raw event log committed before every remote provider request")
            repo.putKnowledge("fast-graph", "FAST GRAPH", "Entity/relation retrieval hook consumes persisted STENO context; live remote graph remains unproven")
            repo.putKnowledge("riznica", "Riznica", "Local Room knowledge-object vault used by Vault and Search")
            repo.putKnowledge("serbian-lexicon", "Active Serbian Lexicon", "Serbian recognition locale and lexical provider hook; external corpus remains unproven")
            repo.putKnowledge("serbian-grammar", "Serbian Grammar Graph", "Grammar-layer provider hook with Serbian speech locale; external graph remains unproven")
        }
    }

    fun endpoint(): String = prefs.getString("endpoint", DEFAULT_ENDPOINT).orEmpty().ifBlank { DEFAULT_ENDPOINT }
    fun saveEndpoint(value: String) { prefs.edit().putString("endpoint", value.trim()).apply() }

    fun sendMessage(text: String, source: String = "TEXT") {
        if (text.isBlank() || _busy.value) return
        viewModelScope.launch {
            _busy.value = true
            repo.append("USER", text.trim(), "STENO_FIRST:$source")
            repo.log(null, "CONVERSATION", "USER_STORED", "Raw user event persisted before provider processing")
            val context = repo.context()
            when (val result = HttpProviderBridge(endpoint()).respond(text.trim(), context)) {
                is ProviderResult.Response -> {
                    repo.append("ASSISTANT", result.text, "PROVIDER_RESPONSE")
                    repo.log(null, "PROVIDER", "PASS", "Assistant response persisted to STENO")
                    _lastAssistant.value = result.text
                }
                is ProviderResult.Blocked -> {
                    repo.append("SYSTEM", result.reason, "PROVIDER_BLOCKED")
                    repo.log(null, "PROVIDER", "BLOCK", result.reason)
                }
            }
            _busy.value = false
        }
    }

    fun executeControl(spec: ControlSpec) = viewModelScope.launch { executeAndPersist(spec, false) }

    fun testOne(spec: ControlSpec) = viewModelScope.launch {
        val actual = executeAndPersist(spec, false)
        val forced = CockpitRuntime.execute(spec, repo.runtimeSnapshot(), true)
        repo.log(spec.code, "FORCED_BLOCK_TEST", forced.status, forced.evidence)
        val state = states.value.firstOrNull { it.code == spec.code }
        repo.saveState((state ?: ControlState(spec.code, false, false, false, "DARK", System.currentTimeMillis())).copy(
            localPass = actual.status != "FAIL", forcedBlockPass = forced.status == "BLOCKED",
            light = if (actual.status == "PASS") "GREEN" else if (actual.status == "BLOCKED") "AMBER" else "DARK",
            updatedAt = System.currentTimeMillis(), handler = actual.handler,
            observableResult = actual.observable, evidence = actual.evidence
        ))
    }

    fun testAll() = viewModelScope.launch {
        var pass = 0
        var blocked = 0
        var failed = 0
        CockpitCatalog.controls.forEach { spec ->
            val actual = executeAndPersist(spec, false)
            val forced = CockpitRuntime.execute(spec, repo.runtimeSnapshot(), true)
            repo.log(spec.code, "FORCED_BLOCK_TEST", forced.status, forced.evidence)
            when (actual.status) { "PASS" -> pass++; "BLOCKED" -> blocked++; else -> failed++ }
            val prior = states.value.firstOrNull { it.code == spec.code }
            repo.saveState((prior ?: ControlState(spec.code, false, false, false, "DARK", System.currentTimeMillis())).copy(
                localPass = actual.status != "FAIL", forcedBlockPass = forced.status == "BLOCKED",
                effectProven = actual.status == "PASS", light = when (actual.status) { "PASS" -> "GREEN"; "BLOCKED" -> "AMBER"; else -> "DARK" },
                updatedAt = System.currentTimeMillis(), handler = actual.handler, observableResult = actual.observable, evidence = actual.evidence
            ))
        }
        _selfTestSummary.value = "Behavioral handlers: PASS=$pass BLOCKED=$blocked FAIL=$failed; forced BLOCK called through same entry points"
        repo.log(null, "BEHAVIORAL_TEST_51", if (failed == 0) "COMPLETED" else "FAIL", _selfTestSummary.value)
    }

    private suspend fun executeAndPersist(spec: ControlSpec, forceBlock: Boolean): ActionResult {
        val result = CockpitRuntime.execute(spec, repo.runtimeSnapshot(), forceBlock)
        result.updates.forEach { (key, value) -> repo.putRuntime(key, value) }
        repo.log(spec.code, "CONTROL_HANDLER", result.status, "${result.handler}: ${result.observable}; ${result.evidence}")
        val light = when (result.status) { "PASS" -> "GREEN"; "BLOCKED" -> "AMBER"; else -> "DARK" }
        repo.saveState(ControlState(spec.code, result.status != "FAIL", false, result.status == "PASS", light,
            System.currentTimeMillis(), result.handler, result.observable, result.evidence))
        return result
    }

    fun search(query: String) {
        if (query.isBlank()) { _searchResults.value = emptyList(); return }
        viewModelScope.launch {
            _searchResults.value = repo.search(query).first()
            _knowledgeSearchResults.value = repo.searchKnowledge(query)
            repo.log(null, "SEARCH", "PASS", "Query '$query' returned ${_searchResults.value.size} STENO + ${_knowledgeSearchResults.value.size} knowledge results")
        }
    }

    fun recordMicState(state: String, evidence: String) = viewModelScope.launch { repo.log(null, "MIC", state, evidence) }
}
