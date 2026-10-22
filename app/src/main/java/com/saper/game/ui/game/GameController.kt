package com.saper.game.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.saper.game.game.BoardGenerator
import com.saper.game.game.CellState
import com.saper.game.game.Difficulty
import com.saper.game.game.GameEngine
import com.saper.game.game.GameState
import com.saper.game.game.HintReason
import com.saper.game.game.LampResult
import com.saper.game.game.MoveResult
import com.saper.game.game.ShovelResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Фазы партии с точки зрения интерфейса. */
enum class Phase { WAITING_FIRST_CLICK, GENERATING, PLAYING, WON, LOST }

/**
 * Мост между чистой логикой (GameEngine) и интерфейсом: хранит отображаемое
 * состояние в Compose-переменных, гоняет генерацию поля в фоне и ведёт таймер.
 */
class GameController(val difficulty: Difficulty) {

    var engine: GameEngine? = null
        private set

    /** Счётчик версий: любое изменение поля сопровождается version++, и канва перерисовывается. */
    var version by mutableStateOf(0)
        private set
    var phase by mutableStateOf(Phase.WAITING_FIRST_CLICK)
        private set
    var elapsedSeconds by mutableStateOf(0)
        private set
    var lampHint by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    /** Объяснение последней лампы: почему подсвеченная клетка безопасна. */
    var lampReason by mutableStateOf<HintReason?>(null)
        private set
    var shovelArmed by mutableStateOf(false)
        private set
    var hintCharges by mutableStateOf(difficulty.hintCharges)
        private set
    var mineDisplay by mutableStateOf(difficulty.mines)
        private set

    // --- анимационное состояние: заполняется ходами, читается канвой ---

    /** Клетка под пальцем прямо сейчас — «утопленная» подсветка и смайлик 😮. */
    var pressCell by mutableStateOf<Pair<Int, Int>?>(null)

    /** Волна открытия: открытые клетки → расстояние от точки клика. */
    var openAnimDist by mutableStateOf<Map<Pair<Int, Int>, Int>>(emptyMap())
        private set
    var openAnimStartNs by mutableStateOf(0L)
        private set

    /** Только что поставленные флажки — плавное появление. */
    var flagAnimCells by mutableStateOf<Set<Pair<Int, Int>>>(emptySet())
        private set
    var flagAnimStartNs by mutableStateOf(0L)
        private set

    /** Момент взрыва — красная вспышка по всему экрану. */
    var lostAnimStartNs by mutableStateOf(0L)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null
    private var generation = 0

    /** Смайлик: начать заново. */
    fun restart() {
        generation++
        timerJob?.cancel()
        engine = null
        phase = Phase.WAITING_FIRST_CLICK
        elapsedSeconds = 0
        lampHint = null
        lampReason = null
        shovelArmed = false
        hintCharges = difficulty.hintCharges
        mineDisplay = difficulty.mines
        pressCell = null
        openAnimDist = emptyMap()
        openAnimStartNs = 0L
        flagAnimCells = emptySet()
        flagAnimStartNs = 0L
        lostAnimStartNs = 0L
        version++
    }

    /** Тап по клетке: до генерации — первый клик, дальше обычная копка или лопата. */
    fun tap(x: Int, y: Int) {
        lampHint = null
        lampReason = null
        val e = engine
        if (e == null) {
            firstClick(x, y)
            return
        }
        if (phase != Phase.PLAYING) return
        if (shovelArmed) {
            when (val r = e.shovel(x, y)) {
                is ShovelResult.Opened -> {
                    beginOpenAnim(r.cells, x to y)
                    shovelArmed = false // одно использование — лопата убирается
                }
                is ShovelResult.Flagged -> {
                    beginFlagAnim(x to y)
                    shovelArmed = false
                }
                ShovelResult.Unavailable -> {}
            }
            if (e.hintCharges <= 0) shovelArmed = false
        } else {
            when (val r = e.open(x, y)) {
                is MoveResult.Opened -> beginOpenAnim(r.cells, x to y)
                is MoveResult.MineHit -> {
                    phase = Phase.LOST
                    lostAnimStartNs = System.nanoTime()
                }
                else -> {}
            }
        }
        syncPhase()
        syncScalars()
        version++
    }

    /** Долгое нажатие — флажок. */
    fun longPress(x: Int, y: Int) {
        val e = engine ?: return
        if (phase != Phase.PLAYING) return
        when (val r = e.toggleFlag(x, y)) {
            is MoveResult.FlagToggled -> if (r.nowFlagged) beginFlagAnim(x to y)
            else -> {}
        }
        syncScalars()
        version++
    }

    /** Кнопка лампы; результат нужен интерфейсу для тоста «хода нет». */
    fun lamp(): LampResult {
        val e = engine ?: return LampResult.Unavailable
        if (phase != Phase.PLAYING) return LampResult.Unavailable
        val result = e.lamp()
        lampHint = (result as? LampResult.Highlight)?.let { it.x to it.y }
        lampReason = (result as? LampResult.Highlight)?.reason
        syncScalars()
        version++
        return result
    }

    /** Кнопка лопаты — взять в руки/убрать. */
    fun armShovel() {
        val e = engine ?: return
        if (phase != Phase.PLAYING || e.hintCharges <= 0) return
        shovelArmed = !shovelArmed
    }

    fun dispose() {
        generation++
        timerJob?.cancel()
        scope.cancel()
    }

    // --- внутреннее ---

    private fun firstClick(x: Int, y: Int) {
        if (phase != Phase.WAITING_FIRST_CLICK) return
        phase = Phase.GENERATING
        val myGeneration = ++generation
        scope.launch(Dispatchers.Default) {
            val board = BoardGenerator.generate(difficulty, x, y)
            val newEngine = GameEngine(difficulty)
            val firstOpen = newEngine.startWithBoard(board, x, y)
            scope.launch {
                if (myGeneration != generation) return@launch // пока строили, нажали «заново»
                engine = newEngine
                phase = Phase.PLAYING
                if (firstOpen is MoveResult.Opened) beginOpenAnim(firstOpen.cells, x to y)
                syncPhase()
                syncScalars()
                version++
                startTimer()
            }
        }
    }

    private fun beginOpenAnim(cells: List<Pair<Int, Int>>, origin: Pair<Int, Int>) {
        if (cells.isEmpty()) return
        openAnimDist = cells.associateWith { (x, y) ->
            maxOf(abs(x - origin.first), abs(y - origin.second))
        }
        openAnimStartNs = System.nanoTime()
    }

    private fun beginFlagAnim(vararg cells: Pair<Int, Int>) {
        flagAnimCells = cells.toSet()
        flagAnimStartNs = System.nanoTime()
    }

    private fun syncPhase() {
        when (engine?.state) {
            GameState.WON -> phase = Phase.WON
            GameState.LOST -> phase = Phase.LOST
            else -> return
        }
        timerJob?.cancel()
    }

    private fun syncScalars() {
        val e = engine
        hintCharges = e?.hintCharges ?: difficulty.hintCharges
        mineDisplay = if (e?.board != null) {
            difficulty.mines - e.board!!.cells.sumOf { row -> row.count { it.state == CellState.FLAGGED } }
        } else {
            difficulty.mines
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                if (phase == Phase.PLAYING) elapsedSeconds++
            }
        }
    }
}
