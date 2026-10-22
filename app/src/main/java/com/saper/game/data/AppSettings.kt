package com.saper.game.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Настройки приложения: Compose-состояние для мгновенной реакции интерфейса
 * + запись в базу при каждом изменении.
 */
class AppSettings(private val scope: CoroutineScope) {

    companion object {
        const val KEY_LONG_PRESS_MS = "longPressMs"
        const val KEY_VIBRATION = "vibration"
        const val KEY_SHOW_ANALYSIS = "showAnalysis"
        const val DEFAULT_LONG_PRESS_MS = 200
    }

    /** Сколько мс нужно держать палец, чтобы поставить флажок. */
    var longPressMs by mutableStateOf(DEFAULT_LONG_PRESS_MS)
        private set
    var vibration by mutableStateOf(true)
        private set
    /** Показывать после проигрыша, где была мина и какие флажки ошибочны. */
    var showAnalysis by mutableStateOf(true)
        private set

    private var loaded = false

    suspend fun load() {
        if (loaded) return
        loaded = true
        longPressMs = SaperStore.intSetting(KEY_LONG_PRESS_MS, DEFAULT_LONG_PRESS_MS)
        vibration = SaperStore.boolSetting(KEY_VIBRATION, true)
        showAnalysis = SaperStore.boolSetting(KEY_SHOW_ANALYSIS, true)
    }

    fun updateLongPressMs(value: Int) {
        val clamped = value.coerceIn(80, 500)
        longPressMs = clamped
        scope.launch { SaperStore.putIntSetting(KEY_LONG_PRESS_MS, clamped) }
    }

    fun updateVibration(value: Boolean) {
        vibration = value
        scope.launch { SaperStore.putBoolSetting(KEY_VIBRATION, value) }
    }

    fun updateShowAnalysis(value: Boolean) {
        showAnalysis = value
        scope.launch { SaperStore.putBoolSetting(KEY_SHOW_ANALYSIS, value) }
    }
}
