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
                meals.forEachIndexed { index, group ->
                    item(key = "meal:${group.meal.id}") {
                        MealHeader(
                            meal = group.meal,
                            kcal = group.entries.sumOf { it.nutrients[Nutrient.KCAL] ?: 0.0 },
                            first = index == 0,
                        )
                    }
                    items(group.entries, key = { it.id }) { entry -> ItemRow(entry) }
                }
            }
        }
    }
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
private fun DayTotals(totals: Nutrients, burned: HealthConnect.Burned?, onConnect: () -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                round(totals[Nutrient.KCAL] ?: 0.0),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                // The one number the screen is about: the only place the accent is used,
                // so it keeps meaning "this is the figure that matters".
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

        DetailCard(totals)
    }
}

private val FAT_PARTS = listOf(
    Nutrient.SATURATED_FAT,
    Nutrient.TRANS_FAT,
    Nutrient.MONOUNSATURATED_FAT,
    Nutrient.POLYUNSATURATED_FAT,
)

/** A gap smaller than this is rounding, not something missing. */
private const val GAP_FLOOR = 1.0

/** What a detail line is: the group's whole, one of its parts, or the unaccounted gap. */
private enum class Emphasis { WHOLE, PART, GAP }

private data class DetailLine(val name: String, val value: String, val emphasis: Emphasis)

/**
 * The day's finer detail: one line per nutrient, grouped under the thing it belongs to,
 * each group starting from its whole and ending with whatever the parts leave over.
 *
 * That last line is the point of the shape. Saturated, mono, poly and trans fat should
 * account for all of a day's fat, so a gap means a food's panel never recorded them —
 * worth saying rather than leaving the reader to wonder why the numbers do not add up.
 * Fiber and sugars are components of carbohydrate rather than a partition of it, so
 * their gap is real food (starch) and is named as such.
 */
@Composable
private fun DetailCard(totals: Nutrients) {
    val groups = listOf(
        detailGroup(totals, Nutrient.FAT.displayName, Nutrient.FAT, FAT_PARTS, "not recorded"),
        detailGroup(
            totals,
            Nutrient.CARBS.displayName,
            Nutrient.CARBS,
            listOf(Nutrient.FIBER, Nutrient.SUGARS),
            "starch and other",
        ),
        detailGroup(totals, "Other", null, listOf(Nutrient.SODIUM, Nutrient.CHOLESTEROL), null),
    ).filter { it.isNotEmpty() }
    if (groups.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            // A quieter tone than the macro cards' surfaceVariant, so the detail reads
            // as one step below them rather than as more of the same.
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        groups.forEachIndexed { index, lines ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            lines.forEach { DetailRow(it) }
        }
    }
}

/** A group's lines, or nothing at all when none of its parts are known. */
private fun detailGroup(
    totals: Nutrients,
    heading: String,
    whole: Nutrient?,
    parts: List<Nutrient>,
    gapName: String?,
): List<DetailLine> {
    val known = parts.mapNotNull { nutrient -> totals[nutrient]?.let { nutrient to it } }
    if (known.isEmpty()) return emptyList()

    val total = whole?.let { totals[it] }
    val lines = mutableListOf(
        DetailLine(
            name = heading,
            value = if (whole != null && total != null) formatAmount(whole, total) else "",
            emphasis = Emphasis.WHOLE,
        ),
    )
    known.forEach { (nutrient, amount) ->
        lines += DetailLine(nutrient.displayName, formatAmount(nutrient, amount), Emphasis.PART)
    }
    if (whole != null && total != null && gapName != null) {
        val gap = total - known.sumOf { it.second }
        if (gap >= GAP_FLOOR) {
            lines += DetailLine(gapName, "${round(gap)}\u00a0${whole.unit}", Emphasis.GAP)
        }
    }
    return lines
}

@Composable
private fun DetailRow(line: DetailLine) {
    val whole = line.emphasis == Emphasis.WHOLE
    val style = if (whole) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
    val weight = if (whole) FontWeight.SemiBold else null
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            line.name,
            style = style,
            fontWeight = weight,
            color = if (whole) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            // The parts sit under the whole they belong to.
            modifier = Modifier.padding(start = if (whole) 0.dp else 14.dp).weight(1f),
        )
        Text(
            line.value,
            style = style,
            fontWeight = weight,
            color = if (line.emphasis == Emphasis.GAP) {
                // The gap is worked out from the others, not measured, so it is quieter.
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
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
private fun ItemRow(entry: LogEntryEntity) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
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
