package psiolent.caliper.integrations

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A small rolling file of what the agent actually did — the calls it made and what
 * came back. The system log rotates within minutes on a busy device, which makes a
 * rough session impossible to review afterwards; this survives.
 *
 * Kept in the app's private files, never backed up, and never sent anywhere.
 */
class AgentLog(context: Context) {

    private val file = File(context.filesDir, "agent-log.txt")

    @Synchronized
    fun append(line: String) {
        runCatching {
            if (file.length() > MAX_BYTES) {
                val kept = file.readLines().takeLast(KEEP_LINES)
                file.writeText(kept.joinToString("\n", postfix = "\n"))
            }
            file.appendText("${TIME.format(Instant.now().atZone(ZoneId.systemDefault()))}  $line\n")
        }
    }

    private companion object {
        const val MAX_BYTES = 256 * 1024L
        const val KEEP_LINES = 1500
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
