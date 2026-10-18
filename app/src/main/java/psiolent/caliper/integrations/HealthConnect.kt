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

    /** Active kilocalories burned on [date], or null when unavailable or not granted. */
    suspend fun burnedCalories(context: Context, date: LocalDate): Double? {
        if (!available(context)) return null
        val client = HealthConnectClient.getOrCreate(context)
        if (!client.permissionController.getGrantedPermissions().containsAll(permissions)) return null

        val zone = ZoneId.systemDefault()
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(
                    date.atStartOfDay(zone).toInstant(),
                    date.plusDays(1).atStartOfDay(zone).toInstant(),
                ),
            ),
        )
        return result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
    }
}
