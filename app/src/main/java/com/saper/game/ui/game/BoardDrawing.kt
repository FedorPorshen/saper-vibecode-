package com.saper.game.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.saper.game.game.Board
import com.saper.game.game.CellState

/** Классическая палитра виндового сапёра. */
internal object ClassicColors {
    val GrayBg = Color(0xFFBDBDBD)
    val BevelLight = Color(0xFFFFFFFF)
    val BevelDark = Color(0xFF808080)
    val OpenBorder = Color(0xFF808080)
    val Red = Color(0xFFFF0000)
    val Black = Color(0xFF000000)
    val Yellow = Color(0xFFFFE100)
}

/** Цвета цифр 1..8 — как в оригинале. */
private val NumberColors = listOf(
    Color(0xFF0000FF), Color(0xFF008000), Color(0xFFFF0000), Color(0xFF000080),
    Color(0xFF800000), Color(0xFF008080), Color(0xFF000000), Color(0xFF808080)
)

private val NumberPixels = listOf(
    listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
    listOf("01110", "11011", "00011", "00110", "01100", "11000", "11111"),
    listOf("11110", "00011", "00011", "01110", "00011", "00011", "11110"),
    listOf("00110", "01110", "11010", "11010", "11111", "00010", "00010"),
    listOf("11111", "11000", "11000", "11110", "00011", "00011", "11110"),
    listOf("01110", "11000", "11000", "11110", "11011", "11011", "01110"),
    listOf("11111", "00011", "00110", "00110", "01100", "01100", "01100"),
    listOf("01110", "11011", "11011", "01110", "11011", "11011", "01110")
)

private val MinePixels = listOf(
    "000010000", "010010010", "001111100", "001011100", "111111111",
    "001111100", "001111100", "010010010", "000010000"
)

private val FlagPixels = listOf(
    "000RRK000", "00RRRK000", "0RRRRK000", "000RRK000", "00000K000",
    "00000K000", "0000KKK00", "00KKKKKK0", "00KKKKKK0"
)

private fun DrawScope.drawCellPixels(
    rows: List<String>, color: Color, left: Float, top: Float, cell: Float,
    pixel: Float, alpha: Float = 1f
) {
    val x0 = left + (cell - rows[0].length * pixel) / 2f
    val y0 = top + (cell - rows.size * pixel) / 2f
    for ((y, row) in rows.withIndex()) {
        for ((x, value) in row.withIndex()) {
            if (value == '0') continue
            drawRect(
                if (value == 'R') ClassicColors.Red else color,
                Offset(x0 + x * pixel, y0 + y * pixel), Size(pixel, pixel), alpha
            )
        }
    }
}

/** Рисует всё поле: клетки, цифры, флажки, мины, подсветку лампы, превью нажатия и анимации. */
internal fun DrawScope.drawClassicBoard(
    controller: GameController,
    cellPx: Float,
    nowNs: Long,
    showAnalysis: Boolean
) {
    val board = controller.engine?.board
    val cols = board?.width ?: controller.difficulty.width
    val rows = board?.height ?: controller.difficulty.height
    val lost = controller.phase == Phase.LOST
    val lostAt = controller.engine?.lostAt
    val depressCells = computeDepressed(controller, board)

    for (y in 0 until rows) {
        for (x in 0 until cols) {
            val left = x * cellPx
            val top = y * cellPx
            val cell = board?.get(x, y)
            val isMine = cell?.isMine == true
            val state = cell?.state ?: CellState.CLOSED
            val revealedMine = lost && isMine && state != CellState.FLAGGED
            when {
                revealedMine -> drawMine(left, top, cellPx, hit = lostAt == (x to y))
                state == CellState.OPEN -> drawOpen(left, top, cellPx, cell!!.adjacentMines, openProgress(controller, x, y, nowNs))
                else -> {
                    val flagged = state == CellState.FLAGGED
                    if (!flagged && (x to y) in depressCells) {
                        drawDepressed(left, top, cellPx)
                    } else {
                        drawClosed(
                            left, top, cellPx,
                            flagged = flagged,
                            hint = controller.lampHint == (x to y),
                            wrongFlag = showAnalysis && lost && !isMine && flagged,
                            flagAlpha = flagProgress(controller, x, y, nowNs)
                        )
                    }
                }
            }
        }
    }

    // Объяснение лампы: цифры-участники вывода — синим, известные мины — красным
    val reason = controller.lampReason
    if (reason != null && controller.lampHint != null) {
        val bw = (cellPx * 0.09f).coerceAtLeast(2f)
        for ((px, py) in reason.numberCells) {
            drawRect(Color(0xFF2040D0), topLeft = Offset(px * cellPx, py * cellPx), size = Size(cellPx, cellPx), style = Stroke(bw))
        }
        for ((px, py) in reason.mineCells) {
            drawRect(ClassicColors.Red, topLeft = Offset(px * cellPx, py * cellPx), size = Size(cellPx, cellPx), style = Stroke(bw))
        }
    }
}

/** «Утопленные» клетки под пальцем: сама нажимаемая клетка или её соседи при аккорде. */
private fun computeDepressed(controller: GameController, board: Board?): Set<Pair<Int, Int>> {
    val pressed = controller.pressCell ?: return emptySet()
    if (controller.phase != Phase.PLAYING && controller.phase != Phase.WAITING_FIRST_CLICK) return emptySet()
    val b = board ?: return emptySet()
    if (!b.inBounds(pressed.first, pressed.second)) return emptySet()
    val pc = b[pressed.first, pressed.second]
    return when (pc.state) {
        CellState.CLOSED -> setOf(pressed)
        CellState.OPEN -> if (pc.adjacentMines > 0) {
            b.neighborPositions(pressed.first, pressed.second)
                .filter { b[it.first, it.second].state == CellState.CLOSED }
                .toSet()
        } else {
            emptySet()
        }
        CellState.FLAGGED -> emptySet()
    }
}

/** Прогресс волны открытия клетки: 0 — ещё не открылась, 1 — уже обычная. */
private fun openProgress(controller: GameController, x: Int, y: Int, nowNs: Long): Float {
    val start = controller.openAnimStartNs
    if (start == 0L) return 1f
    val dist = controller.openAnimDist[x to y] ?: return 1f
    val t = (nowNs - start) / 1e9f - dist * 0.03f
    return (t / 0.16f).coerceIn(0f, 1f)
}

/** Прогресс появления флажка. */
private fun flagProgress(controller: GameController, x: Int, y: Int, nowNs: Long): Float {
    val start = controller.flagAnimStartNs
    if (start == 0L || (x to y) !in controller.flagAnimCells) return 1f
    return (((nowNs - start) / 1e9f) / 0.18f).coerceIn(0f, 1f)
}

private fun DrawScope.drawClosed(left: Float, top: Float, cell: Float, flagged: Boolean, hint: Boolean, wrongFlag: Boolean, flagAlpha: Float) {
    drawRect(ClassicColors.GrayBg, topLeft = Offset(left, top), size = Size(cell, cell))
    val bevel = cell * 0.11f
    val right = left + cell
    val bottom = top + cell
    drawPath(Path().apply {
        moveTo(left, bottom)
        lineTo(left, top)
        lineTo(right, top)
        lineTo(right - bevel, top + bevel)
        lineTo(left + bevel, top + bevel)
        lineTo(left + bevel, bottom - bevel)
        close()
    }, ClassicColors.BevelLight)
    drawPath(Path().apply {
        moveTo(left, bottom)
        lineTo(left + bevel, bottom - bevel)
        lineTo(right - bevel, bottom - bevel)
        lineTo(right - bevel, top + bevel)
        lineTo(right, top)
        lineTo(right, bottom)
        close()
    }, ClassicColors.BevelDark)
    if (hint) {
        drawRect(ClassicColors.Yellow, topLeft = Offset(left, top), size = Size(cell, cell), style = Stroke(cell * 0.09f))
    }
    if (flagged) {
        drawCellPixels(FlagPixels, ClassicColors.Black, left, top, cell, cell * 0.077f, flagAlpha)
        if (wrongFlag) {
            drawLine(ClassicColors.Red, Offset(left + cell * 0.12f, top + cell * 0.12f), Offset(left + cell * 0.88f, top + cell * 0.88f), strokeWidth = cell * 0.08f)
            drawLine(ClassicColors.Red, Offset(left + cell * 0.88f, top + cell * 0.12f), Offset(left + cell * 0.12f, top + cell * 0.88f), strokeWidth = cell * 0.08f)
        }
    }
}

/** Нажатая клетка: плоская, как открытая, но без цифры. */
private fun DrawScope.drawDepressed(left: Float, top: Float, cell: Float) {
    drawRect(ClassicColors.GrayBg, topLeft = Offset(left, top), size = Size(cell, cell))
    drawRect(ClassicColors.OpenBorder, topLeft = Offset(left, top), size = Size(cell, cell), style = Stroke(1.5f))
}

private fun DrawScope.drawOpen(left: Float, top: Float, cell: Float, number: Int, progress: Float) {
    drawRect(ClassicColors.GrayBg, topLeft = Offset(left, top), size = Size(cell, cell))
    drawRect(ClassicColors.OpenBorder, topLeft = Offset(left, top), size = Size(cell, cell), style = Stroke(1.5f))
    if (number > 0) {
        val pixel = cell * 0.10f * (0.55f + 0.45f * progress)
        drawCellPixels(NumberPixels[number - 1], NumberColors[number - 1], left, top, cell, pixel, progress)
    }
    if (progress < 1f) {
        // белая вспышка, гаснущая по мере «проявления» клетки
        drawRect(ClassicColors.BevelLight.copy(alpha = (1f - progress) * 0.55f), topLeft = Offset(left, top), size = Size(cell, cell))
    }
}

private fun DrawScope.drawMine(left: Float, top: Float, cell: Float, hit: Boolean) {
    val bg = if (hit) ClassicColors.Red else ClassicColors.GrayBg
    drawRect(bg, topLeft = Offset(left, top), size = Size(cell, cell))
    drawRect(ClassicColors.OpenBorder, topLeft = Offset(left, top), size = Size(cell, cell), style = Stroke(1.5f))
    drawCellPixels(MinePixels, ClassicColors.Black, left, top, cell, cell * 0.082f)
}
