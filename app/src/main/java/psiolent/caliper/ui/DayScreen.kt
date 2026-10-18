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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import psiolent.caliper.AppContainer
import psiolent.caliper.data.LogEntryEntity
import psiolent.caliper.data.MealEntity
import psiolent.caliper.domain.Nutrient
import psiolent.caliper.domain.Nutrients
import psiolent.caliper.integrations.HealthConnect
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The day: what it adds up to, then each meal — named and marked with its own time —
 * and the items eaten for it.
 */
@Composable
fun DayScreen(container: AppContainer) {
    var epochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val date = LocalDate.ofEpochDay(epochDay)
    val flow = remember(date) { container.repository.observeDay(date) }
    val day by flow.collectAsStateWithLifecycle(initialValue = null)

    val context = LocalContext.current
    var burned by remember { mutableStateOf<HealthConnect.Burned?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(HealthConnect.permissionContract) { refresh++ }
    LaunchedEffect(epochDay, refresh) {
        burned = HealthConnect.burned(context, date)
    }

    val totals = day?.totals ?: Nutrients.EMPTY
    // A meal can exist before anything is logged to it; an empty one is not worth showing.
    val meals = day?.meals.orEmpty().filter { it.entries.isNotEmpty() }

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
            item { DayTotals(totals, burned) { permissionLauncher.launch(HealthConnect.permissions) } }

            if (meals.isEmpty()) {
                item {
                    Text(
                        "(nothing logged yet)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
            } else {
                meals.forEach { group ->
                    item(key = "meal:${group.meal.id}") {
                        MealHeader(
                            meal = group.meal,
                            kcal = group.entries.sumOf { it.nutrients[Nutrient.KCAL] ?: 0.0 },
                        )
                    }
                    items(group.entries, key = { it.id }) { entry -> ItemRow(entry) }
                }
            }
        }
    }
}

@Composable
private fun MealHeader(meal: MealEntity, kcal: Double) {
    Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(meal.label, style = MaterialTheme.typography.titleSmall)
            Text(
                "${round(kcal)} kcal",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            timeFormat.format(Instant.ofEpochMilli(meal.at).atZone(ZoneId.systemDefault())),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 6.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun DayTotals(totals: Nutrients, burned: HealthConnect.Burned?, onConnect: () -> Unit) {
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

        when (burned) {
            is HealthConnect.Burned.Value -> Text(
                when {
                    burned.totalKcal != null && burned.activeKcal != null ->
                        "burned ${round(burned.totalKcal)} kcal total \u00b7 ${round(burned.activeKcal)} active"
                    burned.totalKcal != null -> "burned ${round(burned.totalKcal)} kcal total"
                    burned.activeKcal != null ->
                        "burned ${round(burned.activeKcal)} kcal active (resting burn not recorded)"
                    else -> "No calories burned recorded for this day"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HealthConnect.Burned.NotGranted -> TextButton(onClick = onConnect, contentPadding = PaddingValues(0.dp)) {
                Text("Connect Health Connect to show calories burned")
            }
            HealthConnect.Burned.Unavailable, null -> Unit
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
private fun ItemRow(entry: LogEntryEntity) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${round(entry.amount)} ${entry.unit} \u00b7 ${round(entry.grams)} g",
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

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")
private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
