package psiolent.caliper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import psiolent.caliper.domain.Nutrient
import psiolent.caliper.domain.Nutrients

/** What a breakdown is being opened for, and under what name. */
internal data class BreakdownRequest(
    val title: String,
    val panel: Nutrients,
    /** A line under the title: what the panel is of, when that is not the title. */
    val subtitle: String? = null,
)

private val FAT_PARTS = listOf(
    Nutrient.SATURATED_FAT,
    Nutrient.TRANS_FAT,
    Nutrient.MONOUNSATURATED_FAT,
    Nutrient.POLYUNSATURATED_FAT,
)

/** A gap smaller than this is rounding, not something missing. */
private const val GAP_FLOOR = 1.0

/** What a line is: the group's whole, one of its parts, or the unaccounted gap. */
private enum class Emphasis { WHOLE, PART, GAP }

private data class BreakdownLine(val name: String, val value: String, val emphasis: Emphasis)

/**
 * The full nutrient breakdown of one panel — the day's totals, a single logged item, or
 * a food from the library — over whatever screen it was opened from.
 *
 * All three uses share this one component, so a panel is broken down in exactly one
 * place and reads the same way wherever it is looked at.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BreakdownSheet(request: BreakdownRequest, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // A breakdown does not fit half a screen, and opening already expanded beats
        // showing it clipped and making the reader drag the sheet up.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                request.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            request.subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.padding(top = 14.dp)) { Breakdown(request.panel) }
        }
    }
}

@Composable
private fun Breakdown(panel: Nutrients) {
    val groups = listOf(
        detailGroup(panel, Nutrient.FAT.displayName, Nutrient.FAT, FAT_PARTS, "not recorded"),
        detailGroup(
            panel,
            Nutrient.CARBS.displayName,
            Nutrient.CARBS,
            listOf(Nutrient.FIBER, Nutrient.SUGARS),
            "starch and other",
        ),
        detailGroup(panel, Nutrient.PROTEIN.displayName, Nutrient.PROTEIN, emptyList(), null),
        detailGroup(panel, "Other", null, listOf(Nutrient.SODIUM, Nutrient.CHOLESTEROL), null),
    ).filter { it.isNotEmpty() }

    if (groups.isEmpty()) {
        Text(
            "Nothing broken down for this one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    groups.forEachIndexed { index, lines ->
        if (index > 0) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        lines.forEach { BreakdownRow(it) }
    }
}

/**
 * One group's lines: the whole it breaks down, each part, and what the parts leave over.
 *
 * That last line is the point of the shape. Saturated, trans, mono and poly fat should
 * account for all of a fat total, so a shortfall means a panel never recorded them —
 * worth saying rather than leaving the reader to wonder why the numbers do not add up.
 * Fiber and sugars are components of carbohydrate rather than a partition of it, so
 * their shortfall is real food (starch) and is named as such.
 */
private fun detailGroup(
    panel: Nutrients,
    heading: String,
    whole: Nutrient?,
    parts: List<Nutrient>,
    gapName: String?,
): List<BreakdownLine> {
    val total = whole?.let { panel[it] }
    val known = parts.mapNotNull { nutrient -> panel[nutrient]?.let { nutrient to it } }
    if (total == null && known.isEmpty()) return emptyList()

    val lines = mutableListOf(
        BreakdownLine(
            name = whole?.displayName ?: heading,
            value = if (whole != null && total != null) formatAmount(whole, total) else "",
            emphasis = Emphasis.WHOLE,
        ),
    )
    known.forEach { (nutrient, amount) ->
        lines += BreakdownLine(nutrient.displayName, formatAmount(nutrient, amount), Emphasis.PART)
    }
    if (whole != null && total != null && gapName != null) {
        val gap = panel.unaccounted(whole, parts)
        if (gap != null && gap >= GAP_FLOOR) {
            lines += BreakdownLine(gapName, "${round(gap)}\u00a0${whole.unit}", Emphasis.GAP)
        }
    }
    return lines
}

@Composable
private fun BreakdownRow(line: BreakdownLine) {
    val whole = line.emphasis == Emphasis.WHOLE
    val style = if (whole) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
    val weight = if (whole) FontWeight.SemiBold else null
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
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
                // Worked out from the others rather than measured, so it is quieter.
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
