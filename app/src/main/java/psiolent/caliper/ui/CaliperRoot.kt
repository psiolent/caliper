package psiolent.caliper.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import psiolent.caliper.AppContainer

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Chat", Icons.AutoMirrored.Filled.Chat),
    Tab("Day", Icons.Filled.CalendarMonth),
    Tab("Library", Icons.AutoMirrored.Filled.MenuBook),
    Tab("History", Icons.Filled.History),
    Tab("Settings", Icons.Filled.Settings),
)

@Composable
fun CaliperRoot(container: AppContainer) {
    var selected by rememberSaveable { mutableIntStateOf(0) }

    // Back returns to the chat from any other tab rather than leaving the app.
    // A screen with its own back step (History's transcript) registers its own
    // handler and takes precedence.
    BackHandler(enabled = selected != 0) { selected = 0 }

    // While the keyboard is up the nav bar is covered anyway; hiding it also stops
    // its height being reserved *under* the keyboard, which left a dead gap above
    // the chat's input row.
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (!keyboardVisible) {
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
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
