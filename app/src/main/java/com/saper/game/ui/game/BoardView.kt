package com.saper.game.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** Конфигурация жестов с настраиваемым порогом долгого нажатия (для флажка). */
internal class LongPressViewConfiguration(
    private val base: ViewConfiguration,
    private val longPressMs: Long
) : ViewConfiguration {
    override val longPressTimeoutMillis: Long get() = longPressMs
    override val doubleTapTimeoutMillis: Long get() = base.doubleTapTimeoutMillis
    override val doubleTapMinTimeMillis: Long get() = base.doubleTapMinTimeMillis
    override val touchSlop: Float get() = base.touchSlop
}

/**
 * Поле: рисуется канвой, зумится щипком (от «всё поле на экране» до 4x),
 * двигается пальцем, тап копает (с превью нажатия), долгое нажатие ставит флажок.
 */
@Composable
internal fun BoardView(
    controller: GameController,
    showAnalysis: Boolean,
    longPressMs: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cellPx = with(density) { 36.dp.toPx() }

    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var minScale by remember { mutableStateOf(0.25f) }
    var fitted by remember { mutableStateOf(false) }
    var animFrame by remember { mutableStateOf(0L) }

    val board = controller.engine?.board
    val cols = board?.width ?: controller.difficulty.width
    val rows = board?.height ?: controller.difficulty.height
    val contentW = cols * cellPx
    val contentH = rows * cellPx

    // порог долгого нажатия из настроек подменяется для всех жестов этого поля
    val baseConfig = LocalViewConfiguration.current
    val gameConfig = remember(baseConfig, longPressMs) {
        LongPressViewConfiguration(baseConfig, longPressMs.toLong())
    }

    fun clampOffset() {
        val vw = viewport.width.toFloat()
        val vh = viewport.height.toFloat()
        val dw = contentW * scale
        val dh = contentH * scale
        val ox = if (dw <= vw) (vw - dw) / 2f else offset.x.coerceIn(vw - dw, 0f)
        val oy = if (dh <= vh) (vh - dh) / 2f else offset.y.coerceIn(vh - dh, 0f)
        offset = Offset(ox, oy)
    }

    // при первом измерении экрана вписываем поле целиком
    LaunchedEffect(viewport) {
        if (viewport.width > 0 && viewport.height > 0) {
            val wasFitted = !fitted || scale == minScale
            minScale = min(1f, min(viewport.width.toFloat() / contentW, viewport.height.toFloat() / contentH))
            scale = if (wasFitted) minScale else scale.coerceAtLeast(minScale)
            clampOffset()
            fitted = true
        }
    }

    // пока идёт какая-нибудь анимация — тикаем кадры, чтобы канва перерисовывалась
    LaunchedEffect(controller.openAnimStartNs, controller.flagAnimStartNs, controller.lostAnimStartNs) {
        val deadline = maxOf(
            controller.openAnimStartNs + 1_600_000_000L,
            controller.flagAnimStartNs + 300_000_000L,
            controller.lostAnimStartNs + 600_000_000L
        )
        while (System.nanoTime() < deadline) {
            withFrameNanos { animFrame = it }
        }
    }

    CompositionLocalProvider(LocalViewConfiguration provides gameConfig) {
        Box(
            modifier
                .fillMaxSize()
                .background(ClassicColors.GrayBg)
                .onSizeChanged { viewport = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(minScale, 4f)
                        offset += pan
                        clampOffset()
                    }
                }
                .pointerInput(longPressMs) {
                    detectTapGestures(
                        onPress = { p ->
                            controller.pressCell = hitCell(p, offset, scale, cellPx, cols, rows)
                            tryAwaitRelease()
                            controller.pressCell = null
                        },
                        onTap = { p ->
                            hitCell(p, offset, scale, cellPx, cols, rows)?.let { controller.tap(it.first, it.second) }
                        },
                        onLongPress = { p ->
                            hitCell(p, offset, scale, cellPx, cols, rows)?.let { controller.longPress(it.first, it.second) }
                        }
                    )
                }
                .clipToBounds()
                .drawWithCache {
                    onDrawBehind {
                        animFrame // тик анимации — перерисовка каждый кадр, пока она идёт
                        controller.version // версия игры — перерисовка при любом изменении
                        withTransform({
                            translate(offset.x, offset.y)
                            scale(scale, scale, pivot = Offset.Zero)
                        }) {
                            drawClassicBoard(controller, cellPx, System.nanoTime(), showAnalysis)
                        }
                        val lostStart = controller.lostAnimStartNs
                        if (controller.phase == Phase.LOST && lostStart > 0L) {
                            val p = 1f - (((System.nanoTime() - lostStart) / 1e9f) / 0.5f).coerceIn(0f, 1f)
                            if (p > 0f) drawRect(ClassicColors.Red.copy(alpha = 0.30f * p), size = size)
                        }
                    }
                }
        )
    }
}

/** Экранные координаты → координаты клетки; мимо поля — null. */
private fun hitCell(p: Offset, offset: Offset, scale: Float, cellPx: Float, cols: Int, rows: Int): Pair<Int, Int>? {
    val bx = (p.x - offset.x) / (scale * cellPx)
    val by = (p.y - offset.y) / (scale * cellPx)
    if (bx < 0f || by < 0f) return null
    val x = bx.toInt()
    val y = by.toInt()
    return if (x < cols && y < rows) x to y else null
}
