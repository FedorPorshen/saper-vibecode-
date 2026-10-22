package com.saper.game.game

/**
 * Сложности — классические размеры полей.
 * hintCharges     — заряды подсказок (лампа/лопата) на одну партию.
 * parTimeSeconds  — «норматив»: уложился быстрее — получишь бонус опыта.
 * xpBase          — базовый опыт за победу на этой сложности.
 */
enum class Difficulty(
    val title: String,
    val width: Int,
    val height: Int,
    val mines: Int,
    val hintCharges: Int,
    val parTimeSeconds: Int,
    val xpBase: Int
) {
    NOVICE("Новичок", 9, 9, 10, 2, 60, 10),
    AMATEUR("Любитель", 16, 16, 40, 3, 180, 25),
    PRO("Профи", 30, 16, 99, 5, 480, 60)
}

/** Надбавка к финальному времени за каждый потраченный заряд подсказки. */
const val HINT_PENALTY_SECONDS = 30
