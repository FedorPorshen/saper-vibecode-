package com.saper.game.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {

    private fun countOpened(engine: GameEngine): Int =
        engine.board!!.cells.sumOf { row -> row.count { it.state == CellState.OPEN } }

    // ---------- Генерация ----------

    @Test
    fun `зона 3x3 вокруг первого клика всегда чистая и число мин верное`() {
        repeat(50) { seed ->
            val board = Board(9, 9, 10)
            board.placeMines(4, 4, Random(seed))
            for (y in 3..5) {
                for (x in 3..5) {
                    assertFalse("seed=$seed, клетка ($x,$y)", board[x, y].isMine)
                }
            }
            assertEquals(10, board.minesOnBoard())
        }
    }

    // ---------- Открытие, заливка, победа ----------

    @Test
    fun `заливка открывает всё поле с единственной миной в углу и побеждает`() {
        val engine = GameEngine(Difficulty.NOVICE)
        val board = Board(5, 5, 1)
        board.setupMines(listOf(4 to 4))
        engine.startWithBoard(board, 0, 0)

        assertEquals(GameState.WON, engine.state)
        assertEquals(24, countOpened(engine))
        assertEquals(CellState.FLAGGED, board[4, 4].state) // победный авто-флажок
    }

    @Test
    fun `заливка открывает все клетки вокруг единственной мины`() {
        val engine = GameEngine(Difficulty.NOVICE)
        val board = Board(3, 3, 1)
        board.setupMines(listOf(0 to 0))
        engine.startWithBoard(board, 2, 2)

        assertEquals(8, countOpened(engine))
        assertEquals(GameState.WON, engine.state)
    }

    @Test
    fun `заливка останавливается на цифрах - стена мин делит поле`() {
        val engine = GameEngine(Difficulty.NOVICE)
        val board = Board(5, 5, 3)
        board.setupMines(listOf(2 to 1, 2 to 2, 2 to 3))
        engine.startWithBoard(board, 0, 4)

        assertEquals(GameState.PLAYING, engine.state)
        assertTrue(countOpened(engine) < 22) // левая часть открыта, правая нет
        assertEquals(CellState.CLOSED, board[4, 4].state)
    }

    @Test
    fun `открытие мины проигрывает партию`() {
        val engine = GameEngine(Difficulty.NOVICE)
        val board = Board(5, 5, 3)
        board.setupMines(listOf(2 to 1, 2 to 2, 2 to 3))
        engine.startWithBoard(board, 0, 4)
        assertEquals(GameState.PLAYING, engine.state)

        val result = engine.open(2, 2)
        assertTrue(result is MoveResult.MineHit)
        assertEquals(GameState.LOST, engine.state)
        assertEquals(2 to 2, engine.lostAt)
    }

    @Test
    fun `идеальная игра всегда доводит до победы`() {
        repeat(30) { seed ->
            val engine = GameEngine(Difficulty.NOVICE)
            val board = Board(9, 9, 10)
            board.placeMines(4, 4, Random(seed))
            engine.startWithBoard(board, 4, 4)

            var guard = 0
            while (!engine.isFinished && guard++ < 1000) {
                var target: Pair<Int, Int>? = null
                scan@ for (y in 0 until 9) {
                    for (x in 0 until 9) {
                        if (!board[x, y].isMine && board[x, y].state == CellState.CLOSED) {
                            target = x to y
                            break@scan
                        }
                    }
                }
                if (target == null) break
                engine.open(target.first, target.second)
            }
            assertEquals("seed=$seed", GameState.WON, engine.state)
        }
    }

    // ---------- Флажки ----------

    @Test
    fun `флажок ставится и снимается а на открытой клетке запрещён`() {
        val engine = GameEngine(Difficulty.NOVICE)
        val board = Board(5, 5, 3)
        board.setupMines(listOf(2 to 1, 2 to 2, 2 to 3))
        engine.startWithBoard(board, 0, 4)

        engine.toggleFlag(4, 0)
        assertEquals(CellState.FLAGGED, board[4, 0].state)
        engine.toggleFlag(4, 0)
        assertEquals(CellState.CLOSED, board[4, 0].state)

        val onOpen = engine.toggleFlag(0, 4)
        assertEquals(MoveResult.NoAction, onOpen)
    }

    // ---------- Аккорд ----------

    @Test
    fun `аккорд с верными флажками открывает соседей и побеждает`() {
        val engine = GameEngine(Difficulty.NOVICE)
        val board = Board(2, 2, 1)
        board.setupMines(listOf(0 to 0))
        engine.startWithBoard(board, 1, 1) // открывается одна цифра "1"
        assertEquals(GameState.PLAYING, engine.state)

        engine.toggleFlag(0, 0)
        val result = engine.open(1, 1) // аккорд

        assertTrue(result is MoveResult.Opened)
        assertEquals(GameState.WON, engine.state)
    }

    @Test
    fun `аккорд с неверными флажками взрывает пропущенную мину`() {
        val engine = GameEngine(Difficulty.NOVICE)
        val board = Board(3, 3, 2)
        board.setupMines(listOf(0 to 0, 0 to 1))
        engine.startWithBoard(board, 1, 0) // открылась цифра "2"

        engine.toggleFlag(0, 0) // верный флажок на мине
        engine.toggleFlag(1, 1) // неверный флажок на безопасной соседке
        val result = engine.open(1, 0) // аккорд: флажков вокруг два, но мина (0,1) не помечена

        assertTrue(result is MoveResult.MineHit)
        assertEquals(GameState.LOST, engine.state)
        assertEquals(0 to 1, engine.lostAt)
    }

    // ---------- Лопата ----------

    @Test
    fun `лопата разминирует безопасно и тратит заряды`() {
        val engine = GameEngine(Difficulty.NOVICE)
        val board = Board(2, 2, 1)
        board.setupMines(listOf(0 to 0))
        engine.startWithBoard(board, 1, 1) // открыта цифра "1", мина (0,0) закрыта

        val safe = engine.shovel(0, 1) // безопасная клетка
        assertTrue(safe is ShovelResult.Opened)
        assertEquals(CellState.OPEN, board[0, 1].state)
        assertEquals(1, engine.hintCharges)

        val mine = engine.shovel(0, 0) // мина: должна стать флажком, без взрыва
        assertTrue(mine is ShovelResult.Flagged)
        assertEquals(CellState.FLAGGED, board[0, 0].state)
        assertEquals(GameState.PLAYING, engine.state)
        assertEquals(0, engine.hintCharges)

        val noCharges = engine.shovel(1, 0)
        assertTrue(noCharges is ShovelResult.Unavailable)
    }

    @Test
    fun `лопата работает только с закрытыми клетками`() {
        val engine = GameEngine(Difficulty.NOVICE)
        val board = Board(2, 2, 1)
        board.setupMines(listOf(0 to 0))
        engine.startWithBoard(board, 1, 1)

        val onOpen = engine.shovel(1, 1) // уже открыта
        assertTrue(onOpen is ShovelResult.Unavailable)
        assertEquals(2, engine.hintCharges) // заряд не потрачен
    }
}
