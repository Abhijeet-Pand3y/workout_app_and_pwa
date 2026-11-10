@file:OptIn(ExperimentalMaterial3Api::class)

package com.abhijeet.workout.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.abhijeet.workout.data.Drop
import com.abhijeet.workout.data.SetLog
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// ---------------------------------------------------------------- theme

@Composable
fun WorkoutTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

// ---------------------------------------------------------------- formatting

fun fmtNum(d: Double): String =
    String.format(Locale.US, "%.2f", d).trimEnd('0').trimEnd('.')

fun fmtVolume(d: Double): String =
    if (d >= 10_000) String.format(Locale.US, "%.1fk", d / 1000) else fmtNum(Math.round(d).toDouble())

fun SetLog.mainText(unit: String): String =
    if (weight != null) "${fmtNum(weight)} $unit × $reps" else "$reps reps"

fun Drop.text(unit: String): String =
    if (weight != null) "${fmtNum(weight)} $unit × $reps" else "$reps reps"

fun SetLog.summary(unit: String): String = buildString {
    append(if (weight != null) "${fmtNum(weight)}×$reps" else "$reps")
    if (partialReps > 0) append(" +${partialReps}p")
    drops.forEach { d ->
        append(" ↓")
        append(if (d.weight != null) "${fmtNum(d.weight)}×${d.reps}" else "${d.reps}")
    }
}

/** 90 -> "1:30", 3725 -> "1:02:05" */
fun fmtClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%d:%02d", m, sec)
}

fun SetLog.timedText(distUnit: String): String =
    fmtClock(seconds.toLong()) + (distance?.let { " · ${fmtNum(it)} $distUnit" } ?: "")

fun SetLog.summaryFor(timed: Boolean, unit: String, distUnit: String): String =
    if (timed) fmtClock(seconds.toLong()) + (distance?.let { "/${fmtNum(it)}$distUnit" } ?: "") else summary(unit)

fun fmtDuration(ms: Long): String {
    val m = (ms / 60_000).coerceAtLeast(0)
    return if (m < 60) "${m}m" else "${m / 60}h ${String.format(Locale.US, "%02d", m % 60)}m"
}

fun fmtDate(millis: Long, pattern: String): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern))

fun relativeDay(millis: Long): String {
    val day = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return when (val n = ChronoUnit.DAYS.between(day, LocalDate.now())) {
        0L -> "today"
        1L -> "yesterday"
        else -> "$n days ago"
    }
}

private fun cleanNumber(v: String, decimal: Boolean): String {
    val s = v.replace(',', '.').filter { it.isDigit() || (decimal && it == '.') }
    // keep at most one dot
    val firstDot = s.indexOf('.')
    return if (firstDot < 0) s else s.substring(0, firstDot + 1) + s.substring(firstDot + 1).replace(".", "")
}

private fun roundTo(v: Double, step: Double): Double = Math.round(v / step) * step

// ---------------------------------------------------------------- small widgets

@Composable
fun Pill(
    text: String,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp),
    )
}

@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    step: Double,
    decimal: Boolean,
    unit: String? = null,
) {
    fun bump(delta: Double): String {
        val n = ((value.toDoubleOrNull() ?: 0.0) + delta).coerceAtLeast(0.0)
        return if (decimal) fmtNum(n) else n.toInt().toString()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { onValueChange(bump(-step)) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(cleanNumber(it, decimal)) },
            label = { Text(label) },
            singleLine = true,
            suffix = if (unit != null) { { Text(unit) } } else null,
            keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
        )
        FilledTonalIconButton(onClick = { onValueChange(bump(step)) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

// ---------------------------------------------------------------- dialogs

@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    label: String = "Name",
    confirmText: String = "Save",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "OK",
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AddExerciseDialog(
    suggestions: List<String>,
    isTimed: (String) -> Boolean,
    saveLabel: String?,
    onDismiss: () -> Unit,
    onAdd: (name: String, saveToPlan: Boolean, timed: Boolean) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var timed by remember { mutableStateOf(false) }
    // Strength exercises default to being saved to the day; timed/cardio default to "just today".
    var save by remember { mutableStateOf(true) }
    fun chooseKind(t: Boolean) { if (t != timed) { timed = t; save = !t } }
    val q = text.trim()
    val filtered = remember(q, suggestions) {
        (if (q.isEmpty()) suggestions else suggestions.filter { it.contains(q, ignoreCase = true) }).take(10)
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { focus.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add exercise") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v ->
                        text = v
                        // Known exercise: switch to the type it was logged as before.
                        suggestions.firstOrNull { it.equals(v.trim(), ignoreCase = true) }?.let { chooseKind(isTimed(it)) }
                    },
                    label = { Text("Exercise name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !timed, onClick = { chooseKind(false) }, label = { Text("Weights × reps") })
                    FilterChip(selected = timed, onClick = { chooseKind(true) }, label = { Text("Time / cardio") })
                }
                if (saveLabel != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { save = !save },
                    ) {
                        Checkbox(checked = save, onCheckedChange = { save = it })
                        Text(saveLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (filtered.isNotEmpty()) {
                    Text(
                        if (q.isEmpty()) "Your exercises" else "Matches",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    filtered.forEach { s ->
                        val t = isTimed(s)
                        Text(
                            if (t) "⏱ $s" else s,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onAdd(s, if (t == timed) save else !t, t) }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = q.isNotEmpty(), onClick = { onAdd(q, save, timed) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** One round of a timed exercise (plank, treadmill…): duration with a stopwatch, optional distance. */
@Composable
fun TimedSetDialog(
    title: String,
    initial: SetLog,
    distUnit: String,
    onDismiss: () -> Unit,
    onSave: (SetLog) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var minutes by remember { mutableStateOf(if (initial.seconds > 0) (initial.seconds / 60).toString() else "") }
    var seconds by remember { mutableStateOf(if (initial.seconds > 0) (initial.seconds % 60).toString() else "") }
    var distance by remember { mutableStateOf(initial.distance?.let { fmtNum(it) } ?: "") }
    var running by remember { mutableStateOf(false) }
    fun total(): Int = (minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)

    LaunchedEffect(running) {
        if (running) {
            val base = total()
            val start = System.currentTimeMillis()
            while (running) {
                val t = base + ((System.currentTimeMillis() - start) / 1000).toInt()
                minutes = (t / 60).toString()
                seconds = (t % 60).toString()
                delay(200)
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    fmtClock(total().toLong()),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
                )
                Button(
                    onClick = { running = !running },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (running) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
                ) { Text(if (running) "Stop timer" else if (total() > 0) "Continue timer" else "Start timer") }
                NumberField("Minutes", minutes, { minutes = it }, step = 1.0, decimal = false)
                NumberField("Seconds", seconds, { seconds = it }, step = 15.0, decimal = false)
                NumberField("Distance (optional)", distance, { distance = it }, step = 0.5, decimal = true, unit = distUnit)
            }
        },
        confirmButton = {
            TextButton(enabled = total() > 0, onClick = {
                running = false
                onSave(initial.copy(seconds = total(), distance = distance.toDoubleOrNull(), weight = null, reps = 0, partialReps = 0, drops = emptyList()))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { running = false; onDelete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = { running = false; onDismiss() }) { Text("Cancel") }
            }
        },
    )
}

private data class DropDraft(val weight: String, val reps: String)

@Composable
fun SetDialog(
    title: String,
    initial: SetLog,
    unit: String,
    onDismiss: () -> Unit,
    onSave: (SetLog) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var weight by remember { mutableStateOf(initial.weight?.let { fmtNum(it) } ?: "") }
    var reps by remember { mutableStateOf(if (initial.reps > 0) initial.reps.toString() else "") }
    var partials by remember { mutableStateOf(if (initial.partialReps > 0) initial.partialReps.toString() else "") }
    val drops = remember {
        initial.drops.map { d ->
            DropDraft(d.weight?.let { fmtNum(it) } ?: "", if (d.reps > 0) d.reps.toString() else "")
        }.toMutableStateList()
    }
    val repsInt = reps.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NumberField("Weight", weight, { weight = it }, step = 2.5, decimal = true, unit = unit)
                NumberField("Reps", reps, { reps = it }, step = 1.0, decimal = false)
                NumberField("Partial reps", partials, { partials = it }, step = 1.0, decimal = false)

                Text("Drop sets", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                if (drops.isEmpty()) {
                    Text(
                        "Did you drop the weight and keep going after this set? Add it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                drops.forEachIndexed { i, dd ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("↓${i + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(28.dp))
                        OutlinedTextField(
                            value = dd.weight,
                            onValueChange = { drops[i] = dd.copy(weight = cleanNumber(it, true)) },
                            label = { Text(unit) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(
                            value = dd.reps,
                            onValueChange = { drops[i] = dd.copy(reps = cleanNumber(it, false)) },
                            label = { Text("Reps") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { drops.removeAt(i) }) { Icon(Icons.Filled.Close, "Remove drop") }
                    }
                }
                TextButton(onClick = {
                    val base = (drops.lastOrNull()?.weight ?: weight).toDoubleOrNull()
                    drops.add(DropDraft(base?.let { fmtNum(roundTo(it * 0.8, 2.5)) } ?: "", ""))
                }) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add drop set")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = repsInt > 0, onClick = {
                onSave(
                    initial.copy(
                        weight = weight.toDoubleOrNull(),
                        reps = repsInt,
                        partialReps = partials.toIntOrNull() ?: 0,
                        drops = drops
                            .filter { (it.reps.toIntOrNull() ?: 0) > 0 }
                            .map { Drop(it.weight.toDoubleOrNull(), it.reps.toInt()) },
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
