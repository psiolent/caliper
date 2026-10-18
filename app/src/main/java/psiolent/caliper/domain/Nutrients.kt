package psiolent.caliper.domain

import kotlinx.serialization.Serializable

/**
 * The fixed vocabulary of nutrients Caliper tracks.
 *
 * This is deliberately a closed set rather than a flexible dictionary: adding a
 * nutrient means adding an enum value, and nothing downstream has to handle an
 * arbitrary nutrient list.
 */
@Serializable
enum class Nutrient(val displayName: String, val unit: String, val shortName: String) {
    KCAL("Calories", "kcal", "calories"),
    PROTEIN("Protein", "g", "protein"),
    FAT("Total fat", "g", "fat"),
    SATURATED_FAT("Saturated fat", "g", "saturated"),
    TRANS_FAT("Trans fat", "g", "trans"),
    MONOUNSATURATED_FAT("Monounsaturated fat", "g", "mono"),
    POLYUNSATURATED_FAT("Polyunsaturated fat", "g", "poly"),
    CARBS("Carbohydrates", "g", "carbs"),
    FIBER("Fiber", "g", "fiber"),
    SOLUBLE_FIBER("Soluble fiber", "g", "soluble fiber"),
    INSOLUBLE_FIBER("Insoluble fiber", "g", "insoluble fiber"),
    SUGARS("Sugars", "g", "sugars"),
    SODIUM("Sodium", "mg", "sodium"),
    CHOLESTEROL("Cholesterol", "mg", "cholesterol"),
}

/**
 * An amount of each tracked nutrient.
 *
 * Only nutrients with a known value are present: an absent key means "unknown",
 * not zero. Values are for whatever basis the holder defines (per 100 g for a
 * food, per recipe for a recipe, per day for a total).
 */
@Serializable
data class Nutrients(val amounts: Map<Nutrient, Double> = emptyMap()) {

    operator fun get(nutrient: Nutrient): Double? = amounts[nutrient]

    /** Sum two panels, treating an unknown value as absent rather than zero. */
    operator fun plus(other: Nutrients): Nutrients {
        if (amounts.isEmpty()) return other
        if (other.amounts.isEmpty()) return this
        val merged = amounts.toMutableMap()
        other.amounts.forEach { (nutrient, value) ->
            merged[nutrient] = (merged[nutrient] ?: 0.0) + value
        }
        return Nutrients(merged)
    }

    operator fun times(factor: Double): Nutrients =
        Nutrients(amounts.mapValues { (_, value) -> value * factor })

    /** Scale a per-100 g panel to an absolute gram weight. */
    fun forGrams(grams: Double): Nutrients = times(grams / 100.0)

    /**
     * What a set of a nutrient's parts leaves of the whole unaccounted for — null when
     * the whole is unknown, which is not the same as nothing being left over.
     *
     * Saturated, trans, mono and poly fat should account for all of a fat total, so a
     * remainder means a panel never recorded them. Fiber and sugars are components of
     * carbohydrate rather than a partition of it, so a remainder there is starch. The
     * arithmetic is the same either way; only what it means differs, which is the
     * caller's business.
     */
    fun unaccounted(whole: Nutrient, parts: List<Nutrient>): Double? =
        this[whole]?.let { total -> total - parts.mapNotNull { this[it] }.sum() }

    companion object {
        val EMPTY = Nutrients()
    }
}

/** Reduce a nutrient key to a comparable form: letters only, trailing unit stripped. */
private fun nutrientKeyForm(raw: String): String =
    raw.lowercase()
        .filter(Char::isLetter)
        .removeSuffix("grams")
        .removeSuffix("gram")
        .removeSuffix("mg")
        .removeSuffix("g")

private val NUTRIENT_ALIASES: Map<String, Nutrient> = buildMap {
    Nutrient.entries.forEach { put(nutrientKeyForm(it.name), it) }
    listOf(
        "calories" to Nutrient.KCAL,
        "energy" to Nutrient.KCAL,
        "kilocalories" to Nutrient.KCAL,
        "totalfat" to Nutrient.FAT,
        "saturatedfattyacids" to Nutrient.SATURATED_FAT,
        "saturated" to Nutrient.SATURATED_FAT,
        "transfattyacids" to Nutrient.TRANS_FAT,
        "trans" to Nutrient.TRANS_FAT,
        "monounsaturatedfattyacids" to Nutrient.MONOUNSATURATED_FAT,
        "monounsaturated" to Nutrient.MONOUNSATURATED_FAT,
        "mono" to Nutrient.MONOUNSATURATED_FAT,
        "polyunsaturatedfattyacids" to Nutrient.POLYUNSATURATED_FAT,
        "polyunsaturated" to Nutrient.POLYUNSATURATED_FAT,
        "poly" to Nutrient.POLYUNSATURATED_FAT,
        "carbohydrate" to Nutrient.CARBS,
        "carbohydrates" to Nutrient.CARBS,
        "totalcarbohydrate" to Nutrient.CARBS,
        "totalcarbohydrates" to Nutrient.CARBS,
        "dietaryfiber" to Nutrient.FIBER,
        "fibre" to Nutrient.FIBER,
        "totalfiber" to Nutrient.FIBER,
        "solublefibre" to Nutrient.SOLUBLE_FIBER,
        "insolublefibre" to Nutrient.INSOLUBLE_FIBER,
        "sugar" to Nutrient.SUGARS,
        "totalsugars" to Nutrient.SUGARS,
        "na" to Nutrient.SODIUM,
        "chol" to Nutrient.CHOLESTEROL,
    ).forEach { (alias, nutrient) -> put(nutrientKeyForm(alias), nutrient) }
}

/** Resolve a nutrient from a model-supplied key: an enum name, or a common label variant. */
fun nutrientByKey(key: String): Nutrient? = NUTRIENT_ALIASES[nutrientKeyForm(key)]

/** A panel of nutrients, plus the amounts a recipe is computed from. */
