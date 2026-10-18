package psiolent.caliper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import kotlin.math.abs

@Composable
fun SettingsScreen(container: AppContainer) {
    val settings = container.settings
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var apiKey by rememberSaveable { mutableStateOf(settings.openRouterApiKey) }
    var chatModel by rememberSaveable { mutableStateOf(settings.chatModel) }
    var visionModel by rememberSaveable { mutableStateOf(settings.visionModel) }
    var usdaKey by rememberSaveable { mutableStateOf(settings.usdaApiKey) }
    // Null until the list has been fetched, so the sheet can tell "not loaded yet" from
    // "loaded and empty".
    var models by remember { mutableStateOf<List<ModelInfo>?>(null) }
    var modelError by remember { mutableStateOf<String?>(null) }
    var pickerFor by remember { mutableStateOf<ModelSlot?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var revealKeys by rememberSaveable { mutableStateOf(false) }

    fun loadModels() {
        scope.launch {
            modelError = null
            models = null
            runCatching { OpenRouterClient("").models() }
                .onSuccess { models = it }
                .onFailure { modelError = it.message ?: it.javaClass.simpleName }
        }
    }

    fun openPicker(slot: ModelSlot) {
        pickerFor = slot
        // Only fetch once; reopening the list should be instant.
        if (models == null && modelError == null) loadModels()
    }

    // The daily goal: which way it moves the day's burn, and by how much.
    var goalDirection by rememberSaveable { mutableIntStateOf(settings.dailyAdjustmentKcal.compareTo(0)) }
    var goalAmount by rememberSaveable {
        mutableStateOf(
            settings.dailyAdjustmentKcal.takeIf { it != 0 }?.let { abs(it).toString() } ?: "500",
        )
    }
    fun applyGoal() {
        settings.dailyAdjustmentKcal = goalDirection * (goalAmount.toIntOrNull() ?: 0)
    }

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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; settings.openRouterApiKey = it },
            label = { Text("OpenRouter API key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (revealKeys) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = { revealKeys = !revealKeys }) { Text(if (revealKeys) "Hide" else "Show") } },
        )
        OutlinedTextField(
            value = chatModel,
            onValueChange = { chatModel = it; settings.chatModel = it },
            label = { Text("Chat / tool model") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { openPicker(ModelSlot.CHAT) }) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose from the model list")
                }
            },
        )
        OutlinedTextField(
            value = visionModel,
            onValueChange = { visionModel = it; settings.visionModel = it },
            label = { Text("Vision model override (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { openPicker(ModelSlot.VISION) }) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose from the model list")
                }
            },
        )
        OutlinedTextField(
            value = usdaKey,
            onValueChange = { usdaKey = it; settings.usdaApiKey = it },
            label = { Text("USDA FoodData Central key (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (revealKeys) VisualTransformation.None else PasswordVisualTransformation(),
        )

        Text("Calorie goal", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Hold" to 0, "Deficit" to -1, "Surplus" to 1).forEach { (label, direction) ->
                GoalChoice(label, goalDirection == direction) {
                    goalDirection = direction
                    applyGoal()
                }
            }
        }
        if (goalDirection != 0) {
            OutlinedTextField(
                value = goalAmount,
                onValueChange = {
                    goalAmount = it.filter(Char::isDigit).take(5)
                    applyGoal()
                },
                label = {
                    Text(if (goalDirection < 0) "Daily deficit (kcal)" else "Daily surplus (kcal)")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                "Measured against the calories Health Connect says the day will burn, so it " +
                    "needs that to be set up.",
                style = MaterialTheme.typography.bodySmall,
            )
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

    pickerFor?.let { slot ->
        ModelPickerSheet(
            slot = slot,
            models = models,
            error = modelError,
            current = if (slot == ModelSlot.CHAT) chatModel else visionModel,
            onPick = { id ->
                if (slot == ModelSlot.CHAT) {
                    settings.chatModel = id
                    chatModel = id
                } else {
                    settings.visionModel = id
                    visionModel = id
                }
                pickerFor = null
            },
            onDismiss = { pickerFor = null },
            onRetry = { loadModels() },
        )
    }
}

/**
 * One of the three daily-goal choices. The chosen one is drawn as a filled button, so the
 * current goal is readable at a glance rather than by finding a check mark.
 */
@Composable
private fun GoalChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        TextButton(onClick = onClick) { Text(label) }
    }
}
