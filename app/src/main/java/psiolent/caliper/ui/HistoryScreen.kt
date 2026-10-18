package psiolent.caliper.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import psiolent.caliper.AppContainer
import psiolent.caliper.data.SessionSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Read-only session history. Sessions are a record for the user; the agent never reads
 * them back.
 *
 * A session is described by the two ends of its own conversation — what was asked and
 * what was answered — so the list is simply the transcripts, read back, rather than a
 * second summary that could drift from them.
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
    val sessions by container.repository.observeSessionSummaries()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Sessions arrive newest first; the heading changes when the day does.
    val byDay = sessions.groupBy { LocalDate.ofInstant(Instant.ofEpochMilli(it.startedAt), ZoneId.systemDefault()) }
    val today = LocalDate.now()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (sessions.isEmpty()) {
            item { Text("No conversations yet.", style = MaterialTheme.typography.bodyMedium) }
        }
        byDay.forEach { (day, daySessions) ->
            item(key = "day:$day") { DayHeading(dayLabel(day, today)) }
            items(daySessions, key = SessionSummary::sessionId) { session ->
                SessionRow(session) { onOpen(session.sessionId) }
            }
        }
    }
}

@Composable
private fun DayHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
    )
}

/**
 * One session: when it happened, what opened it, and how the agent answered. The reply is
 * cut short because the ask is the point; the whole thing is a tap away.
 */
@Composable
private fun SessionRow(session: SessionSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
    ) {
        Text(
            timeFormat.format(Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault())),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            session.opening?.oneLine() ?: "(no message)",
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        session.closing?.oneLine()?.let { reply ->
            Text(
                reply,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
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

/**
 * Today and Yesterday by name, the weekday for the last week, and a date beyond that.
 * "Last week" would be vaguer than saying which day it was.
 */
private fun dayLabel(day: LocalDate, today: LocalDate): String = when {
    day == today -> "Today"
    day == today.minusDays(1) -> "Yesterday"
    day.isAfter(today.minusDays(7)) -> day.format(DateTimeFormatter.ofPattern("EEEE"))
    else -> day.format(DateTimeFormatter.ofPattern("d MMMM"))
}

/** Replies are written for the chat, where newlines are meaningful; the list wants one line. */
private fun String.oneLine(): String = replace('\n', ' ').replace(Regex(" +"), " ").trim()

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
