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
import psiolent.caliper.data.CaliperRepository
import psiolent.caliper.data.FoodEntity
import psiolent.caliper.data.FoodSource
import psiolent.caliper.data.Ingredient
import psiolent.caliper.data.LogEntryEntity
import psiolent.caliper.data.RecipeEntity
import psiolent.caliper.data.portions
import psiolent.caliper.domain.MatchStrength
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
import java.util.Locale
import java.util.UUID

/**
 * The tools the model may call. The surface is deliberately small and every
 * unit conversion or nutrient calculation happens in the repository, never here.
 */
object ToolSchema {

    fun all(): List<JsonObject> = listOf(
        findFood, recipes, getLog, saveFood, startMeal, logIntake, saveRecipe, updateRecipe, reviseEntry, updateProfile, updateMeal, deleteMeal,
    )

    private val nutrientKeys = Nutrient.entries.joinToString(", ") { it.name.lowercase() }

    private val findFood = function(
        name = "find_food",
        description = "Look up a food. Searches your saved foods first, then USDA FoodData Central. " +
            "Returns candidates with ids you can log against.",
        properties = obj("query" to stringParam("Food name, e.g. 'rolled oats' or 'greek yogurt'.")),
        required = listOf("query"),
    )

    private val recipes = function(
        name = "recipes",
        description = "List the recipes you have defined, by name, with the ids needed to log against one " +
            "or to change it. Recipes are not otherwise readable, so use this rather than defining one " +
            "again. Each line lists the portions it can be logged in.",
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
        description = "Record something eaten, against a saved food or recipe id. Give an amount and a unit: " +
            "a mass unit (g, oz, lb), a portion the food or recipe defines, or `recipe` to mean the whole " +
            "dish, so half of it is 0.5 recipe. A unit that cannot be resolved comes back as an error " +
            "listing the ones that would work.",
        properties = obj(
            "meal_id" to stringParam("The meal to add this item to, from start_meal."),
            "target_id" to stringParam("Id of the food or recipe."),
            "amount" to numberParam("How much was eaten."),
            "unit" to stringParam("Unit, e.g. g, oz, cup, slice, recipe."),
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
                    // Named as in log(), because the model copies this shape from there.
                    // Still a food: recipes do not nest.
                    "target_id" to stringParam("Id of a saved food. An ingredient is always a food, never a recipe."),
                    "amount" to numberParam("Amount of that food."),
                    "unit" to stringParam("Unit, e.g. g, cup, tbsp."),
                ),
            ),
            "yield_grams" to numberParam("Total weight the recipe makes, in grams."),
            "servings" to arrayParam(
                "Named portions of the finished dish. A portion named for a volume (\"cup\", \"tbsp\") is " +
                    "a volume-to-weight ratio for the dish, and makes any volume unit loggable; without " +
                    "one, volume units are refused.",
                obj("name" to stringParam("Portion name, e.g. serving, or cup for a volume."), "grams" to numberParam("Weight in grams.")),
            ),
        ),
        required = listOf("name", "ingredients", "yield_grams"),
    )

    private val updateRecipe = function(
        name = "update_recipe",
        description = "Change a recipe. Supply only the fields that change. Anything already logged keeps " +
            "the panel it was logged with; this changes what is logged from now on.",
        properties = obj(
            "recipe_id" to stringParam("Id of the recipe to change."),
            "name" to stringParam("New name."),
            "ingredients" to arrayParam(
                "Replacement ingredient list.",
                obj(
                    "target_id" to stringParam("Id of a saved food. An ingredient is always a food, never a recipe."),
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

        /** Enough to choose from without drowning the turn in a long library. */
        const val MAX_RECIPES = 10
        const val WHEN_MESSAGE =
            "'at' must be a time of day like 07:00, or a date and time like 2026-10-18T07:00. " +
                "A bare date has no time of day — omit 'at' to use the current time."
        const val SERVINGS_ERROR =
            "Error: servings must be a list of {\"name\": \"cup\", \"grams\": 80} objects, or a map of name to grams — not a string."
        const val INGREDIENTS_ERROR =
            "Error: ingredients must be a list of {\"target_id\": \"...\", \"amount\": 1, \"unit\": \"cup\"} " +
                "objects — not a string."
    }

    private fun clock(epochMillis: Long): String =
        TIME.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    suspend fun execute(name: String, arguments: String): String = try {
        val args = args(arguments)
        agentLog.append("$name $arguments")
        val result = when (name) {
            "find_food" -> findFood(args)
            "recipes" -> listRecipes()
            "get_log" -> getLog(args)
            "save_food" -> saveFood(args)
            "start_meal" -> startMeal(args)
            "update_meal" -> updateMeal(args)
            "delete_meal" -> deleteMeal(args)
            "log" -> logIntake(args)
            "save_recipe" -> saveRecipe(args)
            "update_recipe" -> updateRecipe(args)
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
        val query = args.string("query")?.trim() ?: return "Error: query is required"
        if (query.isBlank()) return "Error: query must name the food to look for."

        // Confident matches are returned as matches. A close one is only a suggestion, and
        // saying so is the difference between the agent asking and the agent logging
        // something that merely looked right.
        val found = repo.searchFoods(query)
        val confident = found
            .filter { it.strength != MatchStrength.CLOSE }
            .mapNotNull { repo.food(it.target.id) }
        val close = found
            .filter { it.strength == MatchStrength.CLOSE }
            .mapNotNull { repo.food(it.target.id) }

        // USDA is the fallback for a genuine miss, not for a near-miss: searching it while
        // a saved food is clearly similar spends a request to offer the agent alternatives
        // to something it already has.
        val usdaLines = if (confident.isEmpty() && close.isEmpty()) {
            usda.search(query).map { describeUsda(it) }
        } else {
            null
        }

        return foodLookupReply(
            query = query,
            librarySize = if (confident.isEmpty()) repo.foodCount() else 0,
            confident = confident.map { describeFood(it) },
            close = close.map { describeFood(it) },
            usda = usdaLines,
        )
    }

    /** Recipes by name, with the ids needed to log against one or to change it. */
    private suspend fun listRecipes(): String {
        val recipes = repo.allRecipes()
        if (recipes.isEmpty()) return "No recipes yet."
        val shown = recipes.take(MAX_RECIPES)
        val lines = shown.map { describeRecipe(it) }.joinToString("\n")
        return if (recipes.size > shown.size) {
            "$lines\n(${recipes.size - shown.size} more not shown.)"
        } else {
            lines
        }
    }

    private suspend fun describeRecipe(recipe: RecipeEntity): String {
        val ingredients = recipe.ingredients
            .map { ingredient ->
                val name = repo.food(ingredient.foodId)?.name ?: "(missing food)"
                "${formatAmount(ingredient.amount)} ${ingredient.unit} $name"
            }
            .joinToString(", ")
        // The portions are the units this recipe can be logged in, the whole included.
        val portions = recipe.portions.joinToString(", ") { "${it.name}=${formatMeasure(it.grams)}g" }
        return "- id=${recipe.id} name=\"${recipe.name}\" makes=${formatMeasure(recipe.yieldGrams)} g " +
            "portions=[$portions] ingredients=[$ingredients]"
    }

    /**
     * A wrong recipe id is worth more than a refusal. There are few recipes and no other
     * way to list them, so say which exist. Without that the only recovery is to define
     * the recipe again, which leaves a duplicate behind.
     */
    private suspend fun unknownRecipe(id: String): String {
        val known = repo.allRecipes()
        if (known.isEmpty()) return "Error: no recipe with id $id, and none has been saved yet."
        return "Error: no recipe with id $id. Saved recipes: " +
            known.joinToString("; ") { "${it.name} (id=${it.id})" }
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

        val recipe = repo.recipe(targetId)
        val entry = when {
            repo.food(targetId) != null -> repo.logFood(targetId, amount, unit, mealId)
            recipe != null -> {
                // A recipe whose food has been deleted still exists but cannot be worked
                // out. Say which recipe and what fixes it, rather than failing on a food
                // id the agent never mentioned.
                if (repo.recipePanel(recipe) == null) {
                    return "Error: the recipe \"${recipe.name}\" cannot be worked out because a food it " +
                        "uses no longer exists. Replace that ingredient with update_recipe, then log it."
                }
                repo.logRecipe(targetId, amount, unit, mealId)
            }
            else -> return "Error: no food or recipe with id $targetId. " +
                "Use find_food for a food, or recipes for a recipe you defined."
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
        if (repo.recipe(id) == null) return unknownRecipe(id)
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
            append(" servings=[${food.servings.joinToString(", ") { "${it.name}=${formatMeasure(it.grams)}g" }}]")
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
            append(" servings=[${food.servings.joinToString(", ") { "${it.name}=${formatMeasure(it.grams)}g" }}]")
        }
    }
}

// Paper descriptions are pure and top-level so their formatting can be tested without a
// repository or a network: the fractions they render are what the model relies on.

internal fun describeEntry(entry: LogEntryEntity): String = buildString {
    append("${formatAmount(entry.amount)} ${entry.unit} of \"${entry.label}\" (${formatMeasure(entry.grams)} g)")
    append(" id=${entry.id}")
    entry.nutrients[Nutrient.KCAL]?.let { append(" ${formatMeasure(it)} kcal") }
}

internal fun describeNutrients(nutrients: Nutrients, includeUnits: Boolean = true): String =
    nutrients.amounts.entries.joinToString(", ") { (nutrient, value) ->
        if (includeUnits) "${nutrient.name.lowercase()}=${formatMeasure(value)}${nutrient.unit}"
        else "${nutrient.name.lowercase()}=${formatMeasure(value)}"
    }

/**
 * Read a time a model sent. A bare time is taken as today. Null means it could not
 * be read at all, which the caller must report rather than papering over with "now".
 *
 * A bare *date* is deliberately not read: "2026-10-18" has no time of day, and taking
 * it as midnight stamped a meal at 00:00 that was eaten in the afternoon. A date alone
 * is a missing time, so it comes back as unreadable and the agent supplies one.
 */
internal fun parseWhen(raw: String, zone: ZoneId, today: LocalDate = LocalDate.now(zone)): Instant? {
    val text = raw.trim()
    runCatching { return Instant.parse(text) }
    runCatching { return LocalDateTime.parse(text).atZone(zone).toInstant() }
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

/**
 * Null when absent or malformed; callers that allow absence check for the key first.
 *
 * The tool calls this field `target_id`, matching `log`, but the stored ingredient keeps
 * `foodId`: that is the persisted shape, and it is genuinely a food.
 */
internal fun JsonObject.ingredients(): List<Ingredient>? {
    val rows = this["ingredients"] as? JsonArray ?: return null
    return rows.map { element ->
        val row = element as? JsonObject ?: return null
        val foodId = row.string("target_id") ?: return null
        val amount = row.number("amount") ?: return null
        val unit = row.string("unit") ?: return null
        Ingredient(foodId, amount, unit)
    }
}

/**
 * What the model is told when nothing saved matched.
 *
 * Saying "nothing saved yet" on a plain search miss once led the agent to tell the user
 * their library was empty when it held 27 foods. A miss is a miss; the size of the
 * library is a separate fact, and the only one that can make "empty" true.
 */
internal fun noSavedMatchLine(query: String, librarySize: Int): String = when (librarySize) {
    0 -> "No saved food matches \"$query\"; nothing is saved yet."
    1 -> "No saved food matches \"$query\". The library has 1 food."
    else -> "No saved food matches \"$query\". The library has $librarySize foods."
}

/** The heading that keeps a near-miss from being read as a match. */
internal const val CLOSE_MATCH_HEADING =
    "Closest saved foods — similar, not matches. Ask which one; do not log one."

/**
 * The whole find_food answer, assembled from lines already rendered.
 *
 * Kept pure so the rule that matters can be tested: a confident match is returned alone,
 * and anything less is labelled as a suggestion.
 *
 * [usda] is null when USDA was not consulted, which happens when a saved food is already
 * similar — there is no point offering alternatives to something the user has.
 */
internal fun foodLookupReply(
    query: String,
    librarySize: Int,
    confident: List<String>,
    close: List<String>,
    usda: List<String>?,
): String {
    if (confident.isNotEmpty()) return confident.joinToString("\n")

    return buildString {
        appendLine(noSavedMatchLine(query, librarySize))
        if (close.isNotEmpty()) {
            appendLine()
            appendLine(CLOSE_MATCH_HEADING)
            close.forEach { appendLine(it) }
        }
        if (usda != null) {
            appendLine()
            if (usda.isEmpty()) {
                append("USDA has no match either. Read the label from a photo, or look it up on the web.")
            } else {
                appendLine("USDA candidates:")
                usda.forEach { appendLine(it) }
                append("Save the right one with save_food using its fdc_id.")
            }
        }
    }.trimEnd()
}

/**
 * An amount in whatever unit: exact enough that 0.25 cup does not become 0.
 *
 * Locale.ROOT because the reader is the model, not the person: a comma decimal separator
 * would make the number unreadable as a number, and the app must not depend on the phone's
 * language for that.
 */
internal fun formatAmount(value: Double): String = trimZeroes(String.format(Locale.ROOT, "%.2f", value))

/** Grams and nutrient values: one decimal is as much as is meant. */
internal fun formatMeasure(value: Double): String = trimZeroes(String.format(Locale.ROOT, "%.1f", value))

/** "0.50" is "0.5", "80.00" is "80". */
private fun trimZeroes(formatted: String): String =
    if (!formatted.contains('.')) formatted else formatted.trimEnd('0').trimEnd('.')
