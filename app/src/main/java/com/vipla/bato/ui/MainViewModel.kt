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
    private val repo = StenoRepository(AppDatabase.get(app).stenoDao())
    private val prefs = app.getSharedPreferences("bato_provider", Context.MODE_PRIVATE)

    val events = repo.events.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val cockpitLog = repo.cockpitLog.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val states = repo.controlStates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _lastAssistant = MutableStateFlow<String?>(null)
    val lastAssistant = _lastAssistant.asStateFlow()
    private val _selfTestSummary = MutableStateFlow("Not run in this installation")
    val selfTestSummary = _selfTestSummary.asStateFlow()
    private val _searchResults = MutableStateFlow<List<StenoEvent>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    fun endpoint(): String = prefs.getString("endpoint", "").orEmpty()
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

    fun testOne(spec: ControlSpec) = viewModelScope.launch {
        val pass = LocalGuardEngine.evaluate(spec, LocalGuardEngine.positivePayload(spec))
        val forced = LocalGuardEngine.evaluate(spec, LocalGuardEngine.forcedBlockPayload(spec))
        persistResult(SelfTestResult(spec, pass, forced))
    }

    fun testAll() = viewModelScope.launch {
        var verified = 0
        CockpitCatalog.controls.forEach { spec ->
            val result = SelfTestResult(
                spec,
                LocalGuardEngine.evaluate(spec, LocalGuardEngine.positivePayload(spec)),
                LocalGuardEngine.evaluate(spec, LocalGuardEngine.forcedBlockPayload(spec))
            )
            if (result.verified) verified++
            persistResult(result)
        }
        _selfTestSummary.value = "$verified/51 local PASS + forced BLOCK; external effects remain unproven"
        repo.log(null, "SELF_TEST_51", if (verified == 51) "LOCAL_PASS" else "LOCAL_FAIL", _selfTestSummary.value)
    }

    private suspend fun persistResult(result: SelfTestResult) {
        val localOk = result.pass.allowed
        val blockOk = !result.forcedBlock.allowed
        val effectProven = states.value.firstOrNull { it.code == result.spec.code }?.effectProven ?: false
        val light = if (!localOk || !blockOk) "DARK" else if (effectProven) "GREEN" else "AMBER"
        repo.saveState(ControlState(result.spec.code, localOk, blockOk, effectProven, light, System.currentTimeMillis()))
        repo.log(result.spec.code, "LOCAL_SELF_TEST", if (localOk && blockOk) "PASS+BLOCK" else "FAIL", "${result.pass.evidence}; ${result.forcedBlock.evidence}")
    }

    fun search(query: String) {
        if (query.isBlank()) { _searchResults.value = emptyList(); return }
        viewModelScope.launch { repo.search(query).first().also { _searchResults.value = it } }
    }
}
