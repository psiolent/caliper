package psiolent.caliper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import psiolent.caliper.ui.CaliperRoot
import psiolent.caliper.ui.CaliperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CaliperApp).container
        setContent {
            CaliperTheme {
                CaliperRoot(container)
            }
        }
    }
}
