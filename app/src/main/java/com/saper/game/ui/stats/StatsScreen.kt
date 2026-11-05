package com.saper.game.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saper.game.data.DifficultySummary
import com.saper.game.data.GameResultEntity
import com.saper.game.data.SaperStore
import com.saper.game.game.Difficulty
import com.saper.game.ui.theme.ClassicTitleBar
import com.saper.game.ui.theme.WindowPanel
import com.saper.game.ui.theme.classicBevel
import com.saper.game.ui.game.formatTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Рекорды и последние партии. */
@Composable
fun StatsScreen(onBack: () -> Unit) {
    var summary by remember { mutableStateOf<List<DifficultySummary>>(emptyList()) }
    var recent by remember { mutableStateOf<List<GameResultEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        summary = SaperStore.summary()
        recent = SaperStore.recent(12)
    }
    val byName = summary.associateBy { it.difficulty }

    Column(
        Modifier
            .fillMaxSize()
            .background(WindowPanel)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ClassicTitleBar("Рекорды", onMenu = onBack)

        for (d in Difficulty.entries) {
            Spacer(Modifier.height(12.dp))
            DifficultyBlock(d, byName[d.title])
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Color(0xFF909090))
        Spacer(Modifier.height(12.dp))
        Text("ПОСЛЕДНИЕ ИГРЫ", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (recent.isEmpty()) {
            Text("Пока сыграно ни одной партии", fontSize = 14.sp, color = Color(0xFF505050))
        } else {
            for (r in recent) RecentRow(r)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DifficultyBlock(d: Difficulty, s: DifficultySummary?) {
    val played = s?.played ?: 0
    val won = s?.wins ?: 0
    val winRate = if (played > 0) won * 100 / played else 0

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8F7EF))
            .classicBevel(inset = true)
            .padding(12.dp)
    ) {
        Text(d.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Лучшее время: ${s?.best?.let { formatTime(it) } ?: "—"}", fontSize = 15.sp)
        Text("Игр: $played · Побед: $won · $winRate%", fontSize = 15.sp, color = Color(0xFF404040))
    }
}

private val dateFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

@Composable
private fun RecentRow(r: GameResultEntity) {
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(if (r.won) "🏁" else "💥", fontSize = 16.sp)
        Text(r.difficulty, fontSize = 15.sp, fontWeight = if (r.won) FontWeight.Bold else FontWeight.Normal)
        Text(formatTime(r.timeSeconds), fontSize = 15.sp)
        if (r.hintsUsed > 0) Text("💡×${r.hintsUsed}", fontSize = 14.sp, color = Color(0xFF806020))
        Text(dateFormat.format(Date(r.finishedAt)).replace(' ', ' '), fontSize = 13.sp, color = Color(0xFF606060))
    }
}
