package com.saper.game.game

/**
 * Дедукционный солвер: играет в сапёр, пользуясь только логикой, без угадывания.
 *
 * Правила вывода:
 *  1. Тривиальные: если у открытой цифры мин уже хватает — все закрытые соседи безопасны;
 *     если закрытых соседей ровно столько, сколько не хватает мин — все они мины.
 *  2. Подмножества: если клетки одного ограничения целиком лежат внутри другого,
 *     разность тоже является ограничением (решает паттерны 1-2-1, 1-2-2-1 и подобные).
 *
 * Использётся генератором (проверка честности поля) и лампой (поиск безопасного хода).
 * Один экземпляр солвера — один запрос.
 */

/** Обоснование подсказки: человеческий текст + клетки для подсветки. */
data class HintReason(
    val text: String,
    /** Цифры, участвовавшие в выводе, — подсвечиваются синим. */
    val numberCells: List<Pair<Int, Int>> = emptyList(),
    /** Известные мины, участвовавшие в выводе, — подсвечиваются красным. */
    val mineCells: List<Pair<Int, Int>> = emptyList()
)

/** Безопасная клетка и объяснение, почему она безопасна. */
data class Hint(val x: Int, val y: Int, val reason: HintReason)

class Solver(private val board: Board) {

    private enum class Knowledge { UNKNOWN, SAFE, MINE }

    /** Ограничение: среди клеток cells ровно mineCount мин (origin — цифра, породившая его). */
    private class Constraint(
        val cells: List<Int>,
        val mineCount: Int,
        val origin: Int,
        val number: Int
    )

    private val w = board.width
    private val h = board.height
    private val knowledge = Array(h) { Array(w) { Knowledge.UNKNOWN } }
    private val opened = Array(h) { BooleanArray(w) }
    private var openedCount = 0
    private val safeReasons = HashMap<Int, HintReason>()

    /** Клетки ограничений, на которых солвер застрял в последний раз (индексы y*w+x). Нужно генератору. */
    var stuckCells: List<Int> = emptyList()
        private set

    /** Решается ли поле чистой логикой, если единственная открытая клетка — (startX, startY). */
    fun isSolvableFrom(startX: Int, startY: Int): Boolean {
        val toOpen = ArrayDeque<Int>()
        setSafe(idx(startX, startY), toOpen)
        while (true) {
            while (toOpen.isNotEmpty()) openCell(toOpen.removeFirst(), toOpen)
            if (openedCount == board.totalSafeCells) return true
            if (!deduce(toOpen)) return false
        }
    }

    /**
     * ЛАМПА: ищет закрытую клетку, безопасность которой доказывается логикой
     * для уже открытого множества клеток, и объясняет, почему она безопасна.
     * Логического хода нет — null. Флажки игрока сознательно игнорируются:
     * вывод строится только по цифрам, поэтому чужие ошибочные флажки подсказку не ломают.
     */
    fun findSafeHint(isOpened: (x: Int, y: Int) -> Boolean): Hint? {
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (isOpened(x, y)) {
                    opened[y][x] = true
                    knowledge[y][x] = Knowledge.SAFE
                    openedCount++
                }
            }
        }
        val sink = ArrayDeque<Int>()
        while (deduce(sink)) { /* крутим дедукцию, пока выводится новое */ }
        for (i in 0 until w * h) {
            val x = xOf(i)
            val y = yOf(i)
            if (knowledge[y][x] == Knowledge.SAFE && !opened[y][x]) {
                return Hint(x, y, safeReasons[i] ?: HintReason("Клетка безопасна."))
            }
        }
        return null
    }

    // --- внутреннее ---

    private fun idx(x: Int, y: Int) = y * w + x
    private fun xOf(i: Int) = i % w
    private fun yOf(i: Int) = i / w

    private fun setSafe(i: Int, queue: ArrayDeque<Int>): Boolean {
        val x = xOf(i)
        val y = yOf(i)
        if (opened[y][x] || knowledge[y][x] != Knowledge.UNKNOWN) return false
        knowledge[y][x] = Knowledge.SAFE
        queue.add(i)
        return true
    }

    private fun setMine(i: Int): Boolean {
        val x = xOf(i)
        val y = yOf(i)
        if (knowledge[y][x] != Knowledge.UNKNOWN) return false
        knowledge[y][x] = Knowledge.MINE
        return true
    }

    private fun openCell(i: Int, queue: ArrayDeque<Int>) {
        val x = xOf(i)
        val y = yOf(i)
        if (opened[y][x]) return
        opened[y][x] = true
        openedCount++
        knowledge[y][x] = Knowledge.SAFE
        if (board[x, y].adjacentMines == 0) {
            for ((nx, ny) in board.neighborPositions(x, y)) {
                if (!opened[ny][nx] && knowledge[ny][nx] == Knowledge.UNKNOWN) {
                    setSafe(idx(nx, ny), queue)
                }
            }
        }
    }

    private fun neighborIndices(i: Int): List<Int> {
        val x = xOf(i)
        val y = yOf(i)
        return board.neighborPositions(x, y).map { (nx, ny) -> idx(nx, ny) }
    }

    /** Один раунд дедукции. true — удалось вывести что-то новое. */
    private fun deduce(queue: ArrayDeque<Int>): Boolean {
        val constraints = ArrayList<Constraint>()
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (!opened[y][x]) continue
                val number = board[x, y].adjacentMines
                if (number == 0) continue
                var unknowns: ArrayList<Int>? = null
                var knownMines = 0
                for ((nx, ny) in board.neighborPositions(x, y)) {
                    when (knowledge[ny][nx]) {
                        Knowledge.MINE -> knownMines++
                        Knowledge.UNKNOWN -> {
                            if (unknowns == null) unknowns = ArrayList()
                            unknowns.add(idx(nx, ny))
                        }
                        Knowledge.SAFE -> {}
                    }
                }
                val cells = unknowns ?: continue
                constraints.add(Constraint(cells, number - knownMines, idx(x, y), number))
            }
        }
        if (constraints.isEmpty()) return false
        stuckCells = constraints.flatMap { it.cells }

        // 1. Тривиальные правила
        var progress = false
        for (c in constraints) {
            if (c.mineCount == 0) {
                for (i in c.cells) {
                    if (setSafe(i, queue)) {
                        progress = true
                        val origin = c.origin
                        val mineNeighbors = neighborIndices(origin)
                            .filter { knowledge[yOf(it)][xOf(it)] == Knowledge.MINE }
                            .map { xOf(it) to yOf(it) }
                        val ox = xOf(origin) + 1
                        val oy = yOf(origin) + 1
                        safeReasons[i] = HintReason(
                            "Все ${c.number} мин вокруг цифры (${ox},${oy}) уже найдены — остальные её соседи чисты.",
                            numberCells = listOf(xOf(origin) to yOf(origin)),
                            mineCells = mineNeighbors
                        )
                    }
                }
                if (progress) return true
            } else if (c.mineCount == c.cells.size) {
                for (i in c.cells) progress = setMine(i) || progress
                if (progress) return true
            }
        }

        // 2. Правило подмножеств: меньшее ограничение внутри большего → разность тоже ограничение
        val sets = constraints.map { it.cells.toHashSet() }
        for (a in constraints.indices) {
            for (b in constraints.indices) {
                if (a == b) continue
                val small = constraints[a]
                val big = constraints[b]
                if (small.cells.size >= big.cells.size) continue
                if (!sets[a].all { it in sets[b] }) continue
                val diff = big.cells.filter { it !in sets[a] }
                val diffCount = big.mineCount - small.mineCount
                when {
                    diffCount == 0 -> for (i in diff) {
                        if (setSafe(i, queue)) {
                            progress = true
                            val ax = xOf(small.origin) + 1
                            val ay = yOf(small.origin) + 1
                            val bx = xOf(big.origin) + 1
                            val by = yOf(big.origin) + 1
                            safeReasons[i] = HintReason(
                                "Цифры (${ax},${ay}) и (${bx},${by}) ждут ещё по ${small.mineCount} мин, и все необъяснённые соседи первой есть у второй — мины второй обязаны лежать у первой, остальным её соседям мина не достанется.",
                                numberCells = listOf(xOf(small.origin) to yOf(small.origin), xOf(big.origin) to yOf(big.origin))
                            )
                        }
                    }
                    diffCount == diff.size -> for (i in diff) progress = setMine(i) || progress
                }
                if (progress) return true
            }
        }
        return false
    }
}
