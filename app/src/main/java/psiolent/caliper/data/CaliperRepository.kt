package psiolent.caliper.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import psiolent.caliper.domain.Nutrient
import psiolent.caliper.domain.Nutrients
import psiolent.caliper.domain.Serving
import psiolent.caliper.domain.resolveGrams
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** One day: its meals in order, and the day's summed totals. */
data class DayView(
    val date: LocalDate,
    val meals: List<MealWithEntries>,
    val totals: Nutrients,
)

/**
 * The single entry point to stored state. All arithmetic (unit resolution,
 * nutrient scaling, batch panels) lives here and in [psiolent.caliper.domain],
 * never in the agent.
 */
class CaliperRepository(private val db: CaliperDatabase) {

    private val foods = db.foods()
    private val recipes = db.recipes()
    private val batches = db.batches()
    private val meals = db.meals()
    private val logs = db.logs()
    private val profile = db.profile()
    private val sessions = db.sessions()
    private val messages = db.messages()

    // --- Reads -------------------------------------------------------------

    fun observeDay(date: LocalDate): Flow<DayView> =
        meals.observeBetween(startOfDay(date), startOfDay(date.plusDays(1))).map { groups ->
            val ordered = groups.ordered()
            DayView(date, ordered, ordered.flatMap { it.entries }.sumOfNutrients())
        }

    suspend fun day(date: LocalDate): DayView {
        val ordered = meals.between(startOfDay(date), startOfDay(date.plusDays(1))).ordered()
        return DayView(date, ordered, ordered.flatMap { it.entries }.sumOfNutrients())
    }

    suspend fun searchFoods(query: String): List<FoodEntity> = foods.search(query)
    suspend fun food(id: String): FoodEntity? = foods.byId(id)
    suspend fun recentBatches(limit: Int = 5): List<BatchEntity> = batches.recent(limit)
    suspend fun batch(id: String): BatchEntity? = batches.byId(id)
    suspend fun entry(id: String): LogEntryEntity? = logs.byId(id)

    fun observeFoods(): Flow<List<FoodEntity>> = foods.observeAll()
    fun observeRecipes(): Flow<List<RecipeEntity>> = recipes.observeAll()
    fun observeBatches(): Flow<List<BatchEntity>> = batches.observeAll()

    /** What is left of a batch, derived from what has been logged against it. */
    suspend fun remainingGrams(batchId: String): Double? {
        val batch = batches.byId(batchId) ?: return null
        val consumed = logs.forBatch(batchId).sumOf { it.grams }
        return (batch.totalGrams - consumed).coerceAtLeast(0.0)
    }

    suspend fun profileText(): String = profile.get()?.text.orEmpty()
    suspend fun saveProfile(text: String) = profile.upsert(ProfileEntity(text = text))

    // --- Foods -------------------------------------------------------------

    suspend fun saveFood(food: FoodEntity) = foods.upsert(food)
    suspend fun deleteFood(food: FoodEntity) = foods.delete(food)
    suspend fun deleteRecipe(recipe: RecipeEntity) = recipes.delete(recipe)
    suspend fun deleteBatch(batch: BatchEntity) = batches.delete(batch)

    // --- Logging -----------------------------------------------------------

    suspend fun logFood(foodId: String, amount: Double, unit: String, mealId: String, at: Instant? = null): LogEntryEntity {
        val food = foods.byId(foodId) ?: throw IllegalArgumentException("No food with id $foodId")
        return log(mealId, TargetType.FOOD, food.id, food.name, food.per100g, food.servings, amount, unit, at)
    }

    suspend fun logBatch(batchId: String, amount: Double, unit: String, mealId: String, at: Instant? = null): LogEntryEntity {
        val batch = batches.byId(batchId) ?: throw IllegalArgumentException("No batch with id $batchId")
        return log(mealId, TargetType.BATCH, batch.id, batch.name, batch.per100g, batch.servings, amount, unit, at)
    }

    private suspend fun log(
        mealId: String,
        type: TargetType,
        targetId: String,
        label: String,
        per100g: Nutrients,
        servings: List<Serving>,
        amount: Double,
        unit: String,
        at: Instant?,
    ): LogEntryEntity {
        val meal = meals.byId(mealId) ?: throw IllegalArgumentException("No meal with id $mealId")
        val grams = resolveGrams(amount, unit, servings)
            ?: throw IllegalArgumentException("Cannot express $amount $unit of \"$label\"")
        val entry = LogEntryEntity(
            id = newId(),
            mealId = mealId,
            targetType = type,
            targetId = targetId,
            label = label,
            amount = amount,
            unit = unit,
            grams = grams,
            nutrients = per100g.forGrams(grams),
            // An item with no time of its own belongs to when the meal happened.
            at = (at ?: Instant.ofEpochMilli(meal.at)).toEpochMilli(),
        )
        logs.upsert(entry)
        return entry
    }

    // --- Meals -------------------------------------------------------------

    /** The agent creates a meal; everything it logs for that meal points at it. */
    suspend fun openMeal(label: String, at: Instant): MealEntity {
        val meal = MealEntity(id = newId(), label = label, at = at.toEpochMilli())
        meals.upsert(meal)
        return meal
    }

    suspend fun meal(id: String): MealEntity? = meals.byId(id)

    /** Remove a meal and everything logged for it. */
    suspend fun deleteMeal(id: String) {
        logs.deleteForMeal(id)
        meals.delete(id)
    }

    /** Replace an entry's amount, unit, and time. The caller supplies complete values. */
    suspend fun reviseEntry(id: String, amount: Double, unit: String, at: Instant? = null) {
        val existing = logs.byId(id) ?: throw IllegalArgumentException("No log entry with id $id")
        val (per100g, servings) = targetPanel(existing)
            ?: throw IllegalArgumentException(
                "The ${existing.targetType.name.lowercase()} \"${existing.label}\" no longer exists; delete this entry and log a new one",
            )
        val grams = resolveGrams(amount, unit, servings)
            ?: throw IllegalArgumentException("Cannot express $amount $unit of \"${existing.label}\"")
        logs.upsert(
            existing.copy(
                amount = amount,
                unit = unit,
                grams = grams,
                nutrients = per100g.forGrams(grams),
                at = at?.toEpochMilli() ?: existing.at,
            ),
        )
    }

    suspend fun deleteEntry(id: String) = logs.delete(id)

    private suspend fun targetPanel(entry: LogEntryEntity): Pair<Nutrients, List<Serving>>? =
        when (entry.targetType) {
            TargetType.FOOD -> foods.byId(entry.targetId)?.let { it.per100g to it.servings }
            TargetType.BATCH -> batches.byId(entry.targetId)?.let { it.per100g to it.servings }
        }

    // --- Recipes and batches ----------------------------------------------

    suspend fun saveRecipe(
        name: String,
        ingredients: List<Ingredient>,
        yieldGrams: Double,
        servings: List<Serving> = emptyList(),
    ): RecipeEntity {
        computePanel(ingredients, yieldGrams) // fail fast on unresolvable ingredients
        val recipe = RecipeEntity(newId(), name, ingredients, yieldGrams, servings, Instant.now().toEpochMilli())
        recipes.upsert(recipe)
        return recipe
    }

    suspend fun updateRecipe(
        id: String,
        name: String? = null,
        ingredients: List<Ingredient>? = null,
        yieldGrams: Double? = null,
        servings: List<Serving>? = null,
    ): RecipeEntity {
        val existing = recipes.byId(id) ?: throw IllegalArgumentException("No recipe with id $id")
        val updated = existing.copy(
            name = name ?: existing.name,
            ingredients = ingredients ?: existing.ingredients,
            yieldGrams = yieldGrams ?: existing.yieldGrams,
            servings = servings ?: existing.servings,
        )
        computePanel(updated.ingredients, updated.yieldGrams)
        recipes.upsert(updated)
        return updated
    }

    /** Start a batch: snapshot the recipe's ingredients and compute its panel once. */
    suspend fun makeBatch(recipeId: String, name: String? = null, at: Instant = Instant.now()): BatchEntity {
        val recipe = recipes.byId(recipeId) ?: throw IllegalArgumentException("No recipe with id $recipeId")
        val (per100g, estimated) = computePanel(recipe.ingredients, recipe.yieldGrams)
        val batch = BatchEntity(
            id = newId(),
            recipeId = recipe.id,
            name = name ?: recipe.name,
            ingredients = recipe.ingredients,
            totalGrams = recipe.yieldGrams,
            per100g = per100g,
            estimated = estimated,
            servings = recipe.servings,
            createdAt = at.toEpochMilli(),
        )
        batches.upsert(batch)
        return batch
    }

    private suspend fun computePanel(ingredients: List<Ingredient>, yieldGrams: Double): Pair<Nutrients, Set<Nutrient>> {
        require(yieldGrams > 0) { "A recipe needs a yield greater than zero" }
        var totals = Nutrients.EMPTY
        var estimated = emptySet<Nutrient>()
        ingredients.forEach { ingredient ->
            val food = foods.byId(ingredient.foodId)
                ?: throw IllegalArgumentException("No food with id ${ingredient.foodId}")
            val grams = resolveGrams(ingredient.amount, ingredient.unit, food.servings)
                ?: throw IllegalArgumentException("Cannot express ${ingredient.amount} ${ingredient.unit} of \"${food.name}\"")
            totals += food.per100g.forGrams(grams)
            estimated = estimated + food.estimated
        }
        return totals.times(100.0 / yieldGrams) to estimated
    }

    // --- Sessions ----------------------------------------------------------

    fun observeSessions(): Flow<List<SessionEntity>> = sessions.observeAll()
    suspend fun saveSession(session: SessionEntity) = sessions.upsert(session)
    fun observeMessages(sessionId: String): Flow<List<MessageEntity>> = messages.observeBySession(sessionId)
    suspend fun messages(sessionId: String): List<MessageEntity> = messages.bySession(sessionId)
    suspend fun addMessage(message: MessageEntity) = messages.insert(message)

    // --- Backup ------------------------------------------------------------

    suspend fun snapshot(): Backup = Backup(
        profile = profile.get(),
        foods = foods.all(),
        recipes = recipes.all(),
        batches = batches.all(),
        meals = meals.all(),
        entries = logs.all(),
        sessions = sessions.all(),
        messages = messages.all(),
    )

    suspend fun restore(backup: Backup) {
        backup.profile?.let { profile.upsert(it) }
        backup.foods.forEach { foods.upsert(it) }
        backup.recipes.forEach { recipes.upsert(it) }
        backup.batches.forEach { batches.upsert(it) }
        backup.meals.forEach { meals.upsert(it) }
        backup.entries.forEach { logs.upsert(it) }
        backup.sessions.forEach { sessions.upsert(it) }
        backup.messages.forEach { messages.upsert(it) }
    }

    // --- Helpers -----------------------------------------------------------

    fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun newId(): String = UUID.randomUUID().toString()
}

private fun List<LogEntryEntity>.sumOfNutrients(): Nutrients =
    fold(Nutrients.EMPTY) { total, entry -> total + entry.nutrients }

/** Room returns relation rows in no particular order; a meal's items run in time. */
private fun List<MealWithEntries>.ordered(): List<MealWithEntries> =
    map { group -> group.copy(entries = group.entries.sortedBy { it.at }) }
