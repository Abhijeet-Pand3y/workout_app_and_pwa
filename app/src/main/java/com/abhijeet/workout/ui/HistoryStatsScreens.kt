@file:OptIn(ExperimentalMaterial3Api::class)

package com.abhijeet.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abhijeet.workout.data.WorkoutViewModel
import com.abhijeet.workout.data.computeStats
import com.abhijeet.workout.data.period

// ---------------------------------------------------------------- history

@Composable
fun HistoryScreen(vm: WorkoutViewModel, open: (String) -> Unit) {
    val all = vm.data.sessions
    val unit = vm.data.weightUnit
    val groups = remember(all) {
        all.sortedByDescending { it.startedAt }.groupBy { fmtDate(it.startedAt, "MMMM yyyy") }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("History") }) }) { pad ->
        if (all.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No workouts logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                groups.forEach { (month, list) ->
                    item(key = "h-$month") { SectionHeader("$month · ${list.size} workouts") }
                    items(list, key = { it.id }) { s ->
                        Card(onClick = { open(s.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.dayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Text(fmtDate(s.startedAt, "EEE, MMM d"), style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    listOfNotNull(
                                        s.planName.ifEmpty { null },
                                        "${s.exercises.size} exercises",
                                        "${s.setCount} sets",
                                        s.finishedAt?.let { fmtDuration(it - s.startedAt) },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    s.exercises.joinToString(" · ") { e ->
                                        val best = e.sets.maxByOrNull { it.weight ?: 0.0 }
                                        e.name + (best?.weight?.let { " ${fmtNum(it)}$unit" } ?: "")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- stats

@Composable
fun StatsScreen(vm: WorkoutViewModel) {
    var monthly by rememberSaveable { mutableStateOf(false) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val sessions = vm.data.sessions
    val unit = vm.data.weightUnit
    val p = period(monthly, offset)
    val st = remember(sessions, monthly, offset) { computeStats(sessions, p.from, p.to) }
    val trend = remember(sessions, monthly, offset) {
        (7 downTo 0).map { k -> period(monthly, offset - k).let { pp -> pp to computeStats(sessions, pp.from, pp.to) } }
    }
    val periodWord = if (monthly) "month" else "week"

    Scaffold(topBar = { TopAppBar(title = { Text("Progress") }) }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !monthly, onClick = { monthly = false; offset = 0 }, label = { Text("Week") })
                    FilterChip(selected = monthly, onClick = { monthly = true; offset = 0 }, label = { Text("Month") })
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { offset-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous") }
                    Text(
                        p.label() + if (offset == 0) " (this $periodWord)" else "",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { offset++ }, enabled = offset < 0) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next") }
                }
            }
            item {
                StatGrid(
                    listOf(
                        "Workouts" to "${st.workouts}",
                        "Time trained" to fmtDuration(st.durationMs),
                        "Sets" to "${st.sets}",
                        "Reps" to "${st.reps}",
                        "Partial reps" to "${st.partials}",
                        "Drop sets" to "${st.dropSets}",
                        "Volume ($unit)" to fmtVolume(st.volume),
                        "Exercises" to "${st.byExercise.size}",
                    )
                )
            }
            if (st.workouts == 0) {
                item { Text("No workouts this $periodWord.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (st.byDay.isNotEmpty()) {
                item { SectionHeader("Split days done") }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            st.byDay.forEach { (name, count) ->
                                Row {
                                    Text(name, modifier = Modifier.weight(1f))
                                    Text("$count×", style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                }
            }
            if (st.byExercise.isNotEmpty()) {
                item { SectionHeader("Exercises") }
                items(st.byExercise, key = { "ex-" + it.name.lowercase() }) { e ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(e.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                e.best?.let { Text("best ${fmtNum(it)} $unit", style = MaterialTheme.typography.labelLarge) }
                            }
                            Text(
                                listOfNotNull(
                                    "${e.sets} sets",
                                    "${e.reps} reps",
                                    if (e.partials > 0) "${e.partials} partials" else null,
                                    if (e.dropSets > 0) "${e.dropSets} drop sets" else null,
                                    if (e.volume > 0) "${fmtVolume(e.volume)} $unit volume" else null,
                                    if (e.sessions > 1) "${e.sessions} workouts" else null,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item { SectionHeader(if (monthly) "Last 8 months · sets" else "Last 8 weeks · sets") }
            item {
                val max = trend.maxOf { it.second.sets }.coerceAtLeast(1)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        trend.forEach { (pp, s) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(pp.shortLabel(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(56.dp))
                                Box(Modifier.weight(1f).height(16.dp)) {
                                    if (s.sets > 0) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth(s.sets.toFloat() / max)
                                                .fillMaxHeight()
                                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                        )
                                    }
                                }
                                Text(
                                    "${s.workouts}× · ${s.sets}",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(64.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatGrid(tiles: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value) ->
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(value, style = MaterialTheme.typography.headlineSmall)
                            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
