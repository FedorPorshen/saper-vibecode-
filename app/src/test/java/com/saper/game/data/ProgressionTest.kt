package com.saper.game.data

import com.saper.game.game.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionTest {

    @Test
    fun `звания определяются по опыту`() {
        assertEquals("Новобранец", Ranks.forXp(0).name)
        assertEquals("Новобранец", Ranks.forXp(49).name)
        assertEquals("Рядовой", Ranks.forXp(50).name)
        assertEquals("Старшина", Ranks.forXp(500).name)
        assertEquals("Маршал сапёрных войск", Ranks.forXp(99999).name)
    }

    @Test
    fun `следующее звание находится корректно`() {
        assertEquals("Рядовой", Ranks.next(0)?.name)
        assertEquals("Лейтенант", Ranks.next(500)?.name)
        assertNull(Ranks.next(7500))
    }

    @Test
    fun `опыт - только база без бонусов`() {
        val b = computeWinXp(Difficulty.NOVICE, finalTimeSeconds = 100, hintsUsed = 1, streakAfterWin = 1)
        assertEquals(10, b.total)
        assertEquals(1, b.lines.size)
    }

    @Test
    fun `опыт - все бонусы сразу`() {
        val b = computeWinXp(Difficulty.NOVICE, finalTimeSeconds = 30, hintsUsed = 0, streakAfterWin = 3)
        assertEquals(10 + 5 + 5 + 7, b.total)
        assertEquals(4, b.lines.size)
    }

    @Test
    fun `опыт - норматив на границе считается пробитым`() {
        val b = computeWinXp(Difficulty.PRO, finalTimeSeconds = 480, hintsUsed = 2, streakAfterWin = 1)
        assertEquals(60 + 30, b.total)
    }

    @Test
    fun `опыт любителя с подсказками но быстро`() {
        val b = computeWinXp(Difficulty.AMATEUR, finalTimeSeconds = 60, hintsUsed = 2, streakAfterWin = 5)
        assertEquals(25 + 12 + 7, b.total)
    }

    @Test
    fun `достижения достаются по маске`() {
        val mask = Achievements.FIRST_WIN.mask or Achievements.STREAK5.mask
        assertEquals(listOf(Achievements.FIRST_WIN, Achievements.STREAK5), Achievements.unlocked(mask))
    }
}
