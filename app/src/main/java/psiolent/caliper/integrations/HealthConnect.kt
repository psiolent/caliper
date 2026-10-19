package psiolent.caliper.integrations

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
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
         * The total is not a running tally: it is the active calories so far plus a
         * resting burn for the *whole* day, so it already covers hours that have not
         * happened. See `docs/decisions/0009` before relabelling it.
         *
         * [source] is the app whose activity figures were used, when they came from a
         * single one. It is worth showing: two trackers disagreeing is the usual reason
         * this number surprises someone.
         */
        data class Value(
            val totalKcal: Double?,
            val activeKcal: Double?,
            val source: String? = null,
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

        return selectBurn(
            perOriginKcal = activeByOrigin(client, range),
            aggregateActive = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
            aggregateTotal = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories,
        ).let { Burned.Value(it.totalKcal, it.activeKcal, it.source) }
    }

    /**
     * Active calories for the day, grouped by the app that recorded them.
     *
     * Read rather than aggregated because Health Connect's aggregate drops records that
     * overlap: a tracker that writes a day-long baseline *and* a workout inside it gets
     * counted once, losing the workout. One ring wrote 167 for the day and 569 for a walk
     * inside it; the aggregate returned 167, while the ring's own app showed 736.
     */
    private suspend fun activeByOrigin(client: HealthConnectClient, range: TimeRangeFilter): Map<String, Double> {
        val totals = mutableMapOf<String, Double>()
        var token: String? = null
        do {
            val page = client.readRecords(
                ReadRecordsRequest(
                    recordType = ActiveCaloriesBurnedRecord::class,
                    timeRangeFilter = range,
                    pageSize = 1000,
                    pageToken = token,
                ),
            )
            page.records.forEach { record ->
                val origin = record.metadata.dataOrigin.packageName
                totals[origin] = (totals[origin] ?: 0.0) + record.energy.inKilocalories
            }
            token = page.pageToken
        } while (token != null && token.isNotBlank())
        return totals
    }
}

/** What the day's burn came to, and which app it was taken from. */
internal data class BurnSelection(val activeKcal: Double?, val totalKcal: Double?, val source: String?)

/**
 * Pick the day's active burn from what the trackers recorded.
 *
 * One app's numbers are used, not a mixture: two trackers covering the same hours would
 * be counted twice. The one reporting the most is taken, on the grounds that the app
 * tracking you all day reports more than one that caught part of it.
 *
 * The total is then adjusted by the same amount as the active figure, because Health
 * Connect derives the total from the active number it worked out itself. Taking its total
 * and swapping in ours keeps `total − active = resting` true, which the screen relies on.
 */
internal fun selectBurn(
    perOriginKcal: Map<String, Double>,
    aggregateActive: Double?,
    aggregateTotal: Double?,
): BurnSelection {
    val chosen = perOriginKcal.maxByOrNull { it.value }?.takeIf { it.value > 0.0 }
    val active = chosen?.value ?: aggregateActive
    val total = when {
        aggregateTotal == null -> null
        aggregateActive == null || active == null -> aggregateTotal
        else -> aggregateTotal - aggregateActive + active
    }
    return BurnSelection(active, total, chosen?.key)
}
