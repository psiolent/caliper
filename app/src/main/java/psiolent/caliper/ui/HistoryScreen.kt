package psiolent.caliper.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import psiolent.caliper.AppContainer
import psiolent.caliper.data.SessionEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Read-only session history. Sessions are a record for the user; the agent never
 * reads them back.
 */
@Composable
fun HistoryScreen(container: AppContainer) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    // Back from a transcript returns to the session list, before the tab-level
    // handler can take it.
    BackHandler(enabled = selectedId != null) { selectedId = null }

    val id = selectedId
    if (id == null) {
        SessionList(container) { selectedId = it }
    } else {
        Transcript(container, id) { selectedId = null }
    }
}

@Composable
private fun SessionList(container: AppContainer, onOpen: (String) -> Unit) {
    val sessions by container.repository.observeSessions().collectAsStateWithLifecycle(initialValue = emptyList())
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (sessions.isEmpty()) {
            item { Text("No conversations yet.", style = MaterialTheme.typography.bodySmall) }
        }
        items(sessions, key = SessionEntity::id) { session ->
            TextButton(onClick = { onOpen(session.id) }) { Text(formatStarted(session.startedAt)) }
        }
    }
}

@Composable
private fun Transcript(container: AppContainer, sessionId: String, onBack: () -> Unit) {
    val messages by container.repository.observeMessages(sessionId).collectAsStateWithLifecycle(initialValue = emptyList())
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) { Text("\u2039 Sessions") }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.id }) { message -> MessageBubble(message) }
        }
    }
}

private val startedFormat = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")

private fun formatStarted(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(startedFormat)
