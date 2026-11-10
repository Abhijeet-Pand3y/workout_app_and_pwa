package com.abhijeet.workout.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** One day of a split, e.g. "Push 1". [exercises] is the saved template list for that day. */
data class SplitDay(
    val id: String = newId(),
    val name: String,
    val exercises: List<String> = emptyList(),
)

/**
 * A split plan with any number of days. [nextDayIndex] is the rotation pointer:
 * it only moves when a workout is finished, never by the calendar.
 */
data class Plan(
    val id: String = newId(),
    val name: String,
    val days: List<SplitDay> = emptyList(),
    val nextDayIndex: Int = 0,
)

data class Drop(val weight: Double? = null, val reps: Int = 0)

data class SetLog(
    val id: String = newId(),
    val weight: Double? = null,
    val reps: Int = 0,
    val partialReps: Int = 0,
    /** Drop sets done right after this set (supports double/triple drops). */
    val drops: List<Drop> = emptyList(),
) {
    fun volume(): Double =
        (weight ?: 0.0) * reps + drops.sumOf { (it.weight ?: 0.0) * it.reps }
}

data class ExerciseLog(
    val id: String = newId(),
    val name: String,
    val sets: List<SetLog> = emptyList(),
)

data class Session(
    val id: String = newId(),
    val planId: String?,
    val planName: String,
    val dayId: String?,
    val dayName: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val exercises: List<ExerciseLog> = emptyList(),
) {
    val setCount: Int get() = exercises.sumOf { it.sets.size }
}

data class AppData(
    val plans: List<Plan> = emptyList(),
    val activePlanId: String? = null,
    /** Finished workouts. */
    val sessions: List<Session> = emptyList(),
    /** Workout in progress, if any. */
    val current: Session? = null,
    val weightUnit: String = "kg",
)

fun <T> List<T>.moved(index: Int, delta: Int): List<T> {
    val to = index + delta
    if (index !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(index)) }
}

// ---------------------------------------------------------------- JSON

private fun JSONObject.doubleOrNull(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
private fun JSONObject.stringOrNull(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
private fun JSONObject.longOrNull(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
private fun JSONObject.idOrNew(): String = optString("id").ifEmpty { newId() }

private fun <T> JSONObject.objects(k: String, f: (JSONObject) -> T): List<T> {
    val a = optJSONArray(k) ?: return emptyList()
    return (0 until a.length()).map { f(a.getJSONObject(it)) }
}

private fun JSONObject.strings(k: String): List<String> {
    val a = optJSONArray(k) ?: return emptyList()
    return (0 until a.length()).map { a.getString(it) }
}

private fun <T> List<T>.toJsonArray(f: (T) -> Any): JSONArray =
    JSONArray().also { a -> forEach { a.put(f(it)) } }

private fun Drop.toJson() = JSONObject().apply {
    weight?.let { put("w", it) }
    put("r", reps)
}

private fun parseDrop(o: JSONObject) = Drop(o.doubleOrNull("w"), o.optInt("r"))

private fun SetLog.toJson() = JSONObject().apply {
    put("id", id)
    weight?.let { put("w", it) }
    put("r", reps)
    put("p", partialReps)
    put("drops", drops.toJsonArray { it.toJson() })
}

private fun parseSet(o: JSONObject) = SetLog(
    id = o.idOrNew(),
    weight = o.doubleOrNull("w"),
    reps = o.optInt("r"),
    partialReps = o.optInt("p"),
    drops = o.objects("drops", ::parseDrop),
)

private fun ExerciseLog.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("sets", sets.toJsonArray { it.toJson() })
}

private fun parseExercise(o: JSONObject) = ExerciseLog(
    id = o.idOrNew(),
    name = o.optString("name"),
    sets = o.objects("sets", ::parseSet),
)

private fun Session.toJson() = JSONObject().apply {
    put("id", id)
    planId?.let { put("planId", it) }
    put("planName", planName)
    dayId?.let { put("dayId", it) }
    put("dayName", dayName)
    put("startedAt", startedAt)
    finishedAt?.let { put("finishedAt", it) }
    put("exercises", exercises.toJsonArray { it.toJson() })
}

private fun parseSession(o: JSONObject) = Session(
    id = o.idOrNew(),
    planId = o.stringOrNull("planId"),
    planName = o.optString("planName"),
    dayId = o.stringOrNull("dayId"),
    dayName = o.optString("dayName"),
    startedAt = o.optLong("startedAt"),
    finishedAt = o.longOrNull("finishedAt"),
    exercises = o.objects("exercises", ::parseExercise),
)

private fun SplitDay.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("exercises", exercises.toJsonArray { it })
}

private fun parseDay(o: JSONObject) = SplitDay(
    id = o.idOrNew(),
    name = o.optString("name"),
    exercises = o.strings("exercises"),
)

private fun Plan.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("nextDayIndex", nextDayIndex)
    put("days", days.toJsonArray { it.toJson() })
}

private fun parsePlan(o: JSONObject) = Plan(
    id = o.idOrNew(),
    name = o.optString("name"),
    days = o.objects("days", ::parseDay),
    nextDayIndex = o.optInt("nextDayIndex"),
)

// ---------------------------------------------------------------- shared plan (no workout data)

/** Just the plan structure (name, days, exercise lists), for sending to a friend. */
fun Plan.toShareJson(): JSONObject = JSONObject().apply {
    put("type", "workout-plan")
    put("version", 1)
    put("name", name)
    put("days", days.toJsonArray { d ->
        JSONObject().apply {
            put("name", d.name)
            put("exercises", d.exercises.toJsonArray { it })
        }
    })
}

/** Parses a shared plan into a brand-new plan (fresh ids, rotation starts at day 1). Null if not a plan file. */
fun parseSharedPlan(text: String): Plan? = try {
    val o = JSONObject(text.trim())
    if (o.optString("type") != "workout-plan") {
        null
    } else {
        Plan(
            name = o.optString("name").ifBlank { "Shared plan" },
            days = o.objects("days") { d ->
                SplitDay(name = d.optString("name").ifBlank { "Day" }, exercises = d.strings("exercises"))
            },
        )
    }
} catch (e: Exception) {
    null
}

fun AppData.toJson(): JSONObject = JSONObject().apply {
    put("version", 1)
    put("plans", plans.toJsonArray { it.toJson() })
    activePlanId?.let { put("activePlanId", it) }
    put("sessions", sessions.toJsonArray { it.toJson() })
    current?.let { put("current", it.toJson()) }
    put("weightUnit", weightUnit)
}

fun parseAppData(o: JSONObject) = AppData(
    plans = o.objects("plans", ::parsePlan),
    activePlanId = o.stringOrNull("activePlanId"),
    sessions = o.objects("sessions", ::parseSession),
    current = o.optJSONObject("current")?.let(::parseSession),
    weightUnit = o.optString("weightUnit", "kg"),
)
