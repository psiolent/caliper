package psiolent.caliper.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WhenParsingTest {

    private val zone = ZoneId.of("America/New_York")
    private val today = LocalDate.of(2026, 10, 18)

    @Test
    fun `a local date-time is read in the device zone, not as UTC`() {
        // 07:00 in New York on that date is 11:00 UTC. Reading it as UTC would be the bug.
        assertEquals(Instant.parse("2026-10-18T11:00:00Z"), parseWhen("2026-10-18T07:00:00", zone, today))
    }

    @Test
    fun `a local date-time without seconds is accepted`() {
        assertEquals(Instant.parse("2026-10-18T11:00:00Z"), parseWhen("2026-10-18T07:00", zone, today))
    }

    @Test
    fun `a bare time is taken as today`() {
        assertEquals(Instant.parse("2026-10-18T11:00:00Z"), parseWhen("07:00", zone, today))
    }

    @Test
    fun `a full instant with an offset wins`() {
        assertEquals(Instant.parse("2026-10-18T07:00:00Z"), parseWhen("2026-10-18T07:00:00Z", zone, today))
    }

    @Test
    fun `an unreadable time is null rather than silently now`() {
        assertNull(parseWhen("this morning", zone, today))
        assertNull(parseWhen("", zone, today))
    }

    @Test
    fun `a bare date is not a time, rather than being read as midnight`() {
        // A date alone is a missing time of day. Reading it as 00:00 stamped a meal
        // at midnight that was eaten in the afternoon.
        assertNull(parseWhen("2026-10-18", zone, today))
    }
}
