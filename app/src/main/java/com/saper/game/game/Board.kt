package com.saper.game.game

import kotlin.random.Random

/** Игровое поле: сетка клеток, расстановка мин и подсчёт чисел. */
class Board(val width: Int, val height: Int, val mineCount: Int) {

    val cells = Array(height) { Array(width) { Cell() } }

    /** Сколько на поле безопасных клеток — ровно столько нужно открыть для победы. */
    val totalSafeCells = width * height - mineCount

    operator fun get(x: Int, y: Int): Cell = cells[y][x]

    fun inBounds(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    /** Позиции 8 соседей клетки, по диагоналям тоже. */
    fun neighborPositions(x: Int, y: Int): List<Pair<Int, Int>> {
        val result = ArrayList<Pair<Int, Int>>(8)
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (inBounds(nx, ny)) result.add(nx to ny)
            }
        }
        return result
    }

    /**
     * Случайная расстановка мин. Зона 3x3 вокруг (safeX, safeY) всегда чистая:
     * первый клик безопасен и вокруг него есть пространство для развития.
     */
    fun placeMines(safeX: Int, safeY: Int, random: Random) {
        require(mineCount <= width * height - 9) { "Мин больше, чем клеток вне зоны 3x3" }
        val forbidden = HashSet<Int>()
        for (dy in -1..1) {
            for (dx in -1..1) {
                val nx = safeX + dx
                val ny = safeY + dy
                if (inBounds(nx, ny)) forbidden.add(ny * width + nx)
            }
        }
        val candidates = ArrayList<Int>(width * height)
        for (i in 0 until width * height) {
            if (i !in forbidden) candidates.add(i)
        }
        candidates.shuffle(random)
        for (i in 0 until mineCount) {
            val index = candidates[i]
            cells[index / width][index % width].isMine = true
        }
        recomputeAdjacency()
    }

    /** Расстановка мин явным списком координат — используется в тестах. */
    fun setupMines(positions: List<Pair<Int, Int>>) {
        for (row in cells) for (cell in row) cell.isMine = false
        for ((x, y) in positions) cells[y][x].isMine = true
        recomputeAdjacency()
    }

    /** Пересчитывает числа вокруг клеток. */
    fun recomputeAdjacency() {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val cell = cells[y][x]
                cell.adjacentMines =
                    if (cell.isMine) 0
                    else neighborPositions(x, y).count { p -> get(p.first, p.second).isMine }
            }
        }
    }

    fun minesOnBoard(): Int = cells.sumOf { row -> row.count { it.isMine } }
}
