package com.abhijeet.workout.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.abhijeet.workout.data.Plan
import com.abhijeet.workout.data.parseSharedPlan
import com.abhijeet.workout.data.toShareJson
import java.io.File

/** Opens the Android share sheet with the plan as a small .json file (no weights or history). */
fun sharePlan(ctx: Context, plan: Plan) {
    try {
        val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val safe = plan.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "plan" }
        val file = File(dir, "$safe - workout plan.json")
        file.writeText(plan.toShareJson().toString(2))
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Workout plan: ${plan.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Share \"${plan.name}\""))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Couldn't share plan", Toast.LENGTH_SHORT).show()
    }
}

/** Confirms adding a received plan file. [text] is the raw file content. */
@Composable
fun ImportPlanDialog(text: String, onDismiss: () -> Unit, onImport: (Plan) -> Unit) {
    val plan = remember(text) { parseSharedPlan(text) }
    if (plan == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Not a plan file") },
            text = {
                Text(
                    "This file isn't a shared Workout plan. " +
                        "(Full backups are restored from Plans → Settings → Import backup.)"
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add plan \"${plan.name}\"?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "It's added as a new plan. Your existing plans and workouts aren't changed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                plan.days.forEachIndexed { i, d ->
                    Text(
                        "${i + 1}. ${d.name}" + if (d.exercises.isNotEmpty()) " · ${d.exercises.size} exercises" else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onImport(plan) }) { Text("Add plan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
