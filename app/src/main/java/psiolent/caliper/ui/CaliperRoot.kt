package psiolent.caliper.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import psiolent.caliper.AppContainer

private data class Tab(val label: String, val symbol: String)

private val tabs = listOf(
    Tab("Chat", "\uD83D\uDCAC"),
    Tab("Day", "\uD83D\uDCC5"),
    Tab("Library", "\uD83D\uDCDA"),
    Tab("History", "\uD83D\uDD52"),
    Tab("Settings", "\u2699"),
)

@Composable
fun CaliperRoot(container: AppContainer) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Text(tab.symbol) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                0 -> ChatScreen(container)
                1 -> DayScreen(container)
                2 -> LibraryScreen(container)
                3 -> HistoryScreen(container)
                else -> SettingsScreen(container)
            }
        }
    }
}
