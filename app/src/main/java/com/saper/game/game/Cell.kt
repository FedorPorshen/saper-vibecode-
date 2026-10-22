package com.saper.game.game

/** Состояние одной клетки на поле. */
enum class CellState {
    CLOSED,   // не тронута
    OPEN,     // открыта
    FLAGGED   // помечена флажком как мина
}

/** Одна клетка поля. */
class Cell {
    var isMine = false
    var adjacentMines = 0 // сколько мин в 8 соседних клетках (для мины всегда 0)
    var state = CellState.CLOSED
}
