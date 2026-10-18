package psiolent.caliper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import psiolent.caliper.AppContainer
import psiolent.caliper.agent.ModelInfo
import psiolent.caliper.agent.OpenRouterClient
import psiolent.caliper.data.BackupCodec

@Composable
fun SettingsScreen(container: AppContainer) {
    val settings = container.settings
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var apiKey by rememberSaveable { mutableStateOf(settings.openRouterApiKey) }
    var chatModel by rememberSaveable { mutableStateOf(settings.chatModel) }
    var visionModel by rememberSaveable { mutableStateOf(settings.visionModel) }
    var usdaKey by rememberSaveable { mutableStateOf(settings.usdaApiKey) }
    var models by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            status = runCatching {
                val text = BackupCodec.encode(container.repository.snapshot())
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                }
                "Exported."
            }.getOrElse { "Export failed: ${it.message}" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            status = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                } ?: error("could not read the file")
                container.repository.restore(BackupCodec.decode(text))
                "Imported."
            }.getOrElse { "Import failed: ${it.message}" }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; settings.openRouterApiKey = it },
            label = { Text("OpenRouter API key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = chatModel,
            onValueChange = { chatModel = it; settings.chatModel = it },
            label = { Text("Chat / tool model") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = visionModel,
            onValueChange = { visionModel = it; settings.visionModel = it },
            label = { Text("Vision model override (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = usdaKey,
            onValueChange = { usdaKey = it; settings.usdaApiKey = it },
            label = { Text("USDA FoodData Central key (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Button(onClick = {
            scope.launch {
                status = "Loading\u2026"
                runCatching { OpenRouterClient("").models() }
                    .onSuccess { models = it; status = "${it.size} models" }
                    .onFailure { status = "Failed: ${it.message}" }
            }
        }) { Text("Load current model list") }

        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        if (models.isNotEmpty()) {
            Text("Tap a model to use it", style = MaterialTheme.typography.titleSmall)
            models.take(80).forEach { model ->
                TextButton(onClick = { settings.chatModel = model.id; chatModel = model.id }) {
                    Text(model.id)
                }
            }
        }

        Text(
            "Spent on OpenRouter so far: $${"%.4f".format(settings.totalCost)}",
            style = MaterialTheme.typography.bodyMedium,
        )

        Text("Backup", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { exportLauncher.launch("caliper-backup.json") }) { Text("Export data") }
            Button(onClick = { importLauncher.launch(arrayOf("application/json")) }) { Text("Import data") }
        }
        Text(
            "Android Auto Backup also copies the database to your Google Drive automatically.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
