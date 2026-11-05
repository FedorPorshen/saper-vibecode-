package com.saper.game.ui.game

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saper.game.data.AppSettings
import com.saper.game.data.GameFinishBonus
import com.saper.game.data.ProgressionStore
import com.saper.game.data.SaperStore
import com.saper.game.game.CellState
import com.saper.game.game.Difficulty
import com.saper.game.game.HINT_PENALTY_SECONDS
import com.saper.game.game.LampResult
import com.saper.game.ui.theme.ClassicButton
import com.saper.game.ui.theme.WindowPanel
import com.saper.game.ui.theme.classicBevel

/**
 * Игровой экран целиком: классическая планка сверху, поле в середине,
 * кнопки подсказок снизу, оверлеи победы/поражения/генерации.
 */
@Composable
fun GameScreen(difficulty: Difficulty, settings: AppSettings, onExit: () -> Unit) {
    val controller = remember(difficulty) { GameController(difficulty) }
    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }
    val context = LocalContext.current
    var showExitDialog by remember { mutableStateOf(false) }
    var finishBonus by remember { mutableStateOf<GameFinishBonus?>(null) }

    // Жест «назад» с края экрана в игре — то же окно подтверждения, что и ☰
    BackHandler(enabled = !showExitDialog) { showExitDialog = true }

    // Пока идёт партия — экран не гаснет
    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // конец партии: вибрация + запись результата и прогресса
    LaunchedEffect(controller.phase) {
        when (controller.phase) {
            Phase.WON, Phase.LOST -> {
                if (settings.vibration && controller.phase == Phase.LOST) vibrate(context)
                val hintsUsed = difficulty.hintCharges - controller.hintCharges
                val finalTime = controller.elapsedSeconds + hintsUsed * HINT_PENALTY_SECONDS
                SaperStore.saveResult(difficulty.title, controller.phase == Phase.WON, finalTime, hintsUsed)
                finishBonus = ProgressionStore.onGameFinished(
                    difficulty,
                    controller.phase == Phase.WON,
                    finalTime,
                    hintsUsed
                )
            }
            else -> {}
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(ClassicColors.GrayBg)
            .safeDrawingPadding()
            .padding(6.dp)
            .classicBevel()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClassicButton("Меню") { showExitDialog = true }
            Text(
                difficulty.title,
                Modifier.weight(1f).padding(start = 12.dp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        ClassicTopBar(controller)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 5.dp)
                .classicBevel(inset = true)
                .padding(2.dp)
        ) {
            BoardView(controller, showAnalysis = settings.showAnalysis, longPressMs = settings.longPressMs)
            androidx.compose.animation.AnimatedVisibility(
                visible = controller.phase == Phase.GENERATING,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(120)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                GeneratingOverlay()
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = controller.lampReason != null,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120))
        ) {
            val reason = controller.lampReason
            val target = controller.lampHint
            if (reason != null && target != null) {
                val flagged = controller.engine?.board?.get(target.first, target.second)?.state == CellState.FLAGGED
                Column(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 5.dp, vertical = 4.dp)
                        .background(Color(0xFFFFF3B0))
                        .classicBevel(inset = true)
                        .heightIn(max = 150.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(
                        if (flagged) "Сними флажок с жёлтой клетки" else "Открой клетку в жёлтой рамке",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(reason.text, fontSize = 14.sp)
                }
            }
        }
        AssistBar(controller, context)
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = controller.phase == Phase.WON || controller.phase == Phase.LOST,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.9f, animationSpec = tween(220)),
        exit = fadeOut(tween(150))
    ) {
        ResultOverlay(controller, finishBonus)
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Выйти в меню?") },
            text = { Text("Текущая партия будет прервана и не попадёт в статистику.") },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false; onExit() }) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Остаться") }
            }
        )
    }
}

@Composable
private fun ClassicTopBar(controller: GameController) {
    Row(
        Modifier.fillMaxWidth().padding(5.dp)
            .background(ClassicColors.GrayBg)
            .classicBevel(inset = true)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val mines = controller.mineDisplay.coerceIn(-99, 999)
        val mineDigits = if (mines < 0) "-" + (-mines).toString().padStart(2, '0')
        else mines.toString().padStart(3, '0')
        LedCounter(mineDigits, "Мины: $mines")
        SmileyButton(controller)
        val elapsed = controller.elapsedSeconds.coerceAtMost(999)
        LedCounter(elapsed.toString().padStart(3, '0'), "Время: $elapsed сек")
    }
}

// Порядок сегментов: верхний, правые, нижний, левые, средний.
private val LedSegments = listOf(
    "1111110", "0110000", "1101101", "1111001", "0110011",
    "1011011", "1011111", "1110000", "1111111", "1111011"
)

@Composable
private fun LedCounter(digits: String, label: String) {
    val units = digits.length * 11
    Canvas(
        Modifier.background(Color.Black)
            .classicBevel(inset = true)
            .semantics { contentDescription = label }
            .padding(5.dp)
            .size((units * 2).dp, 36.dp)
    ) {
        val pixel = minOf(size.width / units, size.height / 18)
        var x = (size.width - units * pixel) / 2
        val y = (size.height - 18 * pixel) / 2
        val lit = Color(0xFFFF241A)
        val unlit = Color(0xFF380600)

        fun segment(dx: Int, dy: Int, vertical: Boolean, active: Boolean) {
            val points = listOf(0 to 1, 1 to 0, 6 to 0, 7 to 1, 6 to 2, 1 to 2)
            val path = Path()
            for ((index, point) in points.withIndex()) {
                val px = x + (dx + if (vertical) point.second else point.first) * pixel
                val py = y + (dy + if (vertical) point.first else point.second) * pixel
                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            drawPath(path, if (active) lit else unlit)
        }

        for (digit in digits) {
            val mask = if (digit == '-') "0000001" else LedSegments[digit.digitToInt()]
            segment(2, 0, false, mask[0] == '1')
            segment(8, 1, true, mask[1] == '1')
            segment(8, 9, true, mask[2] == '1')
            segment(2, 16, false, mask[3] == '1')
            segment(0, 9, true, mask[4] == '1')
            segment(0, 1, true, mask[5] == '1')
            segment(2, 8, false, mask[6] == '1')
            x += 11 * pixel
        }
    }
}

@Composable
private fun SmileyButton(controller: GameController) {
    Box(
        Modifier
            .size(48.dp)
            .background(ClassicColors.GrayBg)
            .classicBevel()
            .clickable(role = Role.Button, onClickLabel = "Новая партия") { controller.restart() }
            .semantics { contentDescription = "Новая партия" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(30.dp)) {
            val unit = size.width / 30
            fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
            drawCircle(ClassicColors.Yellow, radius = 13 * unit)
            drawCircle(Color.Black, radius = 13 * unit, style = Stroke(1.5f * unit))
            if (controller.phase == Phase.WON) {
                drawRect(Color.Black, point(7f, 9f), Size(16 * unit, 5 * unit))
            } else {
                for (x in listOf(10f, 20f)) {
                    drawCircle(Color.Black, 1.5f * unit, point(x, 11f))
                    if (controller.phase == Phase.LOST) {
                        drawLine(Color.Black, point(x - 2, 9f), point(x + 2, 13f), unit)
                    }
                }
            }
            if (controller.pressCell != null && controller.phase == Phase.PLAYING) {
                drawCircle(Color.Black, 3 * unit, point(15f, 20f), style = Stroke(1.5f * unit))
            } else {
                val lost = controller.phase == Phase.LOST
                drawArc(
                    Color.Black, if (lost) 180f else 0f, 180f, false,
                    topLeft = point(9f, if (lost) 17f else 13f),
                    size = Size(12 * unit, 10 * unit),
                    style = Stroke(1.5f * unit)
                )
            }
        }
    }
}

@Composable
private fun AssistBar(controller: GameController, context: Context) {
    val playing = controller.phase == Phase.PLAYING
    val hasCharges = controller.hintCharges > 0
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AssistButton(
                text = "🧹 Лопата",
                active = controller.shovelArmed,
                enabled = playing && hasCharges
            ) { controller.armShovel() }
            AssistButton(
                text = "💡 Лампа",
                active = false,
                enabled = playing && hasCharges
            ) {
                when (controller.lamp()) {
                    LampResult.NoLogicalMove ->
                        Toast.makeText(context, "Лампочка пока не нашла безопасную клетку", Toast.LENGTH_SHORT).show()
                    else -> {}
                }
            }
        }
        val left = controller.hintCharges
        Text(
            text = "Подсказки: " + "●".repeat(left) + "○".repeat(controller.difficulty.hintCharges - left),
            fontSize = 16.sp
        )
    }
}

@Composable
private fun AssistButton(text: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg = when {
        !enabled -> Color(0xFFADADAD)
        active -> Color(0xFFFFFF66)
        else -> WindowPanel
    }
    Box(
        Modifier
            .background(bg)
            .classicBevel(inset = active)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GeneratingOverlay() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(8.dp))
        Text("Строим честное поле…")
    }
}

@Composable
private fun ResultOverlay(controller: GameController, bonus: GameFinishBonus?) {
    val won = controller.phase == Phase.WON
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .background(ClassicColors.GrayBg)
                .border(2.dp, ClassicColors.BevelLight)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (won) "🏁 ПОБЕДА!" else "💥 ВЗРЫВ!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            val hintsUsed = controller.difficulty.hintCharges - controller.hintCharges
            val finalTime = controller.elapsedSeconds + hintsUsed * HINT_PENALTY_SECONDS
            Text("Время: ${formatTime(finalTime)}", fontSize = 18.sp)
            if (won) {
                if (hintsUsed == 0) {
                    Text("Чистая победа без подсказок", fontSize = 14.sp)
                } else {
                    Text("Подсказок: $hintsUsed (+${hintsUsed * HINT_PENALTY_SECONDS} сек)", fontSize = 14.sp)
                }
                if (bonus != null) {
                    Spacer(Modifier.height(12.dp))
                    Text("ОПЫТ:", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    for (line in bonus.breakdown.lines) {
                        Text(
                            "${line.label}  +${line.amount}",
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        "ИТОГО  +${bonus.breakdown.total}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    for (a in bonus.newAchievements) {
                        Text("🏅 ${a.title} — ${a.description}", fontSize = 13.sp, color = Color(0xFF806020))
                    }
                    bonus.rankUp?.let { rank ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "⭐ НОВОЕ ЗВАНИЕ: ${rank.name}!",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF802020)
                        )
                    }
                }
            } else {
                Text("Поле было решаемо — ход где-то был", fontSize = 14.sp)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { controller.restart() }) {
                Text("Ещё раз")
            }
        }
    }
}

internal fun formatTime(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

private fun vibrate(context: Context) {
    try {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(400)
        }
    } catch (_: Exception) {
        // вибрация недоступна — игра продолжается без неё
    }
}
