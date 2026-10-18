package psiolent.caliper.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import psiolent.caliper.data.BatchEntity
import psiolent.caliper.data.CaliperRepository
import psiolent.caliper.data.FoodEntity
import psiolent.caliper.data.FoodSource
import psiolent.caliper.data.Ingredient
import psiolent.caliper.data.LogEntryEntity
import psiolent.caliper.domain.Meal
import psiolent.caliper.domain.Nutrient
import psiolent.caliper.domain.Nutrients
import psiolent.caliper.domain.Serving
import psiolent.caliper.domain.nutrientByKey
import psiolent.caliper.integrations.UsdaClient
import psiolent.caliper.integrations.UsdaFood
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * The tools the model may call. The surface is deliberately small and every
 * unit conversion or nutrient calculation happens in the repository, never here.
 */
object ToolSchema {

    fun all(): List<JsonObject> = listOf(
        findFood, recentBatches, getLog, saveFood, logIntake, saveRecipe, updateRecipe, makeBatch, reviseEntry, updateProfile,
    )

    private val nutrientKeys = Nutrient.entries.joinToString(", ") { it.name.lowercase() }

    private val findFood = function(
        name = "find_food",
        description = "Look up a food. Searches your saved foods first, then USDA FoodData Central. " +
            "Returns candidates with ids you can log against.",
        properties = obj("query" to stringParam("Food name, e.g. 'rolled oats' or 'greek yogurt'.")),
        required = listOf("query"),
    )

    private val recentBatches = function(
        name = "recent_batches",
        description = "List batches you have made recently, newest first. Use this to resolve references " +
            "like 'those overnight oats', then log against the returned id.",
        properties = obj(),
    )

    private val getLog = function(
        name = "get_log",
        description = "Read what was logged on a given day, with that day's totals.",
        properties = obj("date" to stringParam("Date as YYYY-MM-DD.")),
        required = listOf("date"),
    )

    private val saveFood = function(
        name = "save_food",
        description = "Create or update a food and its nutrition panel. All values are per 100 g. " +
            "Nutrient keys: $nutrientKeys.",
        properties = obj(
            "name" to stringParam("Food name."),
            "per_100g" to objectParam("Map of nutrient key to value per 100 g."),
            "brand" to stringParam("Brand, if any."),
            "estimated" to arrayParam("Nutrient keys whose values are estimates.", stringParam("Nutrient key.")),
            "servings" to arrayParam(
                "Named portions and their weight in grams, e.g. a cup of oats.",
                obj("name" to stringParam("Portion name, e.g. cup."), "grams" to numberParam("Weight in grams.")),
            ),
            "source" to enumParam("Where the panel came from.", "LABEL", "USDA", "WEB", "ESTIMATE"),
            "source_ref" to stringParam("USDA fdcId or the source URL."),
            "fdc_id" to numberParam("A USDA fdcId from find_food. The app fetches the panel itself; do not also send per_100g."),
            "food_id" to stringParam("Existing food id to update; omit to create a new food."),
        ),
        required = listOf("name"),
    )

    private val logIntake = function(
        name = "log",
        description = "Record something eaten, against a saved food or batch id. Give an amount and a unit: " +
            "a mass unit (g, oz, lb), or a serving the food defines (cup, slice, tbsp).",
        properties = obj(
            "target_id" to stringParam("Id of the food or batch."),
            "amount" to numberParam("How much was eaten."),
            "unit" to stringParam("Unit, e.g. g, oz, cup, slice."),
            "meal" to enumParam("Optional meal label.", "BREAKFAST", "LUNCH", "DINNER", "SNACK"),
            "at" to stringParam("Optional ISO-8601 timestamp; defaults to now."),
        ),
        required = listOf("target_id", "amount", "unit"),
    )

    private val saveRecipe = function(
        name = "save_recipe",
        description = "Define a recipe: a named list of ingredients and the total weight it yields.",
        properties = obj(
            "name" to stringParam("Recipe name."),
            "ingredients" to arrayParam(
                "Ingredient lines.",
                obj(
                    "food_id" to stringParam("Id of a saved food."),
                    "amount" to numberParam("Amount of that food."),
                    "unit" to stringParam("Unit, e.g. g, cup, tbsp."),
                ),
            ),
            "yield_grams" to numberParam("Total weight the recipe makes, in grams."),
            "servings" to arrayParam(
                "Named portions of the finished dish.",
                obj("name" to stringParam("Portion name, e.g. serving."), "grams" to numberParam("Weight in grams.")),
            ),
        ),
        required = listOf("name", "ingredients", "yield_grams"),
    )

    private val updateRecipe = function(
        name = "update_recipe",
        description = "Change a recipe. Supply only the fields that change. This affects future batches only; " +
            "batches already made keep the ingredients they were made with.",
        properties = obj(
            "recipe_id" to stringParam("Id of the recipe to change."),
            "name" to stringParam("New name."),
            "ingredients" to arrayParam(
                "Replacement ingredient list.",
                obj(
                    "food_id" to stringParam("Id of a saved food."),
                    "amount" to numberParam("Amount of that food."),
                    "unit" to stringParam("Unit."),
                ),
            ),
            "yield_grams" to numberParam("New total weight, in grams."),
            "servings" to arrayParam(
                "Replacement portions of the finished dish.",
                obj("name" to stringParam("Portion name."), "grams" to numberParam("Weight in grams.")),
            ),
        ),
        required = listOf("recipe_id"),
    )

    private val makeBatch = function(
        name = "make_batch",
        description = "Record that you made a recipe. Snapshots the recipe's current ingredients so later edits " +
            "do not change it. Log portions of the batch afterwards with the returned id.",
        properties = obj(
            "recipe_id" to stringParam("Id of the recipe."),
            "name" to stringParam("Optional name for this batch."),
            "at" to stringParam("Optional ISO-8601 timestamp; defaults to now."),
        ),
        required = listOf("recipe_id"),
    )

    private val reviseEntry = function(
        name = "revise_entry",
        description = "Fix or delete something already logged.",
        properties = obj(
            "entry_id" to stringParam("Id of the log entry."),
            "amount" to numberParam("New amount."),
            "unit" to stringParam("New unit."),
            "meal" to enumParam("New meal label.", "BREAKFAST", "LUNCH", "DINNER", "SNACK"),
            "delete" to boolParam("Set true to remove the entry."),
        ),
        required = listOf("entry_id"),
    )

    private val updateProfile = function(
        name = "update_profile",
        description = "Replace the profile document: durable facts about the user such as goals, targets, " +
            "allergies, dietary constraints, and preferences. Write the whole document, keeping what still applies.",
        properties = obj("text" to stringParam("The full profile text.")),
        required = listOf("text"),
    )

    // --- schema helpers ----------------------------------------------------

    private fun function(
        name: String,
        description: String,
        properties: JsonObject,
        required: List<String> = emptyList(),
    ): JsonObject = buildJsonObject {
        put("type", "function")
        put(
            "function",
            buildJsonObject {
                put("name", name)
                put("description", description)
                put(
                    "parameters",
                    buildJsonObject {
                        put("type", "object")
                        put("properties", properties)
                        put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
                    },
                )
            },
        )
    }

    private fun obj(vararg props: Pair<String, JsonObject>): JsonObject = buildJsonObject {
        props.forEach { (key, value) -> put(key, value) }
    }

    private fun param(type: String, description: String): JsonObject = buildJsonObject {
        put("type", type)
        put("description", description)
    }

    private fun stringParam(description: String) = param("string", description)
    private fun numberParam(description: String) = param("number", description)
    private fun boolParam(description: String) = param("boolean", description)
    private fun objectParam(description: String) = param("object", description)

    private fun enumParam(description: String, vararg values: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
        put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
    }

    private fun arrayParam(description: String, items: JsonObject): JsonObject = buildJsonObject {
        put("type", "array")
        put("description", description)
        put("items", items)
    }
}

/** Dispatch a tool call to the repository and return a short result for the model. */
class ToolExecutor(
    private val repo: CaliperRepository,
    private val usda: UsdaClient,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun execute(name: String, arguments: String): String = try {
        val args = args(arguments)
        when (name) {
            "find_food" -> findFood(args)
            "recent_batches" -> recentBatches()
            "get_log" -> getLog(args)
            "save_food" -> saveFood(args)
            "log" -> logIntake(args)
            "save_recipe" -> saveRecipe(args)
            "update_recipe" -> updateRecipe(args)
            "make_batch" -> makeBatch(args)
            "revise_entry" -> reviseEntry(args)
            "update_profile" -> updateProfile(args)
            else -> "Unknown tool: $name"
        }
    } catch (e: Exception) {
        "Error: ${e.message}"
    }

    private suspend fun findFood(args: JsonObject): String {
        val query = args.string("query") ?: return "Error: query is required"
        val saved = repo.searchFoods(query)
        if (saved.isNotEmpty()) return saved.joinToString("\n") { describeFood(it) }

        val matches = usda.search(query)
        if (matches.isEmpty()) {
            return "Nothing saved matches \"$query\", and USDA has no match. Read the label from a photo, or look it up on the web."
        }
        return "Nothing saved yet. USDA candidates:\n" +
            matches.joinToString("\n") { describeUsda(it) } +
            "\nSave the right one with save_food using its fdc_id."
    }

    private suspend fun recentBatches(): String {
        val batches = repo.recentBatches(5)
        if (batches.isEmpty()) return "No batches yet."
        val remaining = batches.map { it to repo.remainingGrams(it.id) }
        return remaining.joinToString("\n") { (batch, left) -> describeBatch(batch, left) }
    }

    private suspend fun getLog(args: JsonObject): String {
        val date = args.string("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return "Error: date must be YYYY-MM-DD"
        val day = repo.day(date)
        if (day.entries.isEmpty()) return "Nothing logged on $date."
        val lines = day.entries.joinToString("\n") { describeEntry(it) }
        return "$date totals: ${describeNutrients(day.totals)}\n$lines"
    }

    private suspend fun saveFood(args: JsonObject): String {
        val name = args.string("name") ?: return "Error: name is required"
        val existing = args.string("food_id")?.let { repo.food(it) }
        val fdcId = args.number("fdc_id")?.toInt()
        val fromUsda = if (fdcId == null) null else usda.byId(fdcId) ?: return "Error: no USDA food with id $fdcId"
        val panel = fromUsda?.per100g
            ?: (args["per_100g"] as? JsonObject)?.let(::buildNutrients)
            ?: return "Error: provide per_100g, or an fdc_id to copy the panel from USDA"

        val food = FoodEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name,
            brand = args.string("brand") ?: fromUsda?.brand ?: existing?.brand,
            per100g = panel,
            estimated = args.stringSet("estimated"),
            servings = args.servings().ifEmpty { fromUsda?.servings ?: emptyList() },
            source = if (fromUsda != null) {
                FoodSource.USDA
            } else {
                args.string("source")?.let { raw -> FoodSource.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
                    ?: existing?.source ?: FoodSource.ESTIMATE
            },
            sourceRef = fromUsda?.let { "fdc:${it.fdcId}" } ?: args.string("source_ref") ?: existing?.sourceRef,
            createdAt = existing?.createdAt ?: Instant.now().toEpochMilli(),
        )
        repo.saveFood(food)
        return "Saved food id=${food.id} name=\"${food.name}\" source=${food.source.name.lowercase()}."
    }

    private suspend fun logIntake(args: JsonObject): String {
        val targetId = args.string("target_id") ?: return "Error: target_id is required"
        val amount = args.number("amount") ?: return "Error: amount is required"
        val unit = args.string("unit") ?: return "Error: unit is required"
        val meal = args.string("meal")?.let { raw -> Meal.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
        val at = args.string("at")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()

        val entry = when {
            repo.food(targetId) != null -> repo.logFood(targetId, amount, unit, meal, at)
            repo.batch(targetId) != null -> repo.logBatch(targetId, amount, unit, meal, at)
            else -> return "Error: no food or batch with id $targetId"
        }
        return "Logged. entry_id=${entry.id} ${describeEntry(entry)}"
    }

    private suspend fun saveRecipe(args: JsonObject): String {
        val name = args.string("name") ?: return "Error: name is required"
        val ingredients = args.ingredients() ?: return "Error: ingredients is required"
        val yield = args.number("yield_grams") ?: return "Error: yield_grams is required"
        val recipe = repo.saveRecipe(name, ingredients, yield, args.servings())
        return "Saved recipe id=${recipe.id} name=\"${recipe.name}\"."
    }

    private suspend fun updateRecipe(args: JsonObject): String {
        val id = args.string("recipe_id") ?: return "Error: recipe_id is required"
        val recipe = repo.updateRecipe(
            id = id,
            name = args.string("name"),
            ingredients = args.ingredients(),
            yieldGrams = args.number("yield_grams"),
            servings = if (args.containsKey("servings")) args.servings() else null,
        )
        return "Updated recipe id=${recipe.id} name=\"${recipe.name}\"."
    }

    private suspend fun makeBatch(args: JsonObject): String {
        val recipeId = args.string("recipe_id") ?: return "Error: recipe_id is required"
        val at = args.string("at")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()
        val batch = repo.makeBatch(recipeId, args.string("name"), at)
        return "Made batch id=${batch.id} name=\"${batch.name}\" total=${batch.totalGrams.toInt()} g."
    }

    private suspend fun reviseEntry(args: JsonObject): String {
        val id = args.string("entry_id") ?: return "Error: entry_id is required"
        if (args.boolean("delete") == true) {
            repo.deleteEntry(id)
            return "Deleted entry $id."
        }
        val existing = repo.entry(id) ?: return "Error: no entry with id $id"
        val meal = if (args.containsKey("meal")) {
            args.string("meal")?.let { raw -> Meal.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
        } else {
            existing.meal
        }
        repo.reviseEntry(
            id = id,
            amount = args.number("amount") ?: existing.amount,
            unit = args.string("unit") ?: existing.unit,
            meal = meal,
        )
        return "Revised entry $id."
    }

    private suspend fun updateProfile(args: JsonObject): String {
        val text = args.string("text") ?: return "Error: text is required"
        repo.saveProfile(text)
        return "Profile updated."
    }

    // --- parsing helpers ---------------------------------------------------

    private fun args(raw: String): JsonObject =
        if (raw.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(raw) as? JsonObject ?: JsonObject(emptyMap())

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()

    private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

    private fun JsonObject.stringSet(key: String): Set<Nutrient> =
        (this[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.let(::nutrientByKey) }
            ?.toSet()
            ?: emptySet()

    private fun JsonObject.servings(): List<Serving> =
        (this["servings"] as? JsonArray)?.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val name = row.string("name") ?: return@mapNotNull null
            val grams = row.number("grams") ?: return@mapNotNull null
            Serving(name, grams)
        } ?: emptyList()

    private fun JsonObject.ingredients(): List<Ingredient>? =
        (this["ingredients"] as? JsonArray)?.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val foodId = row.string("food_id") ?: return@mapNotNull null
            val amount = row.number("amount") ?: return@mapNotNull null
            val unit = row.string("unit") ?: return@mapNotNull null
            Ingredient(foodId, amount, unit)
        }

    private fun buildNutrients(panel: JsonObject): Nutrients = Nutrients(
        panel.entries.mapNotNull { (key, value) ->
            val nutrient = nutrientByKey(key) ?: return@mapNotNull null
            val amount = (value as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return@mapNotNull null
            nutrient to amount
        }.toMap(),
    )

    // --- display helpers ---------------------------------------------------

    private fun describeFood(food: FoodEntity): String = buildString {
        append("- id=${food.id} name=\"${food.name}\"")
        food.brand?.let { append(" brand=\"$it\"") }
        append(" per100g={${describeNutrients(food.per100g, includeUnits = false)}}")
        if (food.servings.isNotEmpty()) {
            append(" servings=[${food.servings.joinToString(", ") { "${it.name}=${it.grams}g" }}]")
        }
        append(" source=${food.source.name.lowercase()}")
        if (food.estimated.isNotEmpty()) {
            append(" estimated=[${food.estimated.joinToString(", ") { it.name.lowercase() }}]")
        }
    }

    private fun describeUsda(food: UsdaFood): String = buildString {
        append("- fdc_id=${food.fdcId} name=\"${food.name}\"")
        food.brand?.let { append(" brand=\"$it\"") }
        append(" per100g={${describeNutrients(food.per100g, includeUnits = false)}}")
        if (food.servings.isNotEmpty()) {
            append(" servings=[${food.servings.joinToString(", ") { "${it.name}=${it.grams}g" }}]")
        }
    }

    private fun describeBatch(batch: BatchEntity, remaining: Double?): String = buildString {
        append("- id=${batch.id} name=\"${batch.name}\"")
        append(" made=${LocalDate.ofInstant(Instant.ofEpochMilli(batch.createdAt), java.time.ZoneId.systemDefault())}")
        append(" total=${batch.totalGrams.toInt()}g")
        remaining?.let { append(" remaining=${it.toInt()}g") }
        append(" per100g={${describeNutrients(batch.per100g, includeUnits = false)}}")
    }

    private fun describeEntry(entry: LogEntryEntity): String = buildString {
        append("${entry.amount} ${entry.unit} of \"${entry.label}\" (${entry.grams.toInt()} g)")
        entry.meal?.let { append(" [${it.name.lowercase()}]") }
        append(" id=${entry.id}")
        entry.nutrients[Nutrient.KCAL]?.let { append(" ${it.toInt()} kcal") }
    }

    private fun describeNutrients(nutrients: Nutrients, includeUnits: Boolean = true): String =
        nutrients.amounts.entries.joinToString(", ") { (nutrient, value) ->
            if (includeUnits) "${nutrient.name.lowercase()}=${value.toInt()}${nutrient.unit}" else "${nutrient.name.lowercase()}=${value.toInt()}"
        }
}
