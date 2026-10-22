package com.saper.game.game

/** Жизненный цикл партии. */
enum class GameState { NOT_STARTED, PLAYING, WON, LOST }

/** Что произошло после хода игрока — интерфейсу это нужно для анимаций и вибрации. */
sealed class MoveResult {
    object NoAction : MoveResult() // ход ни на что не повлиял
    data class Opened(val cells: List<Pair<Int, Int>>) : MoveResult()
    data class MineHit(val x: Int, val y: Int) : MoveResult()
    data class FlagToggled(val x: Int, val y: Int, val nowFlagged: Boolean) : MoveResult()
}

/** Результат лопаты — платной безопасной подсказки. */
sealed class ShovelResult {
    object Unavailable : ShovelResult()
    data class Flagged(val x: Int, val y: Int) : ShovelResult()
    data class Opened(val cells: List<Pair<Int, Int>>) : ShovelResult()
}

/** Результат лампы — платной подсветки гарантированно безопасной клетки с объяснением. */
sealed class LampResult {
    object Unavailable : LampResult()
    object NoLogicalMove : LampResult()
    data class Highlight(val x: Int, val y: Int, val reason: HintReason) : LampResult()
}

/**
 * Правила игры: хранит поле и реагирует на ходы.
 * Никакой графики — чистая логика, поэтому её легко проверять автотестами.
 */
class GameEngine(val difficulty: Difficulty) {

    var board: Board? = null
        private set

    var state = GameState.NOT_STARTED
        private set

    /** Клетка, на которой игрок подорвался — для подсветки после проигрыша. */
    var lostAt: Pair<Int, Int>? = null
        private set

    /** Осталось зарядов подсказок (лампа/лопата) на эту партию. */
    var hintCharges = difficulty.hintCharges
        private set

    val isFinished: Boolean
        get() = state == GameState.WON || state == GameState.LOST

    /** Подключает заранее сгенерированное поле и сразу делает первый клик. */
    fun startWithBoard(board: Board, startX: Int, startY: Int): MoveResult {
        this.board = board
        state = GameState.PLAYING
        return open(startX, startY)
    }

    /** Тап по клетке: закрытая — открыть, открытая цифра — аккорд. */
    fun open(x: Int, y: Int): MoveResult {
        if (isFinished) return MoveResult.NoAction
        val b = board ?: return MoveResult.NoAction
        val cell = b[x, y]
        return when (cell.state) {
            CellState.FLAGGED -> MoveResult.NoAction
            CellState.OPEN -> chord(x, y)
            CellState.CLOSED ->
                if (cell.isMine) {
                    hitMine(x, y)
                } else {
                    val opened = floodOpen(b, x, y)
                    checkWin()
                    MoveResult.Opened(opened)
                }
        }
    }

    /** Долгое нажатие: поставить или снять флажок. По открытой клетке ничего. */
    fun toggleFlag(x: Int, y: Int): MoveResult {
        if (isFinished) return MoveResult.NoAction
        val b = board ?: return MoveResult.NoAction
        val cell = b[x, y]
        if (cell.state == CellState.OPEN) return MoveResult.NoAction
        cell.state = if (cell.state == CellState.CLOSED) CellState.FLAGGED else CellState.CLOSED
        return MoveResult.FlagToggled(x, y, cell.state == CellState.FLAGGED)
    }

    /**
     * Аккорд: клик по открытой цифре. Если флажков вокруг ровно столько,
     * сколько мин, — открывает все закрытые соседи. Ошибся с флажком — взрыв.
     */
    private fun chord(x: Int, y: Int): MoveResult {
        val b = board ?: return MoveResult.NoAction
        val cell = b[x, y]
        if (cell.adjacentMines == 0) return MoveResult.NoAction
        val flaggedAround = b.neighborPositions(x, y)
            .count { p -> b[p.first, p.second].state == CellState.FLAGGED }
        if (flaggedAround != cell.adjacentMines) return MoveResult.NoAction
        val neighbors = b.neighborPositions(x, y)
        val hidden = neighbors.filter { p -> b[p.first, p.second].state == CellState.CLOSED }
        // Сначала проверяем мины, потом открываем — чтобы не «частично открыть» партию
        for ((nx, ny) in hidden) {
            if (b[nx, ny].isMine) return hitMine(nx, ny)
        }
        val opened = ArrayList<Pair<Int, Int>>()
        for ((nx, ny) in hidden) opened += floodOpen(b, nx, ny)
        if (opened.isEmpty()) return MoveResult.NoAction
        checkWin()
        return MoveResult.Opened(opened)
    }

    /**
     * ЛОПАТА: платная безопасная подсказка на закрытой клетке.
     * Мина — ставится флажок, безопасная — открывается. Заряд тратится всегда.
     */
    fun shovel(x: Int, y: Int): ShovelResult {
        val b = board
        if (isFinished || b == null || hintCharges <= 0) return ShovelResult.Unavailable
        val cell = b[x, y]
        if (cell.state != CellState.CLOSED) return ShovelResult.Unavailable
        hintCharges--
        return if (cell.isMine) {
            cell.state = CellState.FLAGGED
            ShovelResult.Flagged(x, y)
        } else {
            val opened = floodOpen(b, x, y)
            checkWin()
            ShovelResult.Opened(opened)
        }
    }

    /**
     * ЛАМПА: платная подсказка — подсвечивает закрытую клетку, безопасность
     * которой доказывается логикой по текущей позиции. Флажки игрока не
     * учитываются: вывод строится только по открытым цифрам.
     */
    fun lamp(): LampResult {
        val b = board ?: return LampResult.Unavailable
        if (isFinished || hintCharges <= 0) return LampResult.Unavailable
        val hint = Solver(b).findSafeHint { x, y -> b[x, y].state == CellState.OPEN }
            ?: return LampResult.NoLogicalMove
        hintCharges--
        return LampResult.Highlight(hint.x, hint.y, hint.reason)
    }

    private fun hitMine(x: Int, y: Int): MoveResult {
        val b = board!!
        b[x, y].state = CellState.OPEN
        state = GameState.LOST
        lostAt = x to y
        return MoveResult.MineHit(x, y)
    }

    /** Заливка: открывает клетку; если вокруг неё 0 мин — открывает и соседей, и так по цепочке. */
    private fun floodOpen(b: Board, x: Int, y: Int): List<Pair<Int, Int>> {
        val opened = ArrayList<Pair<Int, Int>>()
        if (b[x, y].state == CellState.OPEN) return opened
        b[x, y].state = CellState.OPEN
        opened.add(x to y)
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(x to y)
        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()
            if (b[cx, cy].adjacentMines == 0) {
                for ((nx, ny) in b.neighborPositions(cx, cy)) {
                    val neighbor = b[nx, ny]
                    if (neighbor.state == CellState.CLOSED) {
                        neighbor.state = CellState.OPEN
                        opened.add(nx to ny)
                        queue.add(nx to ny)
                    }
                }
            }
        }
        return opened
    }

    private fun checkWin() {
        val b = board ?: return
        for (row in b.cells) {
            for (cell in row) {
                if (!cell.isMine && cell.state != CellState.OPEN) return
            }
        }
        state = GameState.WON
        // Праздничный штрих: на оставшиеся мины сами встают флажки
        for (row in b.cells) {
            for (cell in row) {
                if (cell.isMine && cell.state != CellState.FLAGGED) cell.state = CellState.FLAGGED
            }
        }
    }
}
