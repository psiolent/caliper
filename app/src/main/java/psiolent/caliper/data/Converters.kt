package psiolent.caliper.data

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.json.Json
import psiolent.caliper.domain.Nutrient
import psiolent.caliper.domain.Nutrients
import psiolent.caliper.domain.Serving

/**
 * Value objects (a nutrient panel, a food's servings, a recipe's ingredient list)
 * are always read and written with their parent row and never queried on their
 * own, so they are stored as JSON in a single column rather than as join tables.
 */
class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun nutrientsToString(value: Nutrients): String =
        json.encodeToString(Nutrients.serializer(), value)

    @TypeConverter
    fun stringToNutrients(value: String): Nutrients =
        json.decodeFromString(Nutrients.serializer(), value)

    @TypeConverter
    fun servingsToString(value: List<Serving>): String =
        json.encodeToString(ListSerializer(Serving.serializer()), value)

    @TypeConverter
    fun stringToServings(value: String): List<Serving> =
        json.decodeFromString(ListSerializer(Serving.serializer()), value)

    @TypeConverter
    fun ingredientsToString(value: List<Ingredient>): String =
        json.encodeToString(ListSerializer(Ingredient.serializer()), value)

    @TypeConverter
    fun stringToIngredients(value: String): List<Ingredient> =
        json.decodeFromString(ListSerializer(Ingredient.serializer()), value)

    @TypeConverter
    fun estimatedToString(value: Set<Nutrient>): String =
        json.encodeToString(SetSerializer(Nutrient.serializer()), value)

    @TypeConverter
    fun stringToEstimated(value: String): Set<Nutrient> =
        json.decodeFromString(SetSerializer(Nutrient.serializer()), value)
}
