package com.saper.game

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.saper.game.data.AppSettings
import com.saper.game.data.SaperStore
import com.saper.game.game.Difficulty
import com.saper.game.ui.game.GameScreen
import com.saper.game.ui.menu.MenuScreen
import com.saper.game.ui.progress.ProgressScreen
import com.saper.game.ui.settings.SettingsScreen
import com.saper.game.ui.stats.StatsScreen
import com.saper.game.ui.theme.SaperTheme
import kotlinx.coroutines.launch

/** Экраны приложения без навигационной библиотеки — их всего пять. */
sealed interface Screen {
    data object Menu : Screen
    data object Stats : Screen
    data object Settings : Screen
    data object Progress : Screen
    data class Game(val difficulty: Difficulty) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SaperStore.init(applicationContext)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK))
        setContent {
            SaperTheme {
                val scope = rememberCoroutineScope()
                val settings = remember { AppSettings(scope) }
                LaunchedEffect(Unit) { settings.load() }

                var screen by remember { mutableStateOf<Screen>(Screen.Menu) }
                // Жест «назад» с края экрана: из внутренних экранов — в меню;
                // из меню назад некуда — жест сворачивает приложение.
                BackHandler(enabled = screen !is Screen.Menu && screen !is Screen.Game) {
                    screen = Screen.Menu
                }
                when (val current = screen) {
                    Screen.Menu -> MenuScreen(
                        onPlay = { screen = Screen.Game(it) },
                        onStats = { screen = Screen.Stats },
                        onSettings = { screen = Screen.Settings },
                        onProgress = { screen = Screen.Progress }
                    )
                    Screen.Stats -> StatsScreen(onBack = { screen = Screen.Menu })
                    Screen.Settings -> SettingsScreen(settings, onBack = { screen = Screen.Menu })
                    Screen.Progress -> ProgressScreen(onBack = { screen = Screen.Menu })
                    is Screen.Game -> GameScreen(
                        difficulty = current.difficulty,
                        settings = settings,
                        onExit = { screen = Screen.Menu }
                    )
                }
            }
        }
    }
}
