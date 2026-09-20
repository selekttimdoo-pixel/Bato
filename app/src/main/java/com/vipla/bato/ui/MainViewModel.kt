package com.vipla.bato.ui


import android.app.Application
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
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
