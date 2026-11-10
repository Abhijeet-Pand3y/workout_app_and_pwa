package com.abhijeet.workout.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class ExerciseStat(val name: String, val timed: Boolean) {
    var sets = 0
    var reps = 0
    var partials = 0
    var dropSets = 0
    var volume = 0.0
    var best: Double? = null
    var sessions = 0
    // timed exercises
    var seconds = 0L
    var bestSeconds = 0
    var distance = 0.0
}

data class PeriodStats(
    val workouts: Int,
    val sets: Int,
    val reps: Int,
    val partials: Int,
    val dropSets: Int,
    val volume: Double,
    val durationMs: Long,
    val byDay: List<Pair<String, Int>>,
    val byExercise: List<ExerciseStat>,
    /** Timed / cardio work. */
    val timedSeconds: Long,
    val timedRounds: Int,
    val distance: Double,
)

fun computeStats(all: List<Session>, from: Long, to: Long): PeriodStats {
    val ss = all.filter { it.startedAt in from until to }
    var sets = 0
    var reps = 0
    var partials = 0
    var drops = 0
    var volume = 0.0
    var duration = 0L
    var timedSeconds = 0L
    var timedRounds = 0
    var distance = 0.0
    val ex = LinkedHashMap<String, ExerciseStat>()
    for (s in ss) {
        s.finishedAt?.let { duration += (it - s.startedAt).coerceAtLeast(0) }
        for (e in s.exercises) {
            if (e.sets.isEmpty()) continue
            val key = (if (e.isTimed) "t:" else "s:") + e.name.trim().lowercase()
            val st = ex.getOrPut(key) { ExerciseStat(e.name.trim(), e.isTimed) }
            st.sessions++
            if (e.isTimed) {
                for (set in e.sets) {
                    timedRounds++; st.sets++
                    timedSeconds += set.seconds; st.seconds += set.seconds
                    if (set.seconds > st.bestSeconds) st.bestSeconds = set.seconds
                    set.distance?.let { distance += it; st.distance += it }
                }
                continue
            }
            for (set in e.sets) {
                sets++; st.sets++
                reps += set.reps; st.reps += set.reps
                partials += set.partialReps; st.partials += set.partialReps
                if (set.drops.isNotEmpty()) { drops++; st.dropSets++ }
                val v = set.volume()
                volume += v; st.volume += v
                set.weight?.let { w ->
                    val b = st.best
                    if (b == null || w > b) st.best = w
                }
            }
        }
    }
    return PeriodStats(
        workouts = ss.size,
        sets = sets,
        reps = reps,
        partials = partials,
        dropSets = drops,
        volume = volume,
        durationMs = duration,
        byDay = ss.groupingBy { it.dayName }.eachCount().toList().sortedByDescending { it.second },
        byExercise = ex.values.sortedWith(compareBy<ExerciseStat> { it.timed }.thenByDescending { it.sets }),
        timedSeconds = timedSeconds,
        timedRounds = timedRounds,
        distance = distance,
    )
}

fun LocalDate.toMillis(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** A week (Mon–Sun) or calendar month. [offset] 0 = current, -1 = previous, ... */
data class Period(val start: LocalDate, val end: LocalDate, val monthly: Boolean) {
    val from: Long get() = start.toMillis()
    val to: Long get() = end.toMillis()

    fun label(): String = if (monthly) {
        start.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    } else {
        val last = end.minusDays(1)
        start.format(DateTimeFormatter.ofPattern("MMM d")) + " – " +
            last.format(DateTimeFormatter.ofPattern(if (last.year != LocalDate.now().year) "MMM d, yyyy" else "MMM d"))
    }

    fun shortLabel(): String =
        start.format(DateTimeFormatter.ofPattern(if (monthly) "MMM yy" else "MMM d"))
}

fun period(monthly: Boolean, offset: Int): Period {
    val today = LocalDate.now()
    return if (monthly) {
        val s = today.withDayOfMonth(1).plusMonths(offset.toLong())
        Period(s, s.plusMonths(1), true)
    } else {
        val s = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
        Period(s, s.plusWeeks(1), false)
    }
}
