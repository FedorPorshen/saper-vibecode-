package com.saper.game.data

import com.saper.game.game.Difficulty

/** Воинское звание сапёра и порог опыта для него. */
data class Rank(val name: String, val xpRequired: Int)

object Ranks {
    val ALL = listOf(
        Rank("Новобранец", 0),
        Rank("Рядовой", 50),
        Rank("Ефрейтор", 150),
        Rank("Сержант", 350),
        Rank("Старшина", 450),
        Rank("Лейтенант", 1200),
        Rank("Капитан", 1900),
        Rank("Майор", 2800),
        Rank("Полковник", 4000),
        Rank("Генерал", 5500),
        Rank("Маршал сапёрных войск", 7500)
    )

    /** Звание, которое соответствует данному количеству опыта. */
    fun forXp(xp: Int): Rank = ALL.last { xp >= it.xpRequired }

    /** Следующее звание или null, если достигнут максимум. */
    fun next(xp: Int): Rank? = ALL.firstOrNull { xp < it.xpRequired }
}

/** Одна строка в начислении опыта. */
data class XpLine(val label: String, val amount: Int)

data class XpBreakdown(val total: Int, val lines: List<XpLine>)

/**
 * Чистый расчёт опыта за победу: база за сложность + бонус за скорость
 * (уложился в норматив — половина базы) + бонус за игру без подсказок +
 * бонус за серию побед. Проигрыш опыта не приносит (обрабатывается выше).
 */
fun computeWinXp(d: Difficulty, finalTimeSeconds: Int, hintsUsed: Int, streakAfterWin: Int): XpBreakdown {
    val base = d.xpBase
    val lines = mutableListOf(XpLine("база (${d.title})", base))
    var total = base
    if (finalTimeSeconds <= d.parTimeSeconds) {
        val bonus = base / 2
        total += bonus
        lines += XpLine("скорость (норматив ${formatPar(d.parTimeSeconds)})", bonus)
    }
    if (hintsUsed == 0) {
        total += 5
        lines += XpLine("без подсказок", 5)
    }
    if (streakAfterWin >= 3) {
        total += 7
        lines += XpLine("серия из $streakAfterWin побед", 7)
    }
    return XpBreakdown(total, lines)
}

private fun formatPar(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** Достижение: ключ, человеческие название/описание, иконка и бит в маске хранилища. */
data class Achievement(val key: String, val title: String, val description: String, val icon: String, val mask: Int)

object Achievements {
    val FIRST_WIN = Achievement("first_win", "Первая кровь", "Первая победа", "🩸", 1)
    val BLITZ = Achievement("blitz", "Блицкриг", "Новичок быстрее 15 секунд", "⚡", 2)
    val AMATEUR_FAST = Achievement("amateur_fast", "Уверенный темп", "Любитель быстрее 1:30", "🏃", 4)
    val PRO_WIN = Achievement("pro_win", "Профессионал", "Победа на Профи", "🎖", 8)
    val COOL_HEAD = Achievement("cool_head", "Хладнокровие", "Победа на Профи без подсказок", "🧊", 16)
    val STREAK5 = Achievement("streak5", "Серия из 5", "5 побед подряд", "🔥", 32)
    val VETERAN = Achievement("veteran", "Ветеран", "25 побед всего", "🏅", 64)

    val ALL = listOf(FIRST_WIN, BLITZ, AMATEUR_FAST, PRO_WIN, COOL_HEAD, STREAK5, VETERAN)

    fun unlocked(mask: Int): List<Achievement> = ALL.filter { mask and it.mask != 0 }
}

/** Итоги партии для игрока: начисленный опыт, новые достижения, повышение звания. */
data class GameFinishBonus(
    val breakdown: XpBreakdown,
    val xpBefore: Int,
    val xpAfter: Int,
    val newAchievements: List<Achievement>,
    val rankUp: Rank?
)

/** Хранение и начисление прогресса (опыт, серия, достижения, даты званий). */
object ProgressionStore {
    private const val KEY_XP = "xp"
    private const val KEY_STREAK = "streak"
    private const val KEY_BEST_STREAK = "bestStreak"
    private const val KEY_ACH_MASK = "achMask"
    private const val RANK_TS_PREFIX = "rank."

    suspend fun currentXp(): Int = SaperStore.intSetting(KEY_XP, 0)

    suspend fun achievementMask(): Int = SaperStore.intSetting(KEY_ACH_MASK, 0)

    suspend fun bestStreak(): Int = SaperStore.intSetting(KEY_BEST_STREAK, 0)

    /** Имя звания → момент получения (для званий, полученных после появления этой записи). */
    suspend fun rankTimestamps(): Map<String, Long> =
        SaperStore.allWithPrefix(RANK_TS_PREFIX)
            .filter { it.longValue > 0 }
            .associate { it.key.removePrefix(RANK_TS_PREFIX) to it.longValue }

    /**
     * Вызывать один раз в конце партии (после записи результата).
     * Проигрыш обнуляет серию и возвращает null; победа начисляет опыт
     * и проверяет достижения.
     */
    suspend fun onGameFinished(
        difficulty: Difficulty,
        won: Boolean,
        finalTimeSeconds: Int,
        hintsUsed: Int
    ): GameFinishBonus? {
        if (!won) {
            if (SaperStore.intSetting(KEY_STREAK, 0) != 0) SaperStore.putIntSetting(KEY_STREAK, 0)
            return null
        }
        val prevXp = SaperStore.intSetting(KEY_XP, 0)
        val streak = SaperStore.intSetting(KEY_STREAK, 0) + 1
        SaperStore.putIntSetting(KEY_STREAK, streak)
        SaperStore.putIntSetting(KEY_BEST_STREAK, maxOf(streak, SaperStore.intSetting(KEY_BEST_STREAK, 0)))

        val breakdown = computeWinXp(difficulty, finalTimeSeconds, hintsUsed, streak)
        val newXp = prevXp + breakdown.total
        SaperStore.putIntSetting(KEY_XP, newXp)

        val winsTotal = SaperStore.summary().sumOf { it.wins ?: 0 }
        var mask = SaperStore.intSetting(KEY_ACH_MASK, 0)
        val newly = mutableListOf<Achievement>()
        fun tryUnlock(a: Achievement, condition: Boolean) {
            if (condition && mask and a.mask == 0) {
                mask = mask or a.mask
                newly += a
            }
        }
        tryUnlock(Achievements.FIRST_WIN, winsTotal == 1)
        tryUnlock(Achievements.BLITZ, difficulty == Difficulty.NOVICE && finalTimeSeconds <= 15)
        tryUnlock(Achievements.AMATEUR_FAST, difficulty == Difficulty.AMATEUR && finalTimeSeconds <= 90)
        tryUnlock(Achievements.PRO_WIN, difficulty == Difficulty.PRO)
        tryUnlock(Achievements.COOL_HEAD, difficulty == Difficulty.PRO && hintsUsed == 0)
        tryUnlock(Achievements.STREAK5, streak >= 5)
        tryUnlock(Achievements.VETERAN, winsTotal >= 25)
        if (newly.isNotEmpty()) SaperStore.putIntSetting(KEY_ACH_MASK, mask)

        val oldRank = Ranks.forXp(prevXp)
        val newRank = Ranks.forXp(newXp)

        // запоминаем, когда получено каждое пройденное звание (обычно одно за партию)
        val now = System.currentTimeMillis()
        for (rank in Ranks.ALL) {
            if (rank.xpRequired > prevXp && rank.xpRequired <= newXp) {
                SaperStore.putLongSetting(RANK_TS_PREFIX + rank.name, now)
            }
        }

        return GameFinishBonus(
            breakdown = breakdown,
            xpBefore = prevXp,
            xpAfter = newXp,
            newAchievements = newly,
            rankUp = if (newRank != oldRank) newRank else null
        )
    }
}
