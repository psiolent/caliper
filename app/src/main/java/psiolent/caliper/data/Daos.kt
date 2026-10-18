package psiolent.caliper.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM foods ORDER BY name")
    fun observeAll(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods")
    suspend fun all(): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun byId(id: String): FoodEntity?

    /** Used to reuse a food the model is saving again rather than pile up duplicates. */
    @Query(
        "SELECT * FROM foods WHERE name = :name COLLATE NOCASE " +
            "AND IFNULL(brand, '') = IFNULL(:brand, '') LIMIT 1",
    )
    suspend fun byNameAndBrand(name: String, brand: String?): FoodEntity?

    @Query(
        "SELECT * FROM foods WHERE name LIKE '%' || :query || '%' " +
            "OR (brand IS NOT NULL AND brand LIKE '%' || :query || '%') ORDER BY name LIMIT 25",
    )
    suspend fun search(query: String): List<FoodEntity>

    @Upsert
    suspend fun upsert(food: FoodEntity)

    @Delete
    suspend fun delete(food: FoodEntity)
}

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY name")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes ORDER BY name")
    suspend fun all(): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun byId(id: String): RecipeEntity?

    @Upsert
    suspend fun upsert(recipe: RecipeEntity)

    @Delete
    suspend fun delete(recipe: RecipeEntity)
}

@Dao
interface MealDao {
    /** Meals in a time window, each with its items. */
    @Transaction
    @Query("SELECT * FROM meals WHERE at >= :from AND at < :to ORDER BY at")
    fun observeBetween(from: Long, to: Long): Flow<List<MealWithEntries>>

    @Transaction
    @Query("SELECT * FROM meals WHERE at >= :from AND at < :to ORDER BY at")
    suspend fun between(from: Long, to: Long): List<MealWithEntries>

    @Query("SELECT * FROM meals WHERE id = :id")
    suspend fun byId(id: String): MealEntity?

    @Query("SELECT * FROM meals")
    suspend fun all(): List<MealEntity>

    @Upsert
    suspend fun upsert(meal: MealEntity)

    @Query("DELETE FROM meals WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM log_entries")
    suspend fun all(): List<LogEntryEntity>

    @Query("SELECT * FROM log_entries WHERE id = :id")
    suspend fun byId(id: String): LogEntryEntity?

    /** How many logged items point at a food or recipe, to warn before deleting one. */
    @Query("SELECT COUNT(*) FROM log_entries WHERE targetId = :targetId")
    suspend fun countByTarget(targetId: String): Int

    @Query("DELETE FROM log_entries WHERE mealId = :mealId")
    suspend fun deleteForMeal(mealId: String)

    @Upsert
    suspend fun upsert(entry: LogEntryEntity)

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface SessionDao {
    /**
     * Each session described by the two ends of its own conversation: the first thing the
     * user said and the last thing the agent replied. Nothing is read across sessions and
     * nothing is computed, so a description cannot drift from what was actually stored.
     *
     * A session is created when the app opens, before anything is said, so sessions with
     * no messages at all are left out — an empty one is not a conversation.
     */
    @Query(
        """
        SELECT s.id AS sessionId, s.startedAt AS startedAt,
            (SELECT content FROM messages WHERE sessionId = s.id AND role = 'USER' ORDER BY id LIMIT 1) AS opening,
            (SELECT content FROM messages WHERE sessionId = s.id AND role = 'ASSISTANT' ORDER BY id DESC LIMIT 1) AS closing
        FROM sessions s
        WHERE EXISTS (SELECT 1 FROM messages WHERE sessionId = s.id)
        ORDER BY s.startedAt DESC
        """,
    )
    fun observeSummaries(): Flow<List<SessionSummary>>

    @Query("SELECT * FROM sessions")
    suspend fun all(): List<SessionEntity>

    @Upsert
    suspend fun upsert(session: SessionEntity)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY at, id")
    fun observeBySession(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY at, id")
    suspend fun bySession(sessionId: String): List<MessageEntity>

    @Query("SELECT * FROM messages")
    suspend fun all(): List<MessageEntity>

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Insert
    suspend fun insert(message: MessageEntity)
}
