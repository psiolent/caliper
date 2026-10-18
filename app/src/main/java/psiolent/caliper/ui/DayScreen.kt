package psiolent.caliper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import psiolent.caliper.AppContainer
import psiolent.caliper.data.LogEntryEntity
import psiolent.caliper.domain.Nutrient
import psiolent.caliper.domain.Nutrients
import psiolent.caliper.integrations.HealthConnect
import java.time.LocalDate

@Composable
fun DayScreen(container: AppContainer) {
    var epochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val date = LocalDate.ofEpochDay(epochDay)
    val flow = remember(date) { container.repository.observeDay(date) }
    val day by flow.collectAsStateWithLifecycle(initialValue = null)

    val context = LocalContext.current
    val healthAvailable = remember { HealthConnect.available(context) }
    var burned by remember { mutableStateOf<Double?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(HealthConnect.permissionContract) { refresh++ }
    LaunchedEffect(epochDay, refresh) {
        burned = HealthConnect.burnedCalories(context, date)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { epochDay -= 1 }) { Text("\u2039 Prev") }
            Text(date.toString(), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { epochDay += 1 }) { Text("Next \u203A") }
        }

        if (healthAvailable) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    burned?.let { "Burned ${round(it)} kcal" } ?: "Health Connect not connected",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (burned == null) {
                    TextButton(onClick = { permissionLauncher.launch(HealthConnect.permissions) }) { Text("Connect") }
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text("Totals", style = MaterialTheme.typography.titleSmall)
                val totals = day?.totals ?: Nutrients.EMPTY
                if (totals.amounts.isEmpty()) {
                    Text("Nothing logged.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    displayOrder.forEach { nutrient ->
                        totals[nutrient]?.let { value ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(nutrient.displayName)
                                Text("${round(value)} ${nutrient.unit}")
                            }
                        }
                    }
                }
            }

            val entries = day?.entries.orEmpty()
            if (entries.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Entries", style = MaterialTheme.typography.titleSmall)
                }
                items(entries, key = { it.id }) { entry -> EntryRow(entry) }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: LogEntryEntity) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(entry.label, modifier = Modifier.weight(1f))
            val kcal = entry.nutrients[Nutrient.KCAL]
            if (kcal != null) {
                Text(
                    "${round(kcal)} kcal",
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        val detail = buildString {
            append("${round(entry.amount)} ${entry.unit} \u00b7 ${round(entry.grams)} g")
            entry.meal?.let { append(" \u00b7 ${it.displayName.lowercase()}") }
        }
        Text(detail, style = MaterialTheme.typography.bodySmall)
    }
}

/** Display order for the totals list, broad to specific. */
private val displayOrder = listOf(
        Nutrient.KCAL,
        Nutrient.PROTEIN,
        Nutrient.CARBS,
        Nutrient.FAT,
        Nutrient.SATURATED_FAT,
        Nutrient.TRANS_FAT,
        Nutrient.MONOUNSATURATED_FAT,
        Nutrient.POLYUNSATURATED_FAT,
        Nutrient.FIBER,
        Nutrient.SOLUBLE_FIBER,
        Nutrient.INSOLUBLE_FIBER,
        Nutrient.SUGARS,
        Nutrient.SODIUM,
        Nutrient.CHOLESTEROL,
    )
