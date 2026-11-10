package com.abhijeet.workout.data

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

class WorkoutViewModel(app: Application) : AndroidViewModel(app) {

    private val file = File(app.filesDir, "workout_data.json")
    private val io = Executors.newSingleThreadExecutor()

    var data by mutableStateOf(load())
        private set

    val activePlan: Plan?
        get() = data.plans.find { it.id == data.activePlanId } ?: data.plans.firstOrNull()

    private fun load(): AppData = try {
        if (file.exists()) parseAppData(JSONObject(file.readText())) else AppData()
    } catch (e: Exception) {
        // Keep the unreadable file around instead of silently losing it.
        runCatching { file.copyTo(File(file.path + ".corrupt-" + System.currentTimeMillis()), overwrite = true) }
        AppData()
    }

    private fun update(f: (AppData) -> AppData) {
        val new = f(data)
        if (new === data) return
        data = new
        val json = new.toJson().toString()
        io.execute {
            try {
                val tmp = File(file.path + ".tmp")
                tmp.writeText(json)
                if (!tmp.renameTo(file)) file.writeText(json)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ------------------------------------------------------------ plans

    fun createPlan(name: String, dayNames: List<String> = emptyList()): String {
        val plan = Plan(name = name, days = dayNames.map { SplitDay(name = it) })
        update { it.copy(plans = it.plans + plan, activePlanId = it.activePlanId ?: plan.id) }
        return plan.id
    }

    fun createPplTemplate(): String =
        createPlan("Push Pull Legs", listOf("Push 1", "Pull 1", "Legs 1", "Push 2", "Pull 2", "Legs 2"))

    /** Adds a plan received from someone else as a new plan. Returns its id. */
    fun importPlan(plan: Plan): String {
        val taken = data.plans.map { it.name.lowercase() }.toSet()
        var name = plan.name
        var n = 2
        while (name.lowercase() in taken) name = "${plan.name} ($n)".also { n++ }
        val p = plan.copy(name = name)
        update { it.copy(plans = it.plans + p, activePlanId = it.activePlanId ?: p.id) }
        return p.id
    }

    fun setActivePlan(id: String) = update { it.copy(activePlanId = id) }

    fun renamePlan(id: String, name: String) = editPlan(id) { it.copy(name = name) }

    fun deletePlan(id: String) = update { d ->
        val plans = d.plans.filter { it.id != id }
        d.copy(
            plans = plans,
            activePlanId = if (d.activePlanId == id) plans.firstOrNull()?.id else d.activePlanId,
        )
    }

    private fun editPlan(id: String, f: (Plan) -> Plan) =
        update { d -> d.copy(plans = d.plans.map { if (it.id == id) f(it) else it }) }

    /** Changes the day list while keeping the "next up" pointer on the same day where possible. */
    private fun editDays(planId: String, f: (List<SplitDay>) -> List<SplitDay>) = editPlan(planId) { p ->
        val newDays = f(p.days)
        val nextId = p.days.getOrNull(p.nextDayIndex)?.id
        val found = newDays.indexOfFirst { it.id == nextId }
        val idx = when {
            newDays.isEmpty() -> 0
            found >= 0 -> found
            else -> p.nextDayIndex % newDays.size
        }
        p.copy(days = newDays, nextDayIndex = idx)
    }

    private fun editDay(planId: String, dayId: String, f: (SplitDay) -> SplitDay) =
        editDays(planId) { days -> days.map { if (it.id == dayId) f(it) else it } }

    fun addDay(planId: String, name: String) = editDays(planId) { it + SplitDay(name = name) }

    fun renameDay(planId: String, dayId: String, name: String) = editDay(planId, dayId) { it.copy(name = name) }

    fun deleteDay(planId: String, dayId: String) = editDays(planId) { days -> days.filter { it.id != dayId } }

    fun moveDay(planId: String, index: Int, delta: Int) = editDays(planId) { it.moved(index, delta) }

    fun setNextDay(planId: String, index: Int) = editPlan(planId) {
        it.copy(nextDayIndex = index.coerceIn(0, (it.days.size - 1).coerceAtLeast(0)))
    }

    fun addTemplateExercise(planId: String, dayId: String, name: String) = editDay(planId, dayId) { d ->
        if (d.exercises.any { it.equals(name, ignoreCase = true) }) d else d.copy(exercises = d.exercises + name)
    }

    fun removeTemplateExercise(planId: String, dayId: String, index: Int) = editDay(planId, dayId) { d ->
        d.copy(exercises = d.exercises.filterIndexed { i, _ -> i != index })
    }

    fun moveTemplateExercise(planId: String, dayId: String, index: Int, delta: Int) = editDay(planId, dayId) {
        it.copy(exercises = it.exercises.moved(index, delta))
    }

    // ------------------------------------------------------------ workouts

    /** planId/dayId null = freestyle workout that doesn't touch the rotation. */
    fun startWorkout(planId: String?, dayId: String?) = update { d ->
        if (d.current != null) return@update d
        val plan = d.plans.find { it.id == planId }
        val day = plan?.days?.find { it.id == dayId }
        d.copy(
            current = Session(
                planId = plan?.id,
                planName = plan?.name ?: "",
                dayId = day?.id,
                dayName = day?.name ?: "Freestyle",
                startedAt = System.currentTimeMillis(),
                exercises = day?.exercises?.map { ExerciseLog(name = it) } ?: emptyList(),
            )
        )
    }

    fun updateCurrent(s: Session) = update { if (it.current == null) it else it.copy(current = s) }

    /** Saves the workout and advances the rotation to the day after the one just done. */
    fun finishWorkout() = update { d ->
        val cur = d.current ?: return@update d
        val done = cur.copy(
            finishedAt = System.currentTimeMillis(),
            exercises = cur.exercises.filter { it.sets.isNotEmpty() },
        )
        val plans = d.plans.map { p ->
            val i = if (p.id == cur.planId) p.days.indexOfFirst { it.id == cur.dayId } else -1
            if (i >= 0) p.copy(nextDayIndex = (i + 1) % p.days.size) else p
        }
        d.copy(current = null, sessions = d.sessions + done, plans = plans)
    }

    fun discardWorkout() = update { it.copy(current = null) }

    fun updateSession(s: Session) = update { d ->
        d.copy(sessions = d.sessions.map { if (it.id == s.id) s else it })
    }

    fun deleteSession(id: String) = update { d -> d.copy(sessions = d.sessions.filter { it.id != id }) }

    /** All exercise names ever used or saved, for suggestions. */
    fun exerciseLibrary(): List<String> {
        val names = data.plans.flatMap { p -> p.days.flatMap { it.exercises } } +
            data.sessions.flatMap { s -> s.exercises.map { it.name } }
        return names.map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    }

    /** Most recent logged performance of [name] before [before]. */
    fun lastPerformance(name: String, before: Long): ExerciseLog? =
        data.sessions.asSequence()
            .filter { it.startedAt < before }
            .sortedByDescending { it.startedAt }
            .mapNotNull { s -> s.exercises.find { it.name.equals(name, ignoreCase = true) && it.sets.isNotEmpty() } }
            .firstOrNull()

    fun lastDone(dayId: String): Long? =
        data.sessions.filter { it.dayId == dayId }.maxOfOrNull { it.startedAt }

    // ------------------------------------------------------------ settings / backup

    fun setUnit(unit: String) = update { it.copy(weightUnit = unit) }

    fun exportJson(): String = data.toJson().toString(2)

    fun importJson(text: String): Boolean = try {
        val parsed = parseAppData(JSONObject(text))
        update { parsed }
        true
    } catch (e: Exception) {
        false
    }

    override fun onCleared() {
        io.shutdown()
    }
}
