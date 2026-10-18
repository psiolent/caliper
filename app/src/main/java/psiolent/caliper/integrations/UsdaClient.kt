package psiolent.caliper.integrations

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import psiolent.caliper.domain.Nutrient
import psiolent.caliper.domain.Nutrients
import psiolent.caliper.domain.Serving
import java.net.URLEncoder

/** A food as returned by USDA FoodData Central. All values are per 100 g. */
data class UsdaFood(
    val fdcId: Int,
    val name: String,
    val brand: String?,
    val per100g: Nutrients,
    val servings: List<Serving>,
)

/**
 * USDA FoodData Central. Free, public-domain, one API key. This is the accuracy
 * source for plain foods; the model never transcribes the numbers, it passes an
 * fdcId back and the app fetches the panel itself.
 */
class UsdaClient(private val apiKey: String) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String, limit: Int = 4): List<UsdaFood> {
        if (apiKey.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$BASE/foods/search?query=$encoded&pageSize=$limit" +
            "&dataType=Foundation,SR%20Legacy,Branded&api_key=$apiKey"
        val root = json.parseToJsonElement(Http.get(url)) as? JsonObject ?: return emptyList()
        val foods = root["foods"] as? JsonArray ?: return emptyList()
        return foods.mapNotNull { parseFood(it as? JsonObject ?: return@mapNotNull null) }
    }

    suspend fun byId(fdcId: Int): UsdaFood? {
        if (apiKey.isBlank()) return null
        val root = json.parseToJsonElement(Http.get("$BASE/food/$fdcId?api_key=$apiKey")) as? JsonObject
            ?: return null
        return parseFood(root)
    }

    /** Handles both the search shape (nutrientId/value) and the detail shape (nutrient/amount). */
    private fun parseFood(food: JsonObject): UsdaFood? {
        val fdcId = (food["fdcId"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return null
        val name = (food["description"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return null
        val brand = ((food["brandName"] as? JsonPrimitive)?.content ?: (food["brandOwner"] as? JsonPrimitive)?.content)
            ?.takeIf { it.isNotBlank() }

        val amounts = mutableMapOf<Nutrient, Double>()
        (food["foodNutrients"] as? JsonArray)?.forEach { element ->
            val row = element as? JsonObject ?: return@forEach
            val nested = row["nutrient"] as? JsonObject
            val id = (row["nutrientId"] ?: nested?.get("id"))?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() }
            val value = (row["value"] ?: row["amount"])?.let { (it as? JsonPrimitive)?.content?.toDoubleOrNull() }
            val nutrient = id?.let { NUTRIENT_IDS[it] }
            if (nutrient != null && value != null && nutrient !in amounts) amounts[nutrient] = value
        }
        if (amounts.isEmpty()) return null

        val servings = buildList {
            val size = (food["servingSize"] as? JsonPrimitive)?.content?.toDoubleOrNull()
            val unit = (food["servingSizeUnit"] as? JsonPrimitive)?.content
            val label = (food["householdServingFullText"] as? JsonPrimitive)?.content
            if (size != null && size > 0 && unit.equals("g", ignoreCase = true) && !label.isNullOrBlank()) {
                add(Serving(label.trim(), size))
            }
        }

        return UsdaFood(fdcId, name, brand, Nutrients(amounts), servings)
    }

    private companion object {
        const val BASE = "https://api.nal.usda.gov/fdc/v1"

        /** USDA nutrient ids for the nutrients Caliper tracks. */
        val NUTRIENT_IDS: Map<Int, Nutrient> = mapOf(
            1008 to Nutrient.KCAL,
            1003 to Nutrient.PROTEIN,
            1004 to Nutrient.FAT,
            1258 to Nutrient.SATURATED_FAT,
            1257 to Nutrient.TRANS_FAT,
            1292 to Nutrient.MONOUNSATURATED_FAT,
            1293 to Nutrient.POLYUNSATURATED_FAT,
            1005 to Nutrient.CARBS,
            1079 to Nutrient.FIBER,
            2000 to Nutrient.SUGARS,
            1093 to Nutrient.SODIUM,
            1253 to Nutrient.CHOLESTEROL,
        )
    }
}
