package psiolent.caliper.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
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

    @Query("SELECT * FROM recipes")
    suspend fun all(): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun byId(id: String): RecipeEntity?

    @Upsert
    suspend fun upsert(recipe: RecipeEntity)

    @Delete
    suspend fun delete(recipe: RecipeEntity)
}

@Dao
interface BatchDao {
    @Query("SELECT * FROM batches ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches")
    suspend fun all(): List<BatchEntity>

    @Query("SELECT * FROM batches ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<BatchEntity>

    @Query("SELECT * FROM batches WHERE id = :id")
    suspend fun byId(id: String): BatchEntity?

    @Upsert
    suspend fun upsert(batch: BatchEntity)

    @Delete
    suspend fun delete(batch: BatchEntity)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM log_entries WHERE at >= :from AND at < :to ORDER BY at")
    fun observeBetween(from: Long, to: Long): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entries WHERE at >= :from AND at < :to ORDER BY at")
    suspend fun between(from: Long, to: Long): List<LogEntryEntity>

    @Query("SELECT * FROM log_entries")
    suspend fun all(): List<LogEntryEntity>

    @Query("SELECT * FROM log_entries WHERE id = :id")
    suspend fun byId(id: String): LogEntryEntity?

    @Query("SELECT * FROM log_entries WHERE targetType = 'BATCH' AND targetId = :batchId")
    suspend fun forBatch(batchId: String): List<LogEntryEntity>

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
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

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
