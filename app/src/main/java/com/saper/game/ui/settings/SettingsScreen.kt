package com.saper.game.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saper.game.data.AppSettings
import com.saper.game.ui.game.ClassicColors
import com.saper.game.ui.game.LongPressViewConfiguration

/** Настройки: время зажатия флажка, вибрация, разбор после проигрыша. */
@Composable
fun SettingsScreen(settings: AppSettings, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(ClassicColors.GrayBg)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "← Меню",
            fontSize = 18.sp,
            color = Color(0xFF202060),
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 4.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text("НАСТРОЙКИ", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        Text("Флажок: задержка пальца", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "Держи клетку дольше — поставится флажок; отпусти раньше — клетка откроется. " +
                "Слишком маленькое значение опасно: обычный тап начнёт случайно ставить флажки.",
            fontSize = 13.sp,
            color = Color(0xFF505050)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${settings.longPressMs} мс", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("80", fontSize = 12.sp, color = Color(0xFF606060))
        }
        Slider(
            value = settings.longPressMs.toFloat(),
            onValueChange = { settings.updateLongPressMs(it.toInt()) },
            valueRange = 80f..500f,
            steps = 41
        )
        Text("500", fontSize = 12.sp, color = Color(0xFF606060), modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(8.dp))
        PracticeCell(settings.longPressMs)

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Color(0xFF909090))
        Spacer(Modifier.height(12.dp))

        SwitchRow("Вибрация при взрыве", settings.vibration) { settings.updateVibration(it) }
        SwitchRow("Разбор после проигрыша", settings.showAnalysis) { settings.updateShowAnalysis(it) }

        Spacer(Modifier.height(24.dp))
    }
}

/** Тренировочная клетка: тот же жест, что и в игре, — можно прочувствовать задержку. */
@Composable
private fun PracticeCell(longPressMs: Int) {
    var status by remember { mutableStateOf("Потренируйся прямо здесь") }
    val baseConfig = LocalViewConfiguration.current
    val config = remember(baseConfig, longPressMs) {
        LongPressViewConfiguration(baseConfig, longPressMs.toLong())
    }
    CompositionLocalProvider(LocalViewConfiguration provides config) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(Color(0xFFD6D6D6))
                .border(2.dp, ClassicColors.BevelDark)
                .clipToBounds()
                .pointerInput(longPressMs) {
                    detectTapGestures(
                        onTap = { status = "Тап → клетка открылась бы" },
                        onLongPress = { status = "Зажал $longPressMs мс → флажок ⚑" }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(status, fontSize = 15.sp)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
