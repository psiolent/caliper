package psiolent.caliper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import psiolent.caliper.ui.CaliperTheme

/**
 * Health Connect will not show its permission sheet unless the app declares an
 * activity for the rationale intent. This is where the user lands when they tap
 * the privacy-policy link on Health Connect's own permission screen.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CaliperTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Why Caliper reads Health Connect", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Caliper reads the calories you burn so the day view can show what you ate " +
                                "against what you burned. It reads only active and total calories burned, " +
                                "and only for the day you are looking at.",
                        )
                        Text(
                            "Nothing leaves your phone. Health data is never sent to the model or to any " +
                                "server; the app only puts the day's burned total next to your own numbers.",
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { finish() }) { Text("Close") }
                    }
                }
            }
        }
    }
}
