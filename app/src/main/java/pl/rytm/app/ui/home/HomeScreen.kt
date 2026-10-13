package pl.rytm.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.rytm.app.R
import pl.rytm.app.domain.model.MusicPlayback
import pl.rytm.app.domain.model.Routine
import pl.rytm.app.domain.model.ScheduleMode
import pl.rytm.app.domain.schedule.RoutinePlanner
import pl.rytm.app.domain.schedule.TimelineBuilder
import pl.rytm.app.domain.session.LiveSession
import pl.rytm.app.domain.text.TimeText
import pl.rytm.app.ui.components.ConfirmDialog
import pl.rytm.app.ui.editor.describeChange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenSession: () -> Unit,
    onEdit: (Long) -> Unit,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
) {
    val routines by vm.routines.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val ended by vm.ended.collectAsStateWithLifecycle()
    val dialog by vm.dialog.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val startError by vm.startError.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rytm", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Ustawienia") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nowa rutyna") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            live?.let { session ->
                item { LiveBanner(session, onOpenSession) }
            }
            if (live == null) {
                item {
                    val featured = routines.firstOrNull()
                    StartCard(featured) { r -> vm.requestStart(r, onOpenSession) }
                }
            }
            item {
                Text(
                    "Twoje rutyny",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (routines.isEmpty()) {
                item {
                    Text(
                        "Nie masz jeszcze rutyn. Utwórz pierwszą przyciskiem „Nowa rutyna”.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(routines, key = { it.id }) { routine ->
                RoutineCard(
                    routine = routine,
                    sessionActive = live != null,
                    onStart = { vm.requestStart(routine, onOpenSession) },
                    onEdit = { onEdit(routine.id) },
                    onDuplicate = { vm.duplicate(routine) },
                    onDelete = { vm.askDelete(routine) },
                    onAlarm = { vm.setAlarm(routine, it) },
                )
            }
        }
    }

    ended?.let { e ->
        AlertDialog(
            onDismissRequest = vm::clearEnded,
            title = { Text(if (e.stoppedByUser) "Sesja zakończona" else "Rutyna zakończona 🎉") },
            text = { Text("„${e.routineName}” · czas: ${TimeText.clock(e.durationMs)}") },
            confirmButton = { TextButton(onClick = vm::clearEnded) { Text("OK") } },
        )
    }

    startError?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::clearStartError,
            title = { Text("Nie można rozpocząć") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = vm::clearStartError) { Text("OK") } },
        )
    }

    when (val d = dialog) {
        null -> Unit
        is HomeDialog.ConfirmDelete -> ConfirmDialog(
            title = "Usunąć rutynę?",
            text = "„${d.routine.name}” zostanie trwale usunięta.",
            confirmLabel = "Usuń",
            onConfirm = { vm.delete(d.routine) },
            onDismiss = vm::dismissDialog,
        )
        is HomeDialog.Message -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text(d.title) },
            text = { Text(d.text) },
            confirmButton = { TextButton(onClick = vm::dismissDialog) { Text("OK") } },
        )
        is HomeDialog.LeaveConflict -> LeaveConflictDialog(
            d = d,
            onStartFitted = { tasks -> vm.startWithTasks(d.routine, tasks, onOpenSession) },
            onStartAnyway = { vm.startAnyway(d.routine, onOpenSession) },
            onDismiss = vm::dismissDialog,
        )
        is HomeDialog.ShareTarget -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text("Dodaj muzykę do rutyny") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("„${d.selection.title}”" + if (d.selection.artist.isNotBlank()) " – ${d.selection.artist}" else "")
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    routines.forEach { r ->
                        Text(
                            r.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.attachMusic(r, d.selection) }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = vm::dismissDialog) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun LiveBanner(live: LiveSession, onOpen: () -> Unit) {
    val st = live.status
    val task = st.taskIndex?.let { live.plan.entries.getOrNull(it) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (st.paused) "Wstrzymana rutyna" else "Trwa rutyna",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(live.plan.routineName, style = MaterialTheme.typography.titleLarge)
            if (task != null) {
                Text("${task.title} · ${TimeText.countdown(st.remainingInPhaseMs)}", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { st.totalProgress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpen) { Text("Wróć do sesji") }
        }
    }
}

@Composable
private fun StartCard(featured: Routine?, onStart: (Routine) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Gotowy? Telefon poprowadzi Cię krok po kroku.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Button(
                onClick = { featured?.let(onStart) },
                enabled = featured != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                Icon(painterResource(R.drawable.ic_play), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (featured != null) "Rozpocznij rutynę: ${featured.name}" else "Rozpocznij rutynę",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RoutineCard(
    routine: Routine,
    sessionActive: Boolean,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onAlarm: (Boolean) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val total = TimelineBuilder.totalSec(routine.tasks)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(routine.name, style = MaterialTheme.typography.titleLarge)
                    if (routine.goal.isNotBlank()) {
                        Text(
                            routine.goal,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Więcej") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Edytuj") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menu = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text("Duplikuj (wariant)") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_copy), null) },
                            onClick = { menu = false; onDuplicate() },
                        )
                        DropdownMenuItem(
                            text = { Text("Usuń") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onEdit, label = { Text(TimeText.clockSec(total.toLong())) }, leadingIcon = {
                    Icon(painterResource(R.drawable.ic_timer), null, Modifier.size(18.dp))
                })
                AssistChip(onClick = onEdit, label = { Text("${routine.tasks.size} zadań") })
                AssistChip(
                    onClick = onEdit,
                    label = { Text(if (routine.mode == ScheduleMode.STRICT) "Ścisły" else "Elastyczny") },
                )
                if (routine.wakeTimeMinutes != null && routine.leaveTimeMinutes != null) {
                    AssistChip(
                        onClick = onEdit,
                        label = { Text("${TimeText.timeOfDay(routine.wakeTimeMinutes)} → ${TimeText.timeOfDay(routine.leaveTimeMinutes)}") },
                    )
                }
                if (routine.music != null && routine.effectiveMusicPlayback != MusicPlayback.NONE) {
                    AssistChip(
                        onClick = onEdit,
                        label = { Text(routine.music.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_music), null, Modifier.size(18.dp)) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (routine.wakeTimeMinutes != null) {
                    Icon(painterResource(R.drawable.ic_alarm), null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Budzik ${TimeText.timeOfDay(routine.wakeTimeMinutes)} · ${daysLabel(routine.alarmDays)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.width(4.dp))
                    Switch(checked = routine.alarmEnabled, onCheckedChange = onAlarm, modifier = Modifier.padding(start = 4.dp))
                }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = onStart, enabled = !sessionActive) {
                    Icon(painterResource(R.drawable.ic_play), null)
                    Spacer(Modifier.width(4.dp))
                    Text("Start")
                }
            }
        }
    }
}

fun daysLabel(mask: Int): String {
    val names = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd")
    return when (mask and 0x7F) {
        0x7F -> "codziennie"
        0x1F -> "dni robocze"
        0x60 -> "weekend"
        0 -> "brak dni"
        else -> names.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.joinToString(" ")
    }
}

@Composable
private fun LeaveConflictDialog(
    d: HomeDialog.LeaveConflict,
    onStartFitted: (List<pl.rytm.app.domain.model.RoutineTask>) -> Unit,
    onStartAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nie zdążysz przed wyjściem") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Do wyjścia (${TimeText.timeOfDay(d.routine.leaveTimeMinutes ?: 0)}) zostało " +
                        "${TimeText.clockSec(d.availableSec.toLong())}, a rutyna trwa ${TimeText.clockSec(d.totalSec.toLong())}.",
                )
                when (val r = d.result) {
                    is RoutinePlanner.Result.Proposal -> {
                        Text("Propozycja (zadania obowiązkowe bez zmian):", fontWeight = FontWeight.SemiBold)
                        r.changes.forEach { Text("• " + describeChange(it), style = MaterialTheme.typography.bodySmall) }
                        Text("Nowy czas: ${TimeText.clockSec(r.newTotalSec.toLong())}", style = MaterialTheme.typography.bodySmall)
                    }
                    is RoutinePlanner.Result.Impossible -> Text(
                        "Same zadania obowiązkowe trwają ${TimeText.clockSec(r.mandatorySec.toLong())}. " +
                            "Skróć je w edytorze albo zmień godziny.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    is RoutinePlanner.Result.Fits -> Unit
                }
            }
        },
        confirmButton = {
            val r = d.result
            if (r is RoutinePlanner.Result.Proposal) {
                TextButton(onClick = { onStartFitted(r.tasks) }) { Text("Start z dopasowaniem") }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Anuluj") }
                TextButton(onClick = onStartAnyway) { Text("Start bez zmian") }
            }
        },
    )
}

@Composable
fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
