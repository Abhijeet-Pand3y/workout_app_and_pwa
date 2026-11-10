package com.abhijeet.workout

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat
import com.abhijeet.workout.ui.ImportPlanDialog
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abhijeet.workout.data.WorkoutViewModel
import com.abhijeet.workout.ui.HistoryScreen
import com.abhijeet.workout.ui.PlanEditorScreen
import com.abhijeet.workout.ui.PlansScreen
import com.abhijeet.workout.ui.SessionDetailScreen
import com.abhijeet.workout.ui.StatsScreen
import com.abhijeet.workout.ui.WorkoutTab
import com.abhijeet.workout.ui.WorkoutTheme

class MainActivity : ComponentActivity() {
    /** Content of a plan file opened/shared into the app, waiting to be confirmed. */
    private var incoming by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) incoming = readIncoming(intent)
        setContent {
            WorkoutTheme {
                App(incomingPlan = incoming, onIncomingHandled = { incoming = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIncoming(intent)?.let { incoming = it }
    }

    private fun readIncoming(intent: Intent?): String? {
        if (intent == null) return null
        return try {
            when (intent.action) {
                Intent.ACTION_VIEW -> intent.data?.let(::readUri)
                Intent.ACTION_SEND ->
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(::readUri)
                        ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readUri(uri: Uri): String? =
        contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            if (bytes.size > 1_000_000) null else String(bytes)
        }
}

enum class Tab(val label: String, val icon: ImageVector) {
    Workout("Workout", Icons.Filled.PlayArrow),
    History("History", Icons.AutoMirrored.Filled.List),
    Stats("Progress", Icons.Filled.DateRange),
    Plans("Plans", Icons.Filled.Edit),
}

@Composable
fun App(
    incomingPlan: String? = null,
    onIncomingHandled: () -> Unit = {},
    vm: WorkoutViewModel = viewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Workout) }
    // Sub-screen on top of the current tab: "plan:<id>" or "session:<id>".
    var sub by rememberSaveable { mutableStateOf<String?>(null) }

    if (incomingPlan != null) {
        ImportPlanDialog(
            text = incomingPlan,
            onDismiss = onIncomingHandled,
            onImport = { plan ->
                val id = vm.importPlan(plan)
                onIncomingHandled()
                tab = Tab.Plans
                sub = "plan:$id"
            },
        )
    }

    BackHandler(enabled = sub != null) { sub = null }
    BackHandler(enabled = sub == null && tab != Tab.Workout) { tab = Tab.Workout }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t; sub = null },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner)) {
            val s = sub
            when {
                s != null && s.startsWith("plan:") ->
                    PlanEditorScreen(vm, s.removePrefix("plan:"), onBack = { sub = null })
                s != null && s.startsWith("session:") ->
                    SessionDetailScreen(vm, s.removePrefix("session:"), onBack = { sub = null })
                else -> when (tab) {
                    Tab.Workout -> WorkoutTab(vm, openPlan = { sub = "plan:$it" })
                    Tab.History -> HistoryScreen(vm, open = { sub = "session:$it" })
                    Tab.Stats -> StatsScreen(vm)
                    Tab.Plans -> PlansScreen(vm, edit = { sub = "plan:$it" })
                }
            }
        }
    }
}
