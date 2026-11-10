@file:OptIn(ExperimentalMaterial3Api::class)

package com.abhijeet.workout.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.abhijeet.workout.data.Plan
import com.abhijeet.workout.data.SplitDay
import com.abhijeet.workout.data.WorkoutViewModel
import java.time.LocalDate

// ---------------------------------------------------------------- plan list + settings

@Composable
fun PlansScreen(vm: WorkoutViewModel, edit: (String) -> Unit) {
    val d = vm.data
    val ctx = LocalContext.current
    var newPlan by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var pendingPlan by remember { mutableStateOf<String?>(null) }

    val planLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text == null) Toast.makeText(ctx, "Couldn't read file", Toast.LENGTH_SHORT).show()
            else pendingPlan = text
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray()) } != null
            }.getOrDefault(false)
            Toast.makeText(ctx, if (ok) "Backup saved" else "Backup failed", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text == null) Toast.makeText(ctx, "Couldn't read file", Toast.LENGTH_SHORT).show()
            else pendingImport = text
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Split plans") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { newPlan = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New plan") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (d.plans.isEmpty()) {
                item {
                    Text(
                        "No plans yet. Create one, or start from the Push/Pull/Legs template below and rename anything you like.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(d.plans, key = { it.id }) { p ->
                val active = p.id == vm.activePlan?.id
                Card(onClick = { edit(p.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (active) Pill("Active")
                            else TextButton(onClick = { vm.setActivePlan(p.id) }) { Text("Use this plan") }
                        }
                        Text(
                            if (p.days.isEmpty()) "No days yet" else "${p.days.size} days: " + p.days.joinToString(" → ") { it.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        p.days.getOrNull(p.nextDayIndex)?.let {
                            Text("Next up: ${it.name}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { planLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Import a plan a friend sent you")
                    }
                    OutlinedButton(onClick = { edit(vm.createPplTemplate()) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Add Push / Pull / Legs ×2 template")
                    }
                }
            }

            item { SectionHeader("Settings") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Weight unit", modifier = Modifier.weight(1f))
                            listOf("kg", "lb").forEach { u ->
                                FilterChip(
                                    selected = d.weightUnit == u,
                                    onClick = { vm.setUnit(u) },
                                    label = { Text(u) },
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                        HorizontalDivider()
                        Text("Backup", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Your data is stored only on this phone. Export a backup file now and then (e.g. to Google Drive) so you don't lose it if you change phones.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { exportLauncher.launch("workout-backup-${LocalDate.now()}.json") }) {
                                Text("Export backup")
                            }
                            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                                Text("Import backup")
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (newPlan) {
        TextInputDialog(
            title = "New split plan",
            label = "Plan name",
            confirmText = "Create",
            onDismiss = { newPlan = false },
            onConfirm = { name -> newPlan = false; edit(vm.createPlan(name)) },
        )
    }
    pendingPlan?.let { text ->
        ImportPlanDialog(
            text = text,
            onDismiss = { pendingPlan = null },
            onImport = { plan -> pendingPlan = null; edit(vm.importPlan(plan)) },
        )
    }
    pendingImport?.let { text ->
        ConfirmDialog(
            title = "Replace all data?",
            message = "Importing this backup replaces every plan and workout currently in the app.",
            confirmText = "Import",
            destructive = true,
            onDismiss = { pendingImport = null },
            onConfirm = {
                pendingImport = null
                val ok = vm.importJson(text)
                Toast.makeText(ctx, if (ok) "Backup imported" else "That file isn't a valid backup", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

// ---------------------------------------------------------------- plan editor

@Composable
fun PlanEditorScreen(vm: WorkoutViewModel, planId: String, onBack: () -> Unit) {
    val plan = vm.data.plans.find { it.id == planId }
    if (plan == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var renamePlan by remember { mutableStateOf(false) }
    var deletePlan by remember { mutableStateOf(false) }
    var addDay by remember { mutableStateOf(false) }
    var renameDay by remember { mutableStateOf<SplitDay?>(null) }
    var deleteDay by remember { mutableStateOf<SplitDay?>(null) }
    var addExTo by remember { mutableStateOf<SplitDay?>(null) }
    val isActive = vm.activePlan?.id == plan.id
    val ctx = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text(plan.name) },
                actions = {
                    IconButton(onClick = { sharePlan(ctx, plan, vm.sharePlanJson(plan)) }) { Icon(Icons.Filled.Share, "Share plan") }
                    IconButton(onClick = { renamePlan = true }) { Icon(Icons.Filled.Edit, "Rename plan") }
                    IconButton(onClick = { deletePlan = true }) { Icon(Icons.Filled.Delete, "Delete plan") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addDay = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add day") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Days run in this order and loop back to the start. Each workout picks up where the last one left off, however many days a week you train.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!isActive) {
                        Button(onClick = { vm.setActivePlan(plan.id) }) { Text("Use this plan") }
                    }
                }
            }
            if (plan.days.isEmpty()) {
                item { Text("No days yet. Tap \"Add day\" (e.g. Push 1, Pull 1, Legs 1…).") }
            }
            itemsIndexed(plan.days, key = { _, day -> day.id }) { i, day ->
                DayCard(
                    plan = plan,
                    index = i,
                    day = day,
                    vm = vm,
                    onRename = { renameDay = day },
                    onDelete = { deleteDay = day },
                    onAddExercise = { addExTo = day },
                )
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (renamePlan) {
        TextInputDialog("Rename plan", initial = plan.name, onDismiss = { renamePlan = false }) {
            renamePlan = false; vm.renamePlan(plan.id, it)
        }
    }
    if (deletePlan) {
        ConfirmDialog(
            title = "Delete \"${plan.name}\"?",
            message = "The plan is deleted. Workouts you already logged stay in history and stats.",
            confirmText = "Delete",
            destructive = true,
            onDismiss = { deletePlan = false },
            onConfirm = { deletePlan = false; vm.deletePlan(plan.id); onBack() },
        )
    }
    if (addDay) {
        TextInputDialog("Add day", label = "Day name (e.g. Push ${plan.days.size / 3 + 1})", confirmText = "Add", onDismiss = { addDay = false }) {
            addDay = false; vm.addDay(plan.id, it)
        }
    }
    renameDay?.let { day ->
        TextInputDialog("Rename day", initial = day.name, onDismiss = { renameDay = null }) {
            renameDay = null; vm.renameDay(plan.id, day.id, it)
        }
    }
    deleteDay?.let { day ->
        ConfirmDialog(
            title = "Delete ${day.name}?",
            message = "Removes the day and its saved exercise list from the plan. Past workouts are kept.",
            confirmText = "Delete",
            destructive = true,
            onDismiss = { deleteDay = null },
            onConfirm = { deleteDay = null; vm.deleteDay(plan.id, day.id) },
        )
    }
    addExTo?.let { day ->
        AddExerciseDialog(
            suggestions = vm.exerciseLibrary().filter { n -> day.exercises.none { it.equals(n, ignoreCase = true) } },
            isTimed = { vm.data.isTimed(it) },
            saveLabel = null,
            onDismiss = { addExTo = null },
            onAdd = { name, _, timed ->
                addExTo = null
                vm.setTimed(name, timed)
                vm.addTemplateExercise(plan.id, day.id, name)
            },
        )
    }
}

@Composable
private fun DayCard(
    plan: Plan,
    index: Int,
    day: SplitDay,
    vm: WorkoutViewModel,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAddExercise: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val isNext = index == plan.nextDayIndex
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(day.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (isNext) Pill("Next")
                IconButton(onClick = { vm.moveDay(plan.id, index, -1) }, enabled = index > 0) {
                    Icon(Icons.Filled.KeyboardArrowUp, "Move up")
                }
                IconButton(onClick = { vm.moveDay(plan.id, index, 1) }, enabled = index < plan.days.lastIndex) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Move down")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                        if (!isNext) {
                            DropdownMenuItem(text = { Text("Make this the next workout") }, onClick = {
                                menu = false; vm.setNextDay(plan.id, index)
                            })
                        }
                        DropdownMenuItem(text = { Text("Delete day") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            if (day.exercises.isEmpty()) {
                Text(
                    "No saved exercises. Add some here, or add them during a workout.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            day.exercises.forEachIndexed { j, name ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (vm.data.isTimed(name)) "⏱ $name" else name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(start = 20.dp),
                    )
                    IconButton(onClick = { vm.moveTemplateExercise(plan.id, day.id, j, -1) }, enabled = j > 0, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.KeyboardArrowUp, "Move up")
                    }
                    IconButton(onClick = { vm.moveTemplateExercise(plan.id, day.id, j, 1) }, enabled = j < day.exercises.lastIndex, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Move down")
                    }
                    IconButton(onClick = { vm.removeTemplateExercise(plan.id, day.id, j) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Close, "Remove")
                    }
                }
            }
            TextButton(onClick = onAddExercise) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("Add exercise")
            }
        }
    }
}
