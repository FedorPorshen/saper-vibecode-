package com.saper.game.game

import kotlin.math.abs
import kotlin.random.Random

/**
 * Строит поля, решаемые чистой логикой от первого клика.
 *
 * Стратегия: расставить мины случайно → прогнать солвером; если солвер застрял —
 * перенести «виновную» мину из зависшего района в свободное место и проверить снова;
 * если переносы не помогают — расставить всё заново.
 * Нечестное поле не возвращается никогда: вместо тихого отката (как в питон-версии)
 * генератор честно падает с ошибкой — этого на классических сложностях не случается.
 */
object BoardGenerator {

    private const val MAX_RELOCATIONS = 200
    private const val MAX_REROLLS = 200

    fun generate(
        difficulty: Difficulty,
        firstX: Int,
        firstY: Int,
        random: Random = Random.Default
    ): Board {
        val board = Board(difficulty.width, difficulty.height, difficulty.mines)
        var rerolls = 0
        while (true) {
            board.placeMines(firstX, firstY, random)
            rerolls++
            var relocations = 0
            while (relocations < MAX_RELOCATIONS) {
                val solver = Solver(board)
                if (solver.isSolvableFrom(firstX, firstY)) return board
                relocateCulpritMine(board, solver, firstX, firstY, random)
                relocations++
            }
            check(rerolls < MAX_REROLLS) { "Генератор не смог построить честное поле — это баг, сообщите разработчику" }
        }
    }

    /**
     * Переносит одну мину из зависшего района в свободное место.
     * «Виновные» — мины рядом с клетками ограничений, на которых солвер застрял.
     */
    private fun relocateCulpritMine(board: Board, solver: Solver, firstX: Int, firstY: Int, random: Random) {
        val stuck = solver.stuckCells.toHashSet()
        val culprits = ArrayList<Pair<Int, Int>>()
        for (i in stuck) {
            val sx = i % board.width
            val sy = i / board.width
            for ((nx, ny) in board.neighborPositions(sx, sy)) {
                if (board[nx, ny].isMine) culprits.add(nx to ny)
            }
        }
        val from = when {
            culprits.isNotEmpty() -> culprits[random.nextInt(culprits.size)]
            else -> {
                val allMines = ArrayList<Pair<Int, Int>>()
                for (y in 0 until board.height) {
                    for (x in 0 until board.width) {
                        if (board[x, y].isMine) allMines.add(x to y)
                    }
                }
                allMines[random.nextInt(allMines.size)]
            }
        }

        // Куда переносить: любая свободная клетка вне зоны первого клика;
        // в приоритете — подальше от зависшего района, чтобы не воссоздать ту же пробку.
        val free = ArrayList<Pair<Int, Int>>()
        val farFree = ArrayList<Pair<Int, Int>>()
        for (y in 0 until board.height) {
            for (x in 0 until board.width) {
                if (board[x, y].isMine) continue
                if (abs(x - firstX) <= 1 && abs(y - firstY) <= 1) continue
                free.add(x to y)
                val nearStuck = board.neighborPositions(x, y).any { (nx, ny) -> ny * board.width + nx in stuck }
                if (!nearStuck) farFree.add(x to y)
            }
        }
        if (free.isEmpty()) return
        val pool = if (farFree.isNotEmpty()) farFree else free
        val to = pool[random.nextInt(pool.size)]

        board[from.first, from.second].isMine = false
        board[to.first, to.second].isMine = true
        board.recomputeAdjacency()
    }
}
