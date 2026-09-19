package com.vipla.bato.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vipla.bato.data.AppDatabase
import com.vipla.bato.data.StenoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = StenoRepository(AppDatabase.get(app).stenoDao())

    val events = repo.events.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    fun add(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { repo.append(text.trim()) }
    }
}
