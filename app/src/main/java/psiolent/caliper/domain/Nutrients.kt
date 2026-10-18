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
enum class Nutrient(val displayName: String, val unit: String) {
    KCAL("Calories", "kcal"),
    PROTEIN("Protein", "g"),
    FAT("Total fat", "g"),
    SATURATED_FAT("Saturated fat", "g"),
    TRANS_FAT("Trans fat", "g"),
    MONOUNSATURATED_FAT("Monounsaturated fat", "g"),
    POLYUNSATURATED_FAT("Polyunsaturated fat", "g"),
    CARBS("Carbohydrates", "g"),
    FIBER("Fiber", "g"),
    SOLUBLE_FIBER("Soluble fiber", "g"),
    INSOLUBLE_FIBER("Insoluble fiber", "g"),
    SUGARS("Sugars", "g"),
    SODIUM("Sodium", "mg"),
    CHOLESTEROL("Cholesterol", "mg"),
}

/**
 * An amount of each tracked nutrient.
 *
 * Only nutrients with a known value are present: an absent key means "unknown",
 * not zero. Values are for whatever basis the holder defines (per 100 g for a
 * food, per batch for a batch, per day for a total).
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

    companion object {
        val EMPTY = Nutrients()
    }
}

/** Resolve a nutrient from a model-supplied key (enum name, or a loose variant of it). */
fun nutrientByKey(key: String): Nutrient? {
    val normalized = key.trim().lowercase().replace("_", "").replace(" ", "")
    return Nutrient.entries.firstOrNull { it.name.lowercase().replace("_", "") == normalized }
}

/** Optional label for a log entry. Absent means "unlabelled", which is normal. */
@Serializable
enum class Meal(val displayName: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack"),
}
