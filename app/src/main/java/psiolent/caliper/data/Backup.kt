package psiolent.caliper.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Everything Caliper stores, as one portable document. */
@Serializable
data class Backup(
    val version: Int = 1,
    val profile: ProfileEntity? = null,
    val foods: List<FoodEntity> = emptyList(),
    val recipes: List<RecipeEntity> = emptyList(),
    val batches: List<BatchEntity> = emptyList(),
    val meals: List<MealEntity> = emptyList(),
    val entries: List<LogEntryEntity> = emptyList(),
    val sessions: List<SessionEntity> = emptyList(),
    val messages: List<MessageEntity> = emptyList(),
)

/**
 * Manual export/import, a safety net alongside Android's Auto Backup. The whole
 * store is small enough to round-trip as JSON.
 */
object BackupCodec {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    fun decode(text: String): Backup = json.decodeFromString(Backup.serializer(), text)
}
