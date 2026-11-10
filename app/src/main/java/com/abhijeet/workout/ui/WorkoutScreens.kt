@file:OptIn(ExperimentalMaterial3Api::class)

package com.abhijeet.workout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abhijeet.workout.data.ExerciseLog
import com.abhijeet.workout.data.KIND_STRENGTH
import com.abhijeet.workout.data.KIND_TIMED
import com.abhijeet.workout.data.SetLog
import com.abhijeet.workout.data.Session
import com.abhijeet.workout.data.WorkoutViewModel
import com.abhijeet.workout.data.computeStats
import com.abhijeet.workout.data.newId
import com.abhijeet.workout.data.period
import kotlinx.coroutines.delay

/** Workout tab: shows the running workout if there is one, otherwise the start screen. */
@Composable
fun WorkoutTab(vm: WorkoutViewModel, openPlan: (String) -> Unit) {
    val cur = vm.data.current
    if (cur != null) ActiveWorkoutScreen(vm, cur) else StartScreen(vm, openPlan)
}

// ---------------------------------------------------------------- start

@Composable
private fun StartScreen(vm: WorkoutViewModel, openPlan: (String) -> Unit) {
    val d = vm.data
    val plan = vm.activePlan
    val week = remember(d.sessions) { period(false, 0).let { computeStats(d.sessions, it.from, it.to) } }

    Scaffold(topBar = { TopAppBar(title = { Text("Workout") }) }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (plan == null) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Set up your split", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Create a plan with as many days as you like and name them however you want. " +
                                    "Workouts rotate through the days in order, whenever you go. No calendar involved."
                            )
                            Button(onClick = { openPlan(vm.createPlan("My split")) }, Modifier.fillMaxWidth()) {
                                Text("Create my own plan")
                            }
                            OutlinedButton(onClick = { openPlan(vm.createPplTemplate()) }, Modifier.fillMaxWidth()) {
                                Text("Start from Push / Pull / Legs ×2")
                            }
                        }
                    }
                }
            } else {
                if (d.plans.size > 1) {
                    item {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            d.plans.forEach { p ->
                                FilterChip(
                                    selected = p.id == plan.id,
                                    onClick = { vm.setActivePlan(p.id) },
                                    label = { Text(p.name) },
                                )
                            }
                        }
                    }
                }
                if (plan.days.isEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("\"${plan.name}\" has no days yet.")
                                Button(onClick = { openPlan(plan.id) }) { Text("Add days") }
                            }
                        }
                    }
                } else {
                    val nextIdx = plan.nextDayIndex.coerceIn(0, plan.days.lastIndex)
                    val next = plan.days[nextIdx]
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("NEXT UP", style = MaterialTheme.typography.labelMedium)
                                Text(next.name, style = MaterialTheme.typography.headlineMedium)
                                Text(
                                    "Day ${nextIdx + 1} of ${plan.days.size} · ${plan.name}" +
                                        (vm.lastDone(next.id)?.let { " · last done ${relativeDay(it)}" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (next.exercises.isNotEmpty()) {
                                    Text(
                                        next.exercises.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Button(
                                    onClick = { vm.startWorkout(plan.id, next.id) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                ) {
                                    Icon(Icons.Filled.PlayArrow, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Start ${next.name}")
                                }
                            }
                        }
                    }
                    item {
                        Column {
                            SectionHeader("Or do a different day")
                            Text(
                                "The rotation carries on from whichever day you finish.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    itemsIndexed(plan.days, key = { _, day -> day.id }) { i, day ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(day.name, style = MaterialTheme.typography.titleMedium)
                                        if (i == nextIdx) {
                                            Spacer(Modifier.width(8.dp)); Pill("Next")
                                        }
                                    }
                                    Text(
                                        "${day.exercises.size} exercises" +
                                            (vm.lastDone(day.id)?.let { " · ${relativeDay(it)}" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { vm.startWorkout(plan.id, day.id) }) { Text("Start") }
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { vm.startWorkout(null, null) }, Modifier.fillMaxWidth()) {
                    Text("Start empty workout (not part of split)")
                }
            }
            item {
                Text(
                    "This week: ${week.workouts} workouts · ${week.sets} sets · ${week.reps} reps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- active workout

@Composable
private fun ActiveWorkoutScreen(vm: WorkoutViewModel, cur: Session) {
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(20_000); now = System.currentTimeMillis() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(cur.dayName)
                        Text(
                            listOf(cur.planName, fmtDuration(now - cur.startedAt), "${cur.setCount} sets")
                                .filter { it.isNotEmpty() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDiscard = true }) { Icon(Icons.Filled.Delete, "Discard workout") }
                    Button(onClick = { confirmFinish = true }, modifier = Modifier.padding(end = 8.dp)) { Text("Finish") }
                },
            )
        }
    ) { pad ->
        SessionEditor(vm, cur, onChange = vm::updateCurrent, modifier = Modifier.padding(pad))
    }

    if (confirmFinish) {
        val plan = vm.data.plans.find { it.id == cur.planId }
        val i = plan?.days?.indexOfFirst { it.id == cur.dayId } ?: -1
        val nextName = if (plan != null && i >= 0) plan.days[(i + 1) % plan.days.size].name else null
        ConfirmDialog(
            title = "Finish workout?",
            message = "${cur.setCount} sets logged in ${fmtDuration(now - cur.startedAt)}." +
                (nextName?.let { "\nNext time: $it" } ?: "") +
                (if (cur.exercises.any { it.sets.isEmpty() }) "\nExercises with no sets won't be saved to this workout." else ""),
            confirmText = "Finish",
            onDismiss = { confirmFinish = false },
            onConfirm = { confirmFinish = false; vm.finishWorkout() },
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard workout?",
            message = "Everything logged in this workout will be deleted and the rotation won't move.",
            confirmText = "Discard",
            destructive = true,
            onDismiss = { confirmDiscard = false },
            onConfirm = { confirmDiscard = false; vm.discardWorkout() },
        )
    }
}

// ---------------------------------------------------------------- past workout

@Composable
fun SessionDetailScreen(vm: WorkoutViewModel, sessionId: String, onBack: () -> Unit) {
    val s = vm.data.sessions.find { it.id == sessionId }
    if (s == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text(s.dayName)
                        Text(
                            fmtDate(s.startedAt, "EEE, MMM d yyyy · HH:mm") +
                                (s.finishedAt?.let { " · ${fmtDuration(it - s.startedAt)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete workout") } },
            )
        }
    ) { pad ->
        SessionEditor(vm, s, onChange = vm::updateSession, modifier = Modifier.padding(pad))
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this workout?",
            message = "It will be removed from history and stats.",
            confirmText = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; vm.deleteSession(s.id); onBack() },
        )
    }
}

// ---------------------------------------------------------------- shared editor

@Composable
private fun SessionEditor(
    vm: WorkoutViewModel,
    session: Session,
    onChange: (Session) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = vm.data.weightUnit
    val plan = vm.data.plans.find { it.id == session.planId }
    val day = plan?.days?.find { it.id == session.dayId }
    val template = day?.exercises?.map { it.lowercase() }?.toSet() ?: emptySet()
    var adding by remember { mutableStateOf(false) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (session.exercises.isEmpty()) {
            item {
                Text(
                    if (day != null) "No exercises saved for ${day.name} yet. Add what you do and tick \"save to ${day.name}\" so it's here next time."
                    else "Add your first exercise.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(session.exercises, key = { it.id }) { ex ->
            ExerciseCard(
                ex = ex,
                last = vm.lastPerformance(ex.name, session.startedAt),
                unit = unit,
                distUnit = vm.data.distanceUnit,
                inTemplate = day == null || ex.name.lowercase() in template,
                dayName = day?.name,
                onChange = { n -> onChange(session.copy(exercises = session.exercises.map { if (it.id == n.id) n else it })) },
                onRemove = { onChange(session.copy(exercises = session.exercises.filter { it.id != ex.id })) },
                onSaveToPlan = { if (plan != null && day != null) vm.addTemplateExercise(plan.id, day.id, ex.name) },
            )
        }
        item {
            OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Add exercise")
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (adding) {
        AddExerciseDialog(
            suggestions = vm.exerciseLibrary().filter { n -> session.exercises.none { it.name.equals(n, ignoreCase = true) } },
            isTimed = { vm.data.isTimed(it) },
            saveLabel = day?.let { "Also add to ${it.name} plan for next time" },
            onDismiss = { adding = false },
            onAdd = { name, save, timed ->
                adding = false
                onChange(session.copy(exercises = session.exercises + ExerciseLog(name = name, kind = if (timed) KIND_TIMED else KIND_STRENGTH)))
                vm.setTimed(name, timed)
                if (save && plan != null && day != null) vm.addTemplateExercise(plan.id, day.id, name)
            },
        )
    }
}

@Composable
private fun ExerciseCard(
    ex: ExerciseLog,
    last: ExerciseLog?,
    unit: String,
    distUnit: String,
    inTemplate: Boolean,
    dayName: String?,
    onChange: (ExerciseLog) -> Unit,
    onRemove: () -> Unit,
    onSaveToPlan: () -> Unit,
) {
    // index of set being edited; -1 = new set
    var editing by remember { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ex.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (ex.isTimed) {
                    Pill("⏱ timed", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(4.dp))
                }
                if (!inTemplate) {
                    Pill("today only", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (!inTemplate && dayName != null) {
                            DropdownMenuItem(text = { Text("Add to $dayName plan") }, onClick = { menu = false; onSaveToPlan() })
                        }
                        DropdownMenuItem(text = { Text("Remove from this workout") }, onClick = {
                            menu = false
                            if (ex.sets.isEmpty()) onRemove() else confirmRemove = true
                        })
                    }
                }
            }
            if (last != null) {
                Text(
                    "Last time: " + last.sets.joinToString(",  ") { it.summaryFor(ex.isTimed, unit, distUnit) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp, bottom = 4.dp),
                )
            }
            ex.sets.forEachIndexed { i, s ->
                if (ex.isTimed) TimedRow(i, s, distUnit) { editing = i } else SetRow(i, s, unit) { editing = i }
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { editing = -1 }) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (ex.isTimed) "Add round" else "Add set")
                }
                if (ex.sets.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onChange(ex.copy(sets = ex.sets + ex.sets.last().copy(id = newId()))) }) {
                        Text("Repeat last")
                    }
                }
            }
        }
    }

    editing?.let { idx ->
        val initial: SetLog = if (idx >= 0 && idx < ex.sets.size) ex.sets[idx] else {
            // Prefill a new set from the previous one (or last time's first set), without extras.
            (ex.sets.lastOrNull() ?: last?.sets?.firstOrNull())
                ?.copy(id = newId(), partialReps = 0, drops = emptyList()) ?: SetLog()
        }
        val isEdit = idx >= 0 && idx < ex.sets.size
        val save: (SetLog) -> Unit = { s ->
            editing = null
            onChange(ex.copy(sets = if (isEdit) ex.sets.toMutableList().also { it[idx] = s } else ex.sets + s))
        }
        val delete: (() -> Unit)? = if (isEdit) ({
            editing = null
            onChange(ex.copy(sets = ex.sets.filterIndexed { j, _ -> j != idx }))
        }) else null
        if (ex.isTimed) {
            TimedSetDialog(
                title = "${ex.name} · round ${if (isEdit) idx + 1 else ex.sets.size + 1}",
                // New rounds start at zero so the stopwatch can be used straight away.
                initial = if (isEdit) initial else SetLog(),
                distUnit = distUnit,
                onDismiss = { editing = null },
                onSave = save,
                onDelete = delete,
            )
        } else {
            SetDialog(
                title = "${ex.name} · set ${if (isEdit) idx + 1 else ex.sets.size + 1}",
                initial = initial,
                unit = unit,
                onDismiss = { editing = null },
                onSave = save,
                onDelete = delete,
            )
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = "Remove ${ex.name}?",
            message = "Its ${ex.sets.size} logged sets will be removed from this workout.",
            confirmText = "Remove",
            destructive = true,
            onDismiss = { confirmRemove = false },
            onConfirm = { confirmRemove = false; onRemove() },
        )
    }
}

@Composable
private fun TimedRow(index: Int, s: SetLog, distUnit: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(end = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${index + 1}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        Text(s.timedText(distUnit), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SetRow(index: Int, s: SetLog, unit: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(end = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
            )
            Text(s.mainText(unit), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (s.partialReps > 0) {
                Pill("+${s.partialReps} partial", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
            }
            if (s.drops.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Pill(
                    if (s.drops.size == 1) "drop set" else "${s.drops.size}× drop",
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        if (s.drops.isNotEmpty()) {
            Text(
                s.drops.joinToString("   ") { "↓ " + it.text(unit) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp, top = 2.dp),
            )
        }
    }
}
