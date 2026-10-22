package com.saper.game.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SolverAndGeneratorTest {

    @Test
    fun `солвер проходит поле из одной мины заливкой`() {
        val board = Board(3, 3, 1)
        board.setupMines(listOf(2 to 2))
        assertTrue(Solver(board).isSolvableFrom(0, 0))
    }

    @Test
    fun `солвер признаёт заведомо нерешаемое поле`() {
        // 7x2, мины в правом углу: фронт даёт «1 мина среди двух клеток» — 50/50 без выведения
        val board = Board(7, 2, 2)
        board.setupMines(listOf(5 to 1, 6 to 0))
        assertFalse(Solver(board).isSolvableFrom(0, 0))
    }

    @Test
    fun `лампа решает паттерн 1-2-1 через правило подмножеств и объясняет ход`() {
        // верхний ряд открыт: 1 2 1, под ним три закрытые клетки, мины по краям
        val board = Board(3, 2, 2)
        board.setupMines(listOf(0 to 1, 2 to 1))
        val opened = setOf(0 to 0, 1 to 0, 2 to 0)
        val hint = Solver(board).findSafeHint { x, y -> (x to y) in opened }
        assertEquals(1 to 1, hint?.let { it.x to it.y })
        assertTrue(hint!!.reason.text.isNotBlank())
        assertTrue(hint.reason.numberCells.isNotEmpty())
    }

    @Test
    fun `генератор строит только честные поля на всех сложностях`() {
        val plan = listOf(
            Difficulty.NOVICE to 20,
            Difficulty.AMATEUR to 3,
            Difficulty.PRO to 1
        )
        for ((difficulty, times) in plan) {
            repeat(times) { seed ->
                val fx = difficulty.width / 2
                val fy = difficulty.height / 2
                val board = BoardGenerator.generate(difficulty, fx, fy, Random(seed))
                assertEquals("seed=$seed мин", difficulty.mines, board.minesOnBoard())
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        assertFalse("seed=$seed зона первого клика", board[fx + dx, fy + dy].isMine)
                    }
                }
                assertTrue("seed=$seed честность ${difficulty.title}", Solver(board).isSolvableFrom(fx, fy))
            }
        }
    }
}
