package psiolent.caliper.integrations

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.ZoneId

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
         * The total is not a running tally: Health Connect derives it as the active
         * calories so far plus a resting burn for the *whole* day, so it already covers
         * hours that have not happened. See `docs/decisions/0009` before relabelling it.
         */
        data class Value(val totalKcal: Double?, val activeKcal: Double?) : Burned
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
        )
    }
}
