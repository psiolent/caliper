package psiolent.caliper.ui

import psiolent.caliper.domain.Nutrient
import psiolent.caliper.domain.Nutrients

/** Whole numbers print whole; everything else gets one decimal. */
internal fun round(value: Double): String =
    if (value >= 10 || value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)

/** One nutrient as "value unit name", e.g. "13 g protein" or "380 kcal". */
internal fun formatNutrient(nutrient: Nutrient, value: Double): String =
    if (nutrient == Nutrient.KCAL) "${round(value)} kcal" else "${round(value)} ${nutrient.unit} ${nutrient.shortName}"

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
