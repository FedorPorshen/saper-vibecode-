package com.saper.game.ui.progress

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saper.game.data.Achievements
import com.saper.game.data.ProgressionStore
import com.saper.game.data.Ranks
import com.saper.game.ui.theme.ClassicTitleBar
import com.saper.game.ui.theme.WindowPanel
import com.saper.game.ui.theme.classicBevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Звания с датами получения, полоса опыта и достижения. */
@Composable
fun ProgressScreen(onBack: () -> Unit) {
    var xp by remember { mutableStateOf(0) }
    var achMask by remember { mutableStateOf(0) }
    var bestStreak by remember { mutableStateOf(0) }
    var rankTimestamps by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    LaunchedEffect(Unit) {
        xp = ProgressionStore.currentXp()
        achMask = ProgressionStore.achievementMask()
        bestStreak = ProgressionStore.bestStreak()
        rankTimestamps = ProgressionStore.rankTimestamps()
    }
    val current = Ranks.forXp(xp)

    Column(
        Modifier
            .fillMaxSize()
            .background(WindowPanel)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ClassicTitleBar("Звания и достижения", onMenu = onBack)

        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8F7EF))
                .classicBevel(inset = true)
                .padding(12.dp)
        ) {
            Text("⛑ ${current.name}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            val next = Ranks.next(xp)
            if (next == null) {
                Text("Максимальное звание достигнуто! Опыт: $xp", fontSize = 13.sp)
            } else {
                val fraction = ((xp - current.xpRequired).toFloat() / (next.xpRequired - current.xpRequired)).coerceIn(0f, 1f)
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
                    fontSize = 13.sp
                )
            }
            Text("Лучшая серия побед: $bestStreak", fontSize = 13.sp, color = Color(0xFF404040))
        }

        Spacer(Modifier.height(20.dp))
        Text("ЗВАНИЯ", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        for (rank in Ranks.ALL.reversed()) {
            RankRow(rank, xp, rank == current, rankTimestamps[rank.name])
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Color(0xFF909090))
        Spacer(Modifier.height(12.dp))
        Text("ДОСТИЖЕНИЯ", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        for (a in Achievements.ALL) {
            val unlocked = achMask and a.mask != 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (unlocked) a.icon else "🔒", fontSize = 20.sp)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(
                        a.title,
                        fontSize = 16.sp,
                        fontWeight = if (unlocked) FontWeight.Bold else FontWeight.Normal,
                        color = if (unlocked) Color(0xFF202020) else Color(0xFF909090)
                    )
                    Text(a.description, fontSize = 12.sp, color = Color(0xFF606060))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RankRow(rank: com.saper.game.data.Rank, xp: Int, isCurrent: Boolean, obtainedAt: Long?) {
    val obtained = xp >= rank.xpRequired
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (obtained) "✅" else "🔒", fontSize = 18.sp)
        Column(Modifier.padding(start = 8.dp)) {
            Text(
                rank.name,
                fontSize = if (isCurrent) 17.sp else 15.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (obtained) Color(0xFF202020) else Color(0xFF555555)
            )
            Text(
                "${rank.xpRequired} XP" + if (isCurrent) " · Текущее звание" else "",
                fontSize = 13.sp,
                color = Color(0xFF606060)
            )
            Text(
                text = when {
                    obtained && obtainedAt != null -> "получено ${timeAgo(obtainedAt)}"
                    obtained -> "получено ранее — дата не записывалась"
                    else -> "нужно ещё ${rank.xpRequired - xp} XP"
                },
                fontSize = 12.sp,
                color = if (obtained) Color(0xFF308030) else Color(0xFF909090)
            )
        }
    }
}

/** «2 мин назад», «5 ч назад», «3 дн назад», а после месяца — точная дата. */
private fun timeAgo(timestamp: Long): String {
    val diffMinutes = (System.currentTimeMillis() - timestamp) / 60000
    return when {
        diffMinutes < 1 -> "только что"
        diffMinutes < 60 -> "${diffMinutes} ${plural(diffMinutes, "минуту", "минуты", "минут")} назад"
        diffMinutes < 60 * 24 -> {
            val hours = diffMinutes / 60
            "$hours ${plural(hours, "час", "часа", "часов")} назад"
        }
        diffMinutes < 60L * 24 * 30 -> {
            val days = diffMinutes / (60 * 24)
            "$days ${plural(days, "день", "дня", "дней")} назад"
        }
        else -> SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

/** Русские формы числительных: 1 минуту, 2 минуты, 5 минут. */
private fun plural(n: Long, one: String, few: String, many: String): String {
    val mod10 = (n % 10).toInt()
    val mod100 = (n % 100).toInt()
    return when {
        mod10 == 1 && mod100 != 11 -> one
        mod10 in 2..4 && (mod100 < 10 || mod100 >= 20) -> few
        else -> many
    }
}
