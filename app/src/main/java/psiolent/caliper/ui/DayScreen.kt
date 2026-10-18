package psiolent.caliper.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
 *
 * The totals stay short on purpose: the day's own breakdown is one tap away, and the
 * same sheet opens for a single item, so a panel reads the same wherever it is looked
 * at.
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

    // What the breakdown sheet is open for, if anything: the day's totals, or one item.
    var breakdown by remember { mutableStateOf<BreakdownRequest?>(null) }

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
            item {
                DayTotals(
                    totals = totals,
                    burned = burned,
                    onConnect = { permissionLauncher.launch(HealthConnect.permissions) },
                    onFullBreakdown = {
                        breakdown = BreakdownRequest(
                            title = if (date == LocalDate.now()) "Today" else date.format(dayFormat),
                            panel = totals,
                        )
                    },
                )
            }

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
                meals.forEachIndexed { index, group ->
                    item(key = "meal:${group.meal.id}") {
                        MealHeader(
                            meal = group.meal,
                            kcal = group.entries.sumOf { it.nutrients[Nutrient.KCAL] ?: 0.0 },
                            first = index == 0,
                        )
                    }
                    items(group.entries, key = { it.id }) { entry ->
                        ItemRow(entry) {
                            breakdown = BreakdownRequest(entry.label, entry.nutrients, portion(entry))
                        }
                    }
                }
            }
        }
    }

    breakdown?.let { request -> BreakdownSheet(request) { breakdown = null } }
}

@Composable
private fun MealHeader(meal: MealEntity, kcal: Double, first: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = if (first) 24.dp else 8.dp)) {
        // The rule goes *above* the meal, separating it from the one before. It used to
        // sit under the name, which cut the meal off from its own items and left the
        // meals running together.
        if (!first) {
            HorizontalDivider(
                modifier = Modifier.padding(bottom = 18.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                meal.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${round(kcal)} kcal",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            timeFormat.format(Instant.ofEpochMilli(meal.at).atZone(ZoneId.systemDefault())),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayTotals(
    totals: Nutrients,
    burned: HealthConnect.Burned?,
    onConnect: () -> Unit,
    onFullBreakdown: () -> Unit,
) {
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                round(totals[Nutrient.KCAL] ?: 0.0),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                // The one number the screen is about: the accent marks it, and the two
                // watched nutrients below, so colour here means "this matters".
                color = MaterialTheme.colorScheme.primary,
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

        // The two worth watching day to day: how much saturated fat, how much fiber.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MacroCard("saturated fat", totals[Nutrient.SATURATED_FAT], accent = true)
            MacroCard("fiber", totals[Nutrient.FIBER], accent = true)
        }

        if (totals.amounts.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clickable(onClick = onFullBreakdown)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Full breakdown", style = MaterialTheme.typography.bodyLarge)
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RowScope.MacroCard(label: String, grams: Double?, accent: Boolean = false) {
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
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ItemRow(entry: LogEntryEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                portion(entry),
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

/**
 * What was eaten, in the units it was logged in: "2 × 1 large egg · 122 g". When the
 * unit already is grams, saying it twice ("64 g · 64 g") adds nothing, so it is said
 * once.
 */
private fun portion(entry: LogEntryEntity): String =
    if (entry.unit.equals("g", ignoreCase = true)) {
        "${round(entry.grams)} g"
    } else {
        "${round(entry.amount)} \u00d7 ${entry.unit} \u00b7 ${round(entry.grams)} g"
    }

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")
private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
