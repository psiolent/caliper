package psiolent.caliper.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.serialization.Serializable
import psiolent.caliper.domain.Nutrient
import psiolent.caliper.domain.Nutrients
import psiolent.caliper.domain.Serving

/** Where a food's nutrient panel came from. */
@Serializable
enum class FoodSource { LABEL, USDA, WEB, ESTIMATE }

/**
 * A line of a recipe: a food, an amount, and the unit the amount is expressed in.
 * Units are resolved against the food's servings when the recipe is computed, never
 * stored as grams here.
 */
@Serializable
data class Ingredient(val foodId: String, val amount: Double, val unit: String)

@Serializable
@Entity(tableName = "foods")
data class FoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String? = null,
    /** Nutrients per 100 g. */
    val per100g: Nutrients,
    /** Nutrients in [per100g] the model guessed rather than looked up. */
    val estimated: Set<Nutrient> = emptySet(),
    val servings: List<Serving> = emptyList(),
    val source: FoodSource = FoodSource.ESTIMATE,
    /** The FDC id, URL, or other reference the panel came from, if any. */
    val sourceRef: String? = null,
    val createdAt: Long,
)

/** A named formulation. Its nutrients are computed from its ingredients. */
@Serializable
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ingredients: List<Ingredient>,
    val yieldGrams: Double,
    /** Named portions of the finished dish ("1 serving = 240 g"). */
    val servings: List<Serving> = emptyList(),
    val createdAt: Long,
)

/**
 * The units a recipe can be logged in: the portions it names, plus the whole thing.
 * The whole is what makes "half a recipe" sayable without the model dividing the yield
 * itself, which it is not allowed to do.
 */
val RecipeEntity.portions: List<Serving>
    get() = servings + Serving(RECIPE_UNIT, yieldGrams)

/** The name of the unit meaning the whole recipe. */
const val RECIPE_UNIT = "recipe"

@Serializable
enum class TargetType { FOOD, RECIPE }

@Serializable
@Entity(tableName = "meals")
data class MealEntity(
    @PrimaryKey val id: String,
    /**
     * The user's own word when they gave one ("lunch"), otherwise a short
     * description of what was eaten ("Toast and eggs"). Never a time-of-day guess.
     */
    val label: String,
    /** When the meal happened; shown once for the whole meal. */
    val at: Long,
)

/** A meal with the items eaten for it, in the order they were logged. */
data class MealWithEntries(
    @Embedded val meal: MealEntity,
    @Relation(parentColumn = "id", entityColumn = "mealId")
    val entries: List<LogEntryEntity>,
)

/**
 * A single item eaten. It has no time of its own: it belongs to a meal, and the meal
 * carries when that happened.
 */
@Serializable
@Entity(tableName = "log_entries")
data class LogEntryEntity(
    @PrimaryKey val id: String,
    /** The meal this item belongs to. */
    val mealId: String,
    val targetType: TargetType,
    val targetId: String,
    /** The food or recipe name at log time, for rendering without a join. */
    val label: String,
    val amount: Double,
    val unit: String,
    val grams: Double,
    val nutrients: Nutrients,
)

/** The single always-present document describing the user. */
@Serializable
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val text: String,
)

/** A bounded conversation. Stored for the user to read back; never read by the agent. */
@Serializable
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
)

@Serializable
enum class MessageRole { USER, ASSISTANT }

@Serializable
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    /**
     * Small copies of the photos sent with this message, so the transcript can show
     * what was sent without keeping the full image. Stored as JSON in one column,
     * like the other value objects.
     */
    @ColumnInfo(defaultValue = "'[]'")
    val thumbs: List<String> = emptyList(),
    val at: Long,
)
