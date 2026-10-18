package psiolent.caliper.integrations

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * Read-only calories burned, via Health Connect. Purely additive: if Health
 * Connect is missing or the user declines, the app works exactly as before.
 */
object HealthConnect {

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    )

    val permissionContract = PermissionController.createRequestPermissionResultContract()

    fun available(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun granted(context: Context): Boolean =
        available(context) &&
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(permissions)

    /**
     * Whether calories burned can be read, kept distinct from whether any were
     * recorded — "connected but empty" must not look like "not connected".
     */
    sealed interface Burned {
        data object Unavailable : Burned
        data object NotGranted : Burned

        /**
         * Granted. [totalKcal] includes the resting/basal burn; [activeKcal] is
         * movement only. Either is null when nothing has been recorded.
         *
         * [recordedAt] is when the newest record behind these numbers was last
         * written. Health Connect aggregates over the whole day, so a figure holds
         * still for as long as the source stays quiet — and a source can go quiet for
         * hours. Saying when the reading is from is the difference between a number
         * that looks broken and one that is honestly stale.
         */
        data class Value(
            val totalKcal: Double?,
            val activeKcal: Double?,
            val recordedAt: Instant? = null,
        ) : Burned
    }

    suspend fun burned(context: Context, date: LocalDate): Burned {
        if (!available(context)) return Burned.Unavailable
        val client = HealthConnectClient.getOrCreate(context)
        if (!client.permissionController.getGrantedPermissions().containsAll(permissions)) return Burned.NotGranted

        val zone = ZoneId.systemDefault()
        val range = TimeRangeFilter.between(
            date.atStartOfDay(zone).toInstant(),
            date.plusDays(1).atStartOfDay(zone).toInstant(),
        )
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(
                    TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                    ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                ),
                timeRangeFilter = range,
            ),
        )
        return Burned.Value(
            totalKcal = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories,
            activeKcal = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
            recordedAt = lastWritten(client, TotalCaloriesBurnedRecord::class, range)
                ?: lastWritten(client, ActiveCaloriesBurnedRecord::class, range),
        )
    }

    /** When the newest record of one kind was written, or null when there are none. */
    private suspend fun lastWritten(
        client: HealthConnectClient,
        type: KClass<out Record>,
        range: TimeRangeFilter,
    ): Instant? = runCatching {
        client.readRecords(ReadRecordsRequest(type, range)).records
            .maxOfOrNull { it.metadata.lastModifiedTime }
    }.getOrNull()
}
