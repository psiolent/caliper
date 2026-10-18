package psiolent.caliper.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import psiolent.caliper.agent.ModelInfo

/** Which model slot the picker is choosing for. */
internal enum class ModelSlot(val label: String) {
    CHAT("Chat and tools"),
    VISION("Photos"),
}

/**
 * OpenRouter lists several hundred models, which is more than a settings page can hold
 * inline. They get their own sheet with a search over the id and the name, since which of
 * 400 rows you want is something you find by typing, not by scrolling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelPickerSheet(
    slot: ModelSlot,
    models: List<ModelInfo>?,
    error: String?,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    "Choose a model",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    slot.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                error != null -> Column(Modifier.padding(20.dp)) {
                    Text(
                        "Could not load the list: $error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "Tap to try again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onRetry).padding(top = 8.dp),
                    )
                }

                models == null -> Text(
                    "Loading\u2026",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )

                else -> {
                    val shown = remember(models, query) { models.filter { it.matches(query) } }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        placeholder = { Text("Search models") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                    )
                    Text(
                        if (query.isBlank()) "${models.size} models" else "${shown.size} of ${models.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (shown.isEmpty()) {
                            item {
                                Text(
                                    "Nothing matches \"$query\".",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                        items(shown, key = { it.id }) { model ->
                            PickableModel(model, selected = model.id == current) { onPick(model.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickableModel(model: ModelInfo, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                model.id,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            model.name?.takeIf { it.isNotBlank() && it != model.id }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "In use",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Matches the id or the human name, so "deepseek" finds it either way. */
private fun ModelInfo.matches(query: String): Boolean {
    val text = query.trim()
    if (text.isEmpty()) return true
    return id.contains(text, ignoreCase = true) || name.orEmpty().contains(text, ignoreCase = true)
}
