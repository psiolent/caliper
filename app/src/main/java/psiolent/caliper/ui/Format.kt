package psiolent.caliper.ui

import psiolent.caliper.domain.Nutrient
import psiolent.caliper.domain.Nutrients

/** Whole numbers print whole; everything else gets one decimal. */
internal fun round(value: Double): String =
    if (value >= 10 || value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)

/**
 * One nutrient as "value unit name", e.g. "13 g protein" or "380 kcal".
 *
 * Spaces inside the phrase are non-breaking, so a line breaks only between whole
 * entries. A break inside one stranded a bare "g" — or a bare "poly" — on its own line.
 */
internal fun formatNutrient(nutrient: Nutrient, value: Double): String =
    if (nutrient == Nutrient.KCAL) "${round(value)}\u00a0kcal"
    else "${round(value)}\u00a0${nutrient.unit}\u00a0${nutrient.shortName}"

/** Just the amount: "19 g", "872 mg". For a row whose name is already a column. */
internal fun formatAmount(nutrient: Nutrient, value: Double): String =
    "${round(value)}\u00a0${nutrient.unit}"

private val SUMMARY_ORDER = listOf(
    Nutrient.KCAL,
    Nutrient.PROTEIN,
    Nutrient.FAT,
    Nutrient.CARBS,
    Nutrient.FIBER,
)

/** A compact one-line nutrition summary, for verifying a food at a glance. */
internal fun Nutrients.summary(): String =
    SUMMARY_ORDER.mapNotNull { nutrient -> this[nutrient]?.let { formatNutrient(nutrient, it) } }
        .joinToString(" \u00b7 ")
        .ifBlank { "no nutrition recorded" }
