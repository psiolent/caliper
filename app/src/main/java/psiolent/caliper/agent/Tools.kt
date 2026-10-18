package psiolent.caliper.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
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
import psiolent.caliper.domain.Nutrient
import psiolent.caliper.domain.Nutrients
import psiolent.caliper.domain.Serving
import psiolent.caliper.domain.nutrientByKey
import psiolent.caliper.integrations.AgentLog
import psiolent.caliper.integrations.UsdaClient
import psiolent.caliper.integrations.UsdaFood
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * The tools the model may call. The surface is deliberately small and every
 * unit conversion or nutrient calculation happens in the repository, never here.
 */
object ToolSchema {

    fun all(): List<JsonObject> = listOf(
        findFood, recentBatches, getLog, saveFood, startMeal, logIntake, saveRecipe, updateRecipe, makeBatch, reviseEntry, updateProfile, updateMeal, deleteMeal,
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
            "per_100g" to objectParam(
                "Object of nutrient key to value per 100 g, e.g. {\"kcal\": 143, \"protein\": 12.6}. " +
                    "Omit it entirely when you pass an fdc_id — the app copies the panel from USDA.",
            ),
            "brand" to stringParam("Brand, if any."),
            "estimated" to arrayParam("Nutrient keys whose values are estimates.", stringParam("Nutrient key.")),
            "servings" to arrayParam(
                "Named portions and their weight in grams, e.g. a cup of oats. A map of name to grams is also accepted.",
                obj("name" to stringParam("Portion name, e.g. cup."), "grams" to numberParam("Weight in grams.")),
            ),
            "source" to enumParam("Where the panel came from.", "LABEL", "USDA", "WEB", "ESTIMATE"),
            "source_ref" to stringParam("USDA fdcId or the source URL."),
            "fdc_id" to numberParam("A USDA fdcId from find_food. The app fetches the panel itself; do not also send per_100g."),
            "food_id" to stringParam("Existing food id to update; omit to create a new food."),
        ),
        required = listOf("name"),
    )

    private val startMeal = function(
        name = "start_meal",
        description = "Begin a meal before logging what was eaten for it. Everything the user tells you " +
            "they ate in one session belongs to one meal unless they say otherwise, so start one meal " +
            "and log its items against that id. Name it with the user's own word if they used one " +
            "(\"lunch\"), otherwise a short description of what was eaten (\"Toast and eggs\"). Use the " +
            "current time unless the user says when.",
        properties = obj(
            "label" to stringParam("Short name for the meal."),
            "at" to stringParam("When the meal happened, if the user said. A time like 07:00 means today."),
        ),
        required = listOf("label"),
    )

    private val updateMeal = function(
        name = "update_meal",
        description = "Change an existing meal's name or when it happened — for example when the " +
            "user says the time was wrong. Supply only what changes.",
        properties = obj(
            "meal_id" to stringParam("The meal to change."),
            "label" to stringParam("New name."),
            "at" to stringParam("New time, if the original was wrong."),
        ),
        required = listOf("meal_id"),
    )

    private val deleteMeal = function(
        name = "delete_meal",
        description = "Remove a meal and every item logged for it. Use this when the user asks to " +
            "delete a meal; deleting its items one by one leaves the empty meal behind.",
        properties = obj("meal_id" to stringParam("The meal to remove.")),
        required = listOf("meal_id"),
    )

    private val logIntake = function(
        name = "log",
        description = "Record something eaten, against a saved food or batch id. Give an amount and a unit: " +
            "a mass unit (g, oz, lb), or a serving the food defines (cup, slice, tbsp).",
        properties = obj(
            "meal_id" to stringParam("The meal to add this item to, from start_meal."),
            "target_id" to stringParam("Id of the food or batch."),
            "amount" to numberParam("How much was eaten."),
            "unit" to stringParam("Unit, e.g. g, oz, cup, slice."),
        ),
        required = listOf("meal_id", "target_id", "amount", "unit"),
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
    private val agentLog: AgentLog,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        const val WHEN_MESSAGE =
            "'at' could not be read as a time. Use a local date-time like 2026-10-18T07:00, or a time like 07:00."
        const val SERVINGS_ERROR =
            "Error: servings must be a list of {\"name\": \"cup\", \"grams\": 80} objects, or a map of name to grams — not a string."
        const val INGREDIENTS_ERROR =
            "Error: ingredients must be a list of {\"food_id\": \"...\", \"amount\": 1, \"unit\": \"cup\"} objects."
    }

    private fun clock(epochMillis: Long): String =
        TIME.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    suspend fun execute(name: String, arguments: String): String = try {
        val args = args(arguments)
        agentLog.append("$name $arguments")
        val result = when (name) {
            "find_food" -> findFood(args)
            "recent_batches" -> recentBatches()
            "get_log" -> getLog(args)
            "save_food" -> saveFood(args)
            "start_meal" -> startMeal(args)
            "update_meal" -> updateMeal(args)
            "delete_meal" -> deleteMeal(args)
            "log" -> logIntake(args)
            "save_recipe" -> saveRecipe(args)
            "update_recipe" -> updateRecipe(args)
            "make_batch" -> makeBatch(args)
            "revise_entry" -> reviseEntry(args)
            "update_profile" -> updateProfile(args)
            else -> "Unknown tool: $name"
        }
        agentLog.append("  \u2192 ${result.replace('\n', ' ').take(300)}")
        result
    } catch (e: Exception) {
        agentLog.append("  \u2192 Error: ${e.message}")
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
        if (day.meals.isEmpty()) return "Nothing logged on $date."
        val meals = day.meals.joinToString("\n") { group ->
            val items = group.entries.joinToString("\n  ") { describeEntry(it) }
            "${clock(group.meal.at)} \"${group.meal.label}\" meal_id=${group.meal.id}\n  $items"
        }
        return "$date totals: ${describeNutrients(day.totals)}\n$meals"
    }

    private suspend fun saveFood(args: JsonObject): String {
        val name = args.string("name") ?: return "Error: name is required"
        val fdcId = args.number("fdc_id")?.toInt()
        val fromUsda = if (fdcId == null) null else usda.byId(fdcId) ?: return "Error: no USDA food with id $fdcId"
        val brand = args.string("brand") ?: fromUsda?.brand
        // Reuse a row for the same food rather than adding another: a long turn can save
        // the same food more than once, and duplicates then clutter every search.
        val existing = args.string("food_id")?.let { repo.food(it) }
            ?: repo.foodByNameAndBrand(name, brand)
        val per100gRaw = args["per_100g"]
        val per100g = per100gRaw as? JsonObject
        // Only complain when the value is actually needed; with an fdc_id it is redundant.
        if (fromUsda == null && per100gRaw != null && per100g == null) {
            return "Error: per_100g must be an object of nutrient keys to values ({\"kcal\": 143}), not a string."
        }
        val panel = fromUsda?.per100g ?: per100g?.let(::buildNutrients)
            ?: return "Error: provide per_100g, or an fdc_id to copy the panel from USDA"
        if (panel.amounts.isEmpty()) {
            val keys = per100g?.keys?.joinToString(", ").orEmpty()
            return "Error: no recognised nutrient keys in per_100g (got: $keys). " +
                "Use keys like kcal, protein, fat, saturated_fat, carbs, fiber, sugars, sodium, cholesterol."
        }
        val servings = args.servings() ?: return SERVINGS_ERROR

        val food = FoodEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name,
            brand = args.string("brand") ?: fromUsda?.brand ?: existing?.brand,
            per100g = panel,
            estimated = args.stringSet("estimated"),
            servings = servings.ifEmpty { fromUsda?.servings ?: emptyList() },
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
        val ignored = unknownNutrientKeys(per100g ?: JsonObject(emptyMap()))
        val note = if (ignored.isEmpty()) "" else " (ignored unrecognised keys: ${ignored.joinToString(", ")})"
        return "Saved food id=${food.id} name=\"${food.name}\" source=${food.source.name.lowercase()}.$note"
    }

    private suspend fun logIntake(args: JsonObject): String {
        val mealId = args.string("meal_id")
            ?: return "Error: meal_id is required — call start_meal first if this is a new meal."
        val targetId = args.string("target_id") ?: return "Error: target_id is required"
        val amount = args.number("amount") ?: return "Error: amount is required"
        val unit = args.string("unit") ?: return "Error: unit is required"

        val entry = when {
            repo.food(targetId) != null -> repo.logFood(targetId, amount, unit, mealId)
            repo.batch(targetId) != null -> repo.logBatch(targetId, amount, unit, mealId)
            else -> return "Error: no food or batch with id $targetId"
        }
        val mealLabel = repo.meal(mealId)?.label.orEmpty()
        return "Logged to \"$mealLabel\". entry_id=${entry.id} ${describeEntry(entry)}"
    }

    private suspend fun startMeal(args: JsonObject): String {
        val label = args.string("label")?.trim()?.takeIf { it.isNotEmpty() }
            ?: return "Error: label is required — the user's own word, or a short description of what was eaten."
        val at = whenArg(args.string("at")) ?: Instant.now()
        val meal = repo.openMeal(label, at)
        return "Started meal id=${meal.id} name=\"${meal.label}\". Log its items with this meal_id."
    }

    private suspend fun saveRecipe(args: JsonObject): String {
        val name = args.string("name") ?: return "Error: name is required"
        val ingredients = args.ingredients() ?: return INGREDIENTS_ERROR
        val servings = args.servings() ?: return SERVINGS_ERROR
        val yield = args.number("yield_grams") ?: return "Error: yield_grams is required"
        val recipe = repo.saveRecipe(name, ingredients, yield, servings)
        return "Saved recipe id=${recipe.id} name=\"${recipe.name}\"."
    }

    private suspend fun updateRecipe(args: JsonObject): String {
        val id = args.string("recipe_id") ?: return "Error: recipe_id is required"
        val ingredients = if (args.containsKey("ingredients")) args.ingredients() ?: return INGREDIENTS_ERROR else null
        val servings = if (args.containsKey("servings")) args.servings() ?: return SERVINGS_ERROR else null
        val recipe = repo.updateRecipe(
            id = id,
            name = args.string("name"),
            ingredients = ingredients,
            yieldGrams = args.number("yield_grams"),
            servings = servings,
        )
        return "Updated recipe id=${recipe.id} name=\"${recipe.name}\"."
    }

    private suspend fun makeBatch(args: JsonObject): String {
        val recipeId = args.string("recipe_id") ?: return "Error: recipe_id is required"
        val at = whenArg(args.string("at")) ?: Instant.now()
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
        repo.reviseEntry(
            id = id,
            amount = args.number("amount") ?: existing.amount,
            unit = args.string("unit") ?: existing.unit,
        )
        return "Revised entry $id."
    }

    private suspend fun updateMeal(args: JsonObject): String {
        val mealId = args.string("meal_id") ?: return "Error: meal_id is required"
        val meal = repo.updateMeal(mealId, args.string("label"), whenArg(args.string("at")))
        return "Meal is now \"${meal.label}\" at ${clock(meal.at)}."
    }

    private suspend fun deleteMeal(args: JsonObject): String {
        val mealId = args.string("meal_id") ?: return "Error: meal_id is required"
        val meal = repo.meal(mealId) ?: return "Error: no meal with id $mealId"
        repo.deleteMeal(mealId)
        return "Deleted meal \"${meal.label}\" and its items."
    }

    private suspend fun updateProfile(args: JsonObject): String {
        val text = args.string("text") ?: return "Error: text is required"
        repo.saveProfile(text)
        return "Profile updated."
    }

    // --- parsing helpers ---------------------------------------------------

    private fun args(raw: String): JsonObject {
        if (raw.isBlank()) return JsonObject(emptyMap())
        val parsed = runCatching { json.parseToJsonElement(raw) }.getOrNull()
            ?: throw IllegalArgumentException("tool arguments were not valid JSON: ${raw.take(120)}")
        return parsed as? JsonObject
            ?: throw IllegalArgumentException("tool arguments must be a JSON object, but were ${parsed.typeName()}")
    }

    /**
     * Accept the shapes a model actually produces for a time: a full instant, a
     * local date-time, a bare date, or a bare time today. Returns null rather than
     * quietly substituting "now", so a bad time is surfaced instead of hidden.
     */
    private fun parseWhen(raw: String): Instant? = parseWhen(raw, ZoneId.systemDefault())

    /**
     * A time the user gave. A future one is a mistake — nobody has eaten yet — so it
     * comes back as an error rather than being stored as if it were real.
     */
    private fun whenArg(raw: String?): Instant? {
        if (raw == null) return null
        val at = parseWhen(raw) ?: throw IllegalArgumentException(WHEN_MESSAGE)
        if (isFuture(at, Instant.now())) {
            throw IllegalArgumentException(
                "\"$raw\" is in the future, so it cannot be when something was eaten. " +
                    "Omit `at` to use the current time.",
            )
        }
        return at
    }

    private fun buildNutrients(panel: JsonObject): Nutrients = Nutrients(
        panel.entries.mapNotNull { (key, value) ->
            val nutrient = nutrientByKey(key) ?: return@mapNotNull null
            val amount = (value as? JsonPrimitive)?.amountOrNull() ?: return@mapNotNull null
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
        append(" id=${entry.id}")
        entry.nutrients[Nutrient.KCAL]?.let { append(" ${it.toInt()} kcal") }
    }

    private fun describeNutrients(nutrients: Nutrients, includeUnits: Boolean = true): String =
        nutrients.amounts.entries.joinToString(", ") { (nutrient, value) ->
            if (includeUnits) "${nutrient.name.lowercase()}=${value.toInt()}${nutrient.unit}" else "${nutrient.name.lowercase()}=${value.toInt()}"
        }
}

/**
 * Read a time a model sent. A bare time is taken as today. Null means it could not
 * be read at all, which the caller must report rather than papering over with "now".
 */
internal fun parseWhen(raw: String, zone: ZoneId, today: LocalDate = LocalDate.now(zone)): Instant? {
    val text = raw.trim()
    runCatching { return Instant.parse(text) }
    runCatching { return LocalDateTime.parse(text).atZone(zone).toInstant() }
    runCatching { return LocalDate.parse(text).atStartOfDay(zone).toInstant() }
    runCatching { return LocalTime.parse(text).atDate(today).atZone(zone).toInstant() }
    return null
}

private val NUMBER = Regex("""-?\d+(\.\d+)?""")

/** A little slack, so clock skew does not reject something logged a moment ago. */
internal fun isFuture(at: Instant, now: Instant, toleranceSeconds: Long = 300): Boolean =
    at.isAfter(now.plusSeconds(toleranceSeconds))

internal fun JsonObject.string(key: String): String? {
    val raw = this[key] ?: return null
    return (raw as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: throw IllegalArgumentException("\"$key\" must be text, but was ${raw.typeName()}")
}

internal fun JsonObject.number(key: String): Double? {
    val raw = this[key] ?: return null
    return (raw as? JsonPrimitive)?.content?.toDoubleOrNull()
        ?: throw IllegalArgumentException("\"$key\" must be a number, but was ${raw.typeName()}")
}

internal fun JsonObject.boolean(key: String): Boolean? {
    val raw = this[key] ?: return null
    return (raw as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
        ?: throw IllegalArgumentException("\"$key\" must be true or false, but was ${raw.typeName()}")
}

/** Unknown nutrient names are reported rather than dropped; they are usually a typo. */
internal fun JsonObject.stringSet(key: String): Set<Nutrient> {
    val raw = this[key] ?: return emptySet()
    val rows = raw as? JsonArray
        ?: throw IllegalArgumentException("\"$key\" must be a list of nutrient names, but was ${raw.typeName()}")
    return rows.map { element ->
        val text = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw IllegalArgumentException("\"$key\" must be a list of nutrient names")
        nutrientByKey(text)
            ?: throw IllegalArgumentException("\"$key\" names a nutrient Caliper does not track: \"$text\"")
    }.toSet()
}

/** Nutrient keys in a panel the app does not recognise. */
internal fun unknownNutrientKeys(panel: JsonObject): List<String> =
    panel.keys.filter { nutrientByKey(it) == null }

private fun JsonElement.typeName(): String = when (this) {
    is JsonObject -> "an object"
    is JsonArray -> "a list"
    is JsonNull -> "null"
    else -> if ((this as JsonPrimitive).isString) "text" else "a number or boolean"
}

/** Accept a plain number, or a string like "144 kcal" or "15 g". */
internal fun JsonPrimitive.amountOrNull(): Double? {
    val raw = content.trim()
    return raw.toDoubleOrNull() ?: NUMBER.find(raw)?.value?.toDoubleOrNull()
}

/**
 * The documented shape is a list of {name, grams}; models also send the equivalent
 * {name: grams} map. Anything else — including a JSON string of either — is a contract
 * violation, returned as null so the caller can say so rather than drop it silently.
 */
internal fun JsonObject.servings(): List<Serving>? = when (val raw = this["servings"]) {
    null -> emptyList()
    is JsonArray -> raw.map { element ->
        val row = element as? JsonObject ?: return null
        val name = row.string("name") ?: return null
        val grams = row.number("grams") ?: return null
        Serving(name, grams)
    }
    is JsonObject -> raw.entries.map { (name, grams) ->
        Serving(name, (grams as? JsonPrimitive)?.amountOrNull() ?: return null)
    }
    else -> null
}

/** Null when absent or malformed; callers that allow absence check for the key first. */
internal fun JsonObject.ingredients(): List<Ingredient>? {
    val rows = this["ingredients"] as? JsonArray ?: return null
    return rows.map { element ->
        val row = element as? JsonObject ?: return null
        val foodId = row.string("food_id") ?: return null
        val amount = row.number("amount") ?: return null
        val unit = row.string("unit") ?: return null
        Ingredient(foodId, amount, unit)
    }
}
