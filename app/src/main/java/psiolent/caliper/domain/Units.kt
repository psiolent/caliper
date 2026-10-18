package psiolent.caliper.domain

import kotlinx.serialization.Serializable

/** A named portion of a specific food, with the weight it represents. */
@Serializable
data class Serving(val name: String, val grams: Double)

/** Mass units, exact by definition. */
private enum class Mass(val grams: Double) {
    MILLIGRAM(0.001),
    GRAM(1.0),
    KILOGRAM(1000.0),
    OUNCE(28.349523125),
    POUND(453.59237),
}

/** Volume units, exact by definition. */
private enum class Volume(val millilitres: Double) {
    MILLILITRE(1.0),
    LITRE(1000.0),
    TEASPOON(4.92892159375),
    TABLESPOON(14.78676478125),
    FLUID_OUNCE(29.5735295625),
    CUP(236.5882365),
    PINT(473.176473),
    QUART(946.352946),
    GALLON(3785.411784),
}

private val MASS_UNITS: Map<String, Mass> = buildMap {
    put("mg", Mass.MILLIGRAM); put("milligram", Mass.MILLIGRAM); put("milligrams", Mass.MILLIGRAM)
    put("g", Mass.GRAM); put("gram", Mass.GRAM); put("grams", Mass.GRAM)
    put("kg", Mass.KILOGRAM); put("kilogram", Mass.KILOGRAM); put("kilograms", Mass.KILOGRAM)
    put("oz", Mass.OUNCE); put("ounce", Mass.OUNCE); put("ounces", Mass.OUNCE)
    put("lb", Mass.POUND); put("lbs", Mass.POUND); put("pound", Mass.POUND); put("pounds", Mass.POUND)
}

private val VOLUME_UNITS: Map<String, Volume> = buildMap {
    put("ml", Volume.MILLILITRE); put("millilitre", Volume.MILLILITRE)
    put("milliliter", Volume.MILLILITRE); put("millilitres", Volume.MILLILITRE); put("milliliters", Volume.MILLILITRE)
    put("l", Volume.LITRE); put("litre", Volume.LITRE); put("liter", Volume.LITRE)
    put("litres", Volume.LITRE); put("liters", Volume.LITRE)
    put("tsp", Volume.TEASPOON); put("teaspoon", Volume.TEASPOON); put("teaspoons", Volume.TEASPOON)
    put("tbsp", Volume.TABLESPOON); put("tablespoon", Volume.TABLESPOON); put("tablespoons", Volume.TABLESPOON)
    put("fl oz", Volume.FLUID_OUNCE); put("floz", Volume.FLUID_OUNCE)
    put("fluid ounce", Volume.FLUID_OUNCE); put("fluid ounces", Volume.FLUID_OUNCE)
    put("cup", Volume.CUP); put("cups", Volume.CUP)
    put("pint", Volume.PINT); put("pints", Volume.PINT)
    put("quart", Volume.QUART); put("quarts", Volume.QUART)
    put("gallon", Volume.GALLON); put("gallons", Volume.GALLON)
}

/**
 * Turn an amount expressed in some unit into grams, for a food that may define
 * its own named servings.
 *
 * - A mass unit is exact and needs nothing else.
 * - A unit matching one of the food's serving names uses that serving's weight.
 * - A volume unit resolves through a serving the food defines in volume terms
 *   ("1 cup = 80 g"), converting within the volume dimension first.
 *
 * Returns null when the unit cannot be resolved for this food; callers must
 * surface that rather than guessing.
 */
fun resolveGrams(amount: Double, unit: String, servings: List<Serving>): Double? {
    val key = unit.trim().lowercase()
    if (key.isEmpty()) return null

    MASS_UNITS[key]?.let { return amount * it.grams }

    servings.firstOrNull { it.name.trim().lowercase() == key }?.let { return amount * it.grams }

    val volume = VOLUME_UNITS[key] ?: return null
    val servingInVolume = servings.firstNotNullOfOrNull { serving ->
        VOLUME_UNITS[serving.name.trim().lowercase()]?.let { it to serving.grams }
    } ?: return null
    val (servingVolume, servingGrams) = servingInVolume
    return amount * (volume.millilitres / servingVolume.millilitres) * servingGrams
}
