package psiolent.caliper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import psiolent.caliper.agent.AgentRunner
import psiolent.caliper.data.CaliperRepository
import psiolent.caliper.data.MessageEntity

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repo: CaliperRepository,
    private val agent: AgentRunner,
) : ViewModel() {

    private val session = MutableStateFlow<String?>(null)

    val messages: StateFlow<List<MessageEntity>> = session
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeMessages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    init {
        viewModelScope.launch { session.value = agent.newSession() }
    }

    fun send(text: String, imageDataUrls: List<String>) {
        val id = session.value ?: return
        if (text.isBlank() && imageDataUrls.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            _status.value = "Thinking\u2026"
            try {
                agent.send(id, text.ifBlank { "What is this?" }, imageDataUrls) { _status.value = it }
            } finally {
                _busy.value = false
                _status.value = null
            }
        }
    }

    fun startNewSession() {
        viewModelScope.launch { session.value = agent.newSession() }
    }
}
