package psiolent.caliper.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import psiolent.caliper.AppContainer
import psiolent.caliper.data.LogEntryEntity
import psiolent.caliper.domain.Nutrient
import psiolent.caliper.domain.Nutrients
import psiolent.caliper.integrations.HealthConnect
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The day, in three tiers: what the day adds up to, then roughly when, then each
 * item. Calories lead; protein, fat and carbs follow; fat and carb breakdowns are
 * visible because they matter; everything else is muted at the bottom.
 */
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

    val totals = day?.totals ?: Nutrients.EMPTY
    val entries = day?.entries.orEmpty()

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { epochDay -= 1 }) { Text("\u2039 Prev") }
            Text(date.format(dayFormat), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { epochDay += 1 }) { Text("Next \u203A") }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { DayTotals(totals, burned, healthAvailable) { permissionLauncher.launch(HealthConnect.permissions) } }
            if (entries.isEmpty()) {
                item {
                    Text(
                        "(nothing logged yet)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
            } else {
                items(dayRows(entries), key = { it.key }) { row ->
                    when (row) {
                        is DayRow.Band -> BandHeader(row)
                        is DayRow.Item -> ItemRow(row.entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayTotals(totals: Nutrients, burned: Double?, healthAvailable: Boolean, onConnect: () -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                round(totals[Nutrient.KCAL] ?: 0.0),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                " kcal eaten",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
            )
        }

        when {
            burned != null -> Text(
                "burned ${round(burned)} \u00b7 net ${round((totals[Nutrient.KCAL] ?: 0.0) - burned)} kcal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            healthAvailable -> TextButton(onClick = onConnect, contentPadding = PaddingValues(0.dp)) {
                Text("Connect Health Connect to show calories burned")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MacroCard("protein", totals[Nutrient.PROTEIN])
            MacroCard("fat", totals[Nutrient.FAT])
            MacroCard("carbs", totals[Nutrient.CARBS])
        }

        Breakdown("Fat", totals, listOf(
            Nutrient.SATURATED_FAT,
            Nutrient.TRANS_FAT,
            Nutrient.MONOUNSATURATED_FAT,
            Nutrient.POLYUNSATURATED_FAT,
        ))
        Breakdown("Carbs", totals, listOf(
            Nutrient.FIBER,
            Nutrient.SOLUBLE_FIBER,
            Nutrient.INSOLUBLE_FIBER,
            Nutrient.SUGARS,
        ))

        val rest = listOf(Nutrient.SODIUM, Nutrient.CHOLESTEROL)
            .mapNotNull { nutrient -> totals[nutrient]?.let { formatNutrient(nutrient, it) } }
        if (rest.isNotEmpty()) {
            Text(
                rest.joinToString("  \u00b7  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun RowScope.MacroCard(label: String, grams: Double?) {
    Column(
        modifier = Modifier
            .weight(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            grams?.let { "${round(it)} g" } ?: "\u2014",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Breakdown(label: String, totals: Nutrients, parts: List<Nutrient>) {
    val line = parts
        .mapNotNull { nutrient -> totals[nutrient]?.let { "${nutrient.shortName} ${round(it)}" } }
        .joinToString("  \u00b7  ")
    if (line.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp),
        )
        Text(line, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BandHeader(band: DayRow.Band) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            band.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${round(band.kcal)} kcal",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ItemRow(entry: LogEntryEntity) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            timeFormat.format(Instant.ofEpochMilli(entry.at).atZone(ZoneId.systemDefault())),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp).padding(top = 3.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(entry.label, style = MaterialTheme.typography.bodyLarge)
            val detail = buildString {
                append("${round(entry.amount)} ${entry.unit} \u00b7 ${round(entry.grams)} g")
                entry.meal?.let { append(" \u00b7 ${it.displayName.lowercase()}") }
            }
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        entry.nutrients[Nutrient.KCAL]?.let { kcal ->
            Text(
                round(kcal),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp, top = 2.dp),
            )
        }
    }
}

/** Flatten entries into band headers followed by their items, in clock order. */
private fun dayRows(entries: List<LogEntryEntity>): List<DayRow> {
    val rows = mutableListOf<DayRow>()
    entries.groupBy { bandOf(it.at) }.forEach { (name, group) ->
        rows += DayRow.Band(name, group.sumOf { it.nutrients[Nutrient.KCAL] ?: 0.0 })
        group.forEach { rows += DayRow.Item(it) }
    }
    return rows
}

private sealed interface DayRow {
    val key: String

    data class Band(val name: String, val kcal: Double) : DayRow {
        override val key get() = "band:$name"
    }

    data class Item(val entry: LogEntryEntity) : DayRow {
        override val key get() = "item:${entry.id}"
    }
}

private fun bandOf(epochMillis: Long): String {
    val hour = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).hour
    return when (hour) {
        in 5..11 -> "Morning"
        in 12..16 -> "Afternoon"
        in 17..21 -> "Evening"
        else -> "Night"
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
