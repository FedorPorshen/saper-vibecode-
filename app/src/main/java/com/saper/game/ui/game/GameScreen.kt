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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saper.game.data.AppSettings
import com.saper.game.data.GameFinishBonus
import com.saper.game.data.ProgressionStore
import com.saper.game.data.SaperStore
import com.saper.game.game.Difficulty
import com.saper.game.game.HINT_PENALTY_SECONDS
import com.saper.game.game.LampResult
import com.saper.game.ui.theme.ClassicTitleBar
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
            .background(WindowPanel)
            .safeDrawingPadding()
            .padding(6.dp)
            .classicBevel()
    ) {
        ClassicTitleBar(difficulty.title, onMenu = { showExitDialog = true })
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
            controller.lampReason?.let { reason ->
                Text(
                    text = "💡 " + reason.text,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .background(Color(0xFFFFF3B0))
                        .border(1.dp, Color(0xFFC0A020))
                        .padding(8.dp)
                )
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
        LedCounter(controller.mineDisplay)
        SmileyButton(controller)
        LedCounter(controller.elapsedSeconds.coerceAtMost(999))
    }
}

/** Табло в стиле виндового сапёра: красные цифры на чёрном. */
@Composable
private fun LedCounter(value: Int) {
    val v = value.coerceIn(-99, 999)
    Box(
        Modifier
            .background(Color.Black)
            .classicBevel(inset = true)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "%03d".format(v),
            color = Color(0xFFFF3333),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            maxLines = 1,
            softWrap = false
        )
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
                        Toast.makeText(context, "Логического хода нет — думай сам", Toast.LENGTH_SHORT).show()
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
