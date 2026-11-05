package com.saper.game.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saper.game.data.DifficultySummary
import com.saper.game.data.ProgressionStore
import com.saper.game.data.Ranks
import com.saper.game.data.SaperStore
import com.saper.game.game.Difficulty
import com.saper.game.ui.theme.ClassicButton
import com.saper.game.ui.theme.ClassicTitleBar
import com.saper.game.ui.theme.WindowPanel
import com.saper.game.ui.theme.WindowInk
import com.saper.game.ui.theme.classicBevel
import com.saper.game.ui.game.formatTime

/** Главный экран: звание со прогрессом, выбор сложности (с рекордами) и навигация. */
@Composable
fun MenuScreen(
    onPlay: (Difficulty) -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onProgress: () -> Unit
) {
    var summary by remember { mutableStateOf<Map<String, DifficultySummary>>(emptyMap()) }
    var xp by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        summary = SaperStore.summary().associateBy { it.difficulty }
        xp = ProgressionStore.currentXp()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(WindowPanel)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
            .classicBevel(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ClassicTitleBar("Сапёр")
        Spacer(Modifier.height(12.dp))
        Text("Новая игра", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = WindowInk)
        Text("Логика без угадывания", fontSize = 14.sp, color = WindowInk)
        Spacer(Modifier.height(12.dp))
        // карточка звания кликабельна: полный список званий и дат
        Box(
            Modifier
                .fillMaxWidth()
                .clickable { onProgress() }
        ) {
            RankCard(xp)
        }

        Spacer(Modifier.height(16.dp))
        for (d in Difficulty.entries) {
            val s = summary[d.title]
            DifficultyButton(
                title = d.title,
                size = "${d.width}×${d.height} · ${d.mines} мин",
                record = s?.best?.let { "рекорд: ${formatTime(it)}" } ?: "рекорд: —",
                onClick = { onPlay(d) }
            )
            Spacer(Modifier.height(10.dp))
        }

        FlowRow(
            Modifier.fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ClassicButton("Звания", onProgress)
            ClassicButton("Рекорды", onStats)
            ClassicButton("Настройки", onSettings)
        }
    }
}

@Composable
private fun RankCard(xp: Int) {
    val rank = Ranks.forXp(xp)
    val next = Ranks.next(xp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8F7EF))
            .classicBevel(inset = true)
            .padding(12.dp)
    ) {
        Text("⛑ ${rank.name}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (next == null) {
            Text("Максимальное звание! Опыт: $xp", fontSize = 13.sp, color = Color(0xFF404040))
        } else {
            val fraction = ((xp - rank.xpRequired).toFloat() / (next.xpRequired - rank.xpRequired)).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(Color(0xFFAAAAAA))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(Color(0xFF5BAF27))
                )
            }
            Text(
                "$xp / ${next.xpRequired} XP · до «${next.name}» осталось ${next.xpRequired - xp}",
                fontSize = 13.sp,
                color = Color(0xFF404040)
            )
        }
    }
}

@Composable
private fun DifficultyButton(title: String, size: String, record: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(WindowPanel)
            .classicBevel()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(size, fontSize = 13.sp, color = Color(0xFF404040))
            Text(record, fontSize = 13.sp, color = Color(0xFF802020), fontWeight = FontWeight.Bold)
        }
    }
}
