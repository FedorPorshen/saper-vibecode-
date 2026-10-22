package com.saper.game.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
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
import com.saper.game.ui.game.ClassicColors
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
            .background(ClassicColors.GrayBg)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("САПЁР", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color(0xFF202020))
        Text("решается логикой, а не угадыванием", fontSize = 13.sp, color = Color(0xFF505050))
        Spacer(Modifier.height(16.dp))
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

        Spacer(Modifier.weight(1f))

        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            MenuLink("Звания", onProgress)
            Spacer(Modifier.width(20.dp))
            MenuLink("Рекорды", onStats)
            Spacer(Modifier.width(20.dp))
            MenuLink("Настройки", onSettings)
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
            .background(Color(0xFFD6D6D6))
            .border(2.dp, ClassicColors.BevelDark)
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
                        .background(Color(0xFF308030))
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
            .background(Color(0xFFD6D6D6))
            .border(2.dp, ClassicColors.BevelDark)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(size, fontSize = 13.sp, color = Color(0xFF404040))
            Text(record, fontSize = 13.sp, color = Color(0xFF802020), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MenuLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 18.sp,
        color = Color(0xFF202060),
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp)
    )
}
