package pl.rytm.app.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.rytm.app.R
import pl.rytm.app.domain.model.InterruptionBehavior
import pl.rytm.app.domain.model.MusicPlayback
import pl.rytm.app.domain.model.MusicSelection
import pl.rytm.app.domain.model.Routine
import pl.rytm.app.domain.model.RoutineTask
import pl.rytm.app.domain.model.ScheduleMode
import pl.rytm.app.domain.schedule.RoutinePlanner
import pl.rytm.app.domain.schedule.ScheduleIssue
import pl.rytm.app.domain.schedule.Severity
import pl.rytm.app.domain.schedule.TimelineBuilder
import pl.rytm.app.domain.text.TimeText
import pl.rytm.app.ui.components.ConfirmDialog
import pl.rytm.app.ui.components.DurationField
import pl.rytm.app.ui.components.RadioGroup
import pl.rytm.app.ui.components.SectionCard
import pl.rytm.app.ui.components.SliderRow
import pl.rytm.app.ui.components.SwitchRow
import pl.rytm.app.ui.components.TimeOfDayDialog
import pl.rytm.app.ui.home.LoadingBox
import pl.rytm.app.ui.theme.MonoSmall

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    vm: EditorViewModel,
    pickedMusic: MusicSelection?,
    onPickedMusicConsumed: () -> Unit,
    onBack: () -> Unit,
    onPickMusic: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmExit by remember { mutableStateOf(false) }

    LaunchedEffect(pickedMusic) {
        if (pickedMusic != null) {
            vm.setMusic(pickedMusic)
            onPickedMusicConsumed()
        }
    }
    LaunchedEffect(state.saved) { if (state.saved && state.message == null) onBack() }

    val tryExit = { if (state.dirty) confirmExit = true else onBack() }
    BackHandler(enabled = state.dirty) { confirmExit = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Nowa rutyna" else "Edycja rutyny") },
                navigationIcon = {
                    IconButton(onClick = tryExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz") }
                },
                actions = { TextButton(onClick = vm::save, enabled = state.loaded) { Text("Zapisz") } },
            )
        },
    ) { padding ->
        if (!state.loaded) {
            LoadingBox()
            return@Scaffold
        }
        val r = state.routine
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { BasicsSection(r, vm) }
            item { TimeWindowSection(r, state, vm) }
            item { ModeSection(r, vm) }
            item { ScheduleHeader(r, state, vm) }
            itemsIndexed(r.tasks, key = { i, t -> "${t.id}-$i-${t.title}" }) { index, task ->
                TaskRow(
                    index = index,
                    task = task,
                    tasks = r.tasks,
                    issues = state.issues.filter { it.taskIndex == index },
                    onClick = { vm.openTask(index) },
                    onDuplicate = { vm.duplicateTask(index) },
                    onDelete = { vm.deleteTask(index) },
                    onMoveUp = { vm.moveTask(index, -1) },
                    onMoveDown = { vm.moveTask(index, +1) },
                    onShift = { vm.shiftTask(index, it) },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::openNewTask) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Dodaj zadanie")
                    }
                    if (r.tasks.any { it.gapBeforeSec > 0 }) {
                        OutlinedButton(onClick = vm::removeGaps) { Text("Usuń przerwy") }
                    }
                }
            }
            item { MusicSection(r, vm, onPickMusic) }
            item { VoiceSection(r, state, vm) }
            item {
                Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Zapisz rutynę") }
            }
        }
    }

    state.taskDraft?.let { draft ->
        TaskEditorDialog(
            draft = draft,
            routine = state.routine,
            estimateMs = vm::estimateMs,
            onPreview = vm::previewSpeech,
            onDismiss = vm::closeTaskEditor,
            onSave = vm::saveTask,
        )
    }

    state.proposal?.let { p -> ProposalDialog(p, onApply = vm::applyProposal, onDismiss = vm::dismissProposal) }

    state.message?.let { msg ->
        AlertDialog(
            onDismissRequest = {
                vm.dismissMessage()
                if (state.saved) onBack()
            },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = {
                    vm.dismissMessage()
                    if (state.saved) onBack()
                }) { Text("OK") }
            },
        )
    }

    if (confirmExit) {
        ConfirmDialog(
            title = "Porzucić zmiany?",
            text = "Masz niezapisane zmiany w rutynie.",
            confirmLabel = "Porzuć",
            onConfirm = {
                confirmExit = false
                onBack()
            },
            onDismiss = { confirmExit = false },
            dismissLabel = "Wróć do edycji",
        )
    }
}

@Composable
private fun BasicsSection(r: Routine, vm: EditorViewModel) {
    SectionCard("Rutyna") {
        OutlinedTextField(
            value = r.name,
            onValueChange = vm::setName,
            label = { Text("Nazwa") },
            placeholder = { Text("np. Poranne wstawanie") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = r.goal,
            onValueChange = vm::setGoal,
            label = { Text("Cel") },
            placeholder = { Text("np. Wyjść z domu o 7:30 bez stresu") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeWindowSection(r: Routine, state: EditorState, vm: EditorViewModel) {
    var pick by remember { mutableStateOf<String?>(null) }
    SectionCard(
        "Godziny",
        subtitle = "Ustaw, o której wstajesz i o której musisz wyjść – aplikacja sprawdzi, czy plan się mieści.",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            TimeButton("Pobudka", r.wakeTimeMinutes, Modifier.weight(1f), onClick = { pick = "wake" }, onClear = { vm.setWakeTime(null) })
            TimeButton("Wyjście", r.leaveTimeMinutes, Modifier.weight(1f), onClick = { pick = "leave" }, onClear = { vm.setLeaveTime(null) })
        }
        val window = r.availableWindowSec
        if (r.wakeTimeMinutes != null && r.leaveTimeMinutes != null && window != null) {
            Text(
                "Dostępne: ${TimeText.clockSec(window.toLong())} · plan: ${TimeText.clockSec(state.totalSec.toLong())}",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            DurationField(
                label = "Planowany czas całej sesji (opcjonalnie)",
                seconds = r.targetDurationSec ?: 0,
                onChange = { vm.setTargetDuration(it) },
                allowZero = true,
                supporting = "0:00 = bez limitu · plan: ${TimeText.clockSec(state.totalSec.toLong())}",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SwitchRow(
            title = "Uruchamiaj o godzinie pobudki",
            description = if (r.wakeTimeMinutes == null) {
                "Najpierw ustaw godzinę pobudki."
            } else {
                "Rutyna wystartuje sama (z muzyką i głosem), także przy zablokowanym ekranie."
            },
            checked = r.alarmEnabled,
            onCheckedChange = vm::setAlarmEnabled,
            enabled = r.wakeTimeMinutes != null,
        )
        if (r.alarmEnabled) {
            val names = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                names.forEachIndexed { i, n ->
                    FilterChip(selected = r.alarmDays and (1 shl i) != 0, onClick = { vm.toggleDay(i) }, label = { Text(n) })
                }
            }
        }
    }
    when (pick) {
        "wake" -> TimeOfDayDialog("Godzina pobudki", r.wakeTimeMinutes ?: (6 * 60 + 30), onDismiss = { pick = null }) {
            vm.setWakeTime(it)
            pick = null
        }
        "leave" -> TimeOfDayDialog("Godzina wyjścia", r.leaveTimeMinutes ?: (7 * 60 + 30), onDismiss = { pick = null }) {
            vm.setLeaveTime(it)
            pick = null
        }
    }
}

@Composable
private fun TimeButton(label: String, minutes: Int?, modifier: Modifier, onClick: () -> Unit, onClear: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(minutes?.let { TimeText.timeOfDay(it) } ?: "—", style = MaterialTheme.typography.titleLarge)
        }
        if (minutes != null) {
            IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Clear, contentDescription = "Wyczyść") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSection(r: Routine, vm: EditorViewModel) {
    SectionCard("Tryb harmonogramu") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = r.mode == ScheduleMode.STRICT,
                onClick = { vm.setMode(ScheduleMode.STRICT) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Ścisły") }
            SegmentedButton(
                selected = r.mode == ScheduleMode.FLEXIBLE,
                onClick = { vm.setMode(ScheduleMode.FLEXIBLE) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Elastyczny") }
        }
        Text(
            if (r.mode == ScheduleMode.STRICT) {
                "Każde zadanie zaczyna się dokładnie o ustalonej porze. Aplikacja nie czeka na potwierdzenie – " +
                    "po czasie zadania od razu mówi, co dalej."
            } else {
                "Możesz nacisnąć „Zrobione” wcześniej. Gdy czas minie, aplikacja czeka na potwierdzenie najwyżej " +
                    "${TimeText.short(r.flexibleTimeoutSec)} i przechodzi dalej."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (r.mode == ScheduleMode.FLEXIBLE) {
            DurationField(
                label = "Maksymalne czekanie na potwierdzenie",
                seconds = r.flexibleTimeoutSec,
                onChange = vm::setFlexTimeout,
                allowZero = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        HorizontalDivider()
        Text("Po przerwaniu (połączenie, zamknięcie aplikacji przez system)", style = MaterialTheme.typography.bodyLarge)
        RadioGroup(
            options = listOf(
                InterruptionBehavior.CONTINUE_SCHEDULE to "Kontynuuj według planu",
                InterruptionBehavior.PAUSE to "Wstrzymaj rutynę",
                InterruptionBehavior.RESUME_CURRENT_TASK to "Wznów od bieżącego zadania",
            ),
            selected = r.interruptionBehavior,
            onSelect = vm::setInterruption,
            descriptions = mapOf(
                InterruptionBehavior.CONTINUE_SCHEDULE to "Godziny zadań się nie zmieniają.",
                InterruptionBehavior.PAUSE to "Czekam, aż naciśniesz „Wznów”.",
                InterruptionBehavior.RESUME_CURRENT_TASK to "Przerwane zadanie zaczyna się od nowa, kolejne przesuwają się.",
            ),
        )
    }
}

@Composable
private fun ScheduleHeader(r: Routine, state: EditorState, vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Harmonogram", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(TimeText.clockSec(state.totalSec.toLong()), style = MaterialTheme.typography.titleMedium)
        }
        TimelineBar(r.tasks, r.availableWindowSec)
        val serious = state.issues.filter { it.taskIndex == null && it !is ScheduleIssue.Slack }
        serious.forEach { issue -> IssueBanner(issue, r.tasks) }
        state.issues.filterIsInstance<ScheduleIssue.Slack>().firstOrNull()?.let {
            Text(describeIssue(it, r.tasks), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
        if (state.issues.any { it is ScheduleIssue.ExceedsWindow }) {
            Button(onClick = vm::proposeFit) { Text("Zaproponuj dopasowanie do czasu") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.measured.isEmpty()) "Długości komunikatów: szacunkowe" else "Długości komunikatów: zmierzone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::measureAll, enabled = !state.measuring) {
                Text(if (state.measuring) "Mierzę…" else "Zmierz głosem")
            }
        }
        if (state.measuring) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun IssueBanner(issue: ScheduleIssue, tasks: List<RoutineTask>) {
    val error = issue.severity == Severity.ERROR
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Warning, null, tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(8.dp))
        Text(describeIssue(issue, tasks), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Oś czasu: proporcjonalne odcinki zadań, przerwy jako puste miejsca, znacznik dostępnego czasu. */
@Composable
fun TimelineBar(tasks: List<RoutineTask>, availableSec: Int?) {
    val total = TimelineBuilder.totalSec(tasks).coerceAtLeast(1)
    val scaleMax = maxOf(total, availableSec ?: 0).coerceAtLeast(1)
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val optional = MaterialTheme.colorScheme.outline
    val track = MaterialTheme.colorScheme.surfaceVariant
    val error = MaterialTheme.colorScheme.error
    val entries = TimelineBuilder.build(tasks)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(28.dp),
    ) {
        val w = size.width
        val h = size.height
        drawRoundRect(track, size = Size(w, h), cornerRadius = CornerRadius(8f, 8f))
        entries.forEachIndexed { i, e ->
            val x0 = w * (e.startMs / 1000f) / scaleMax
            val x1 = w * (e.endMs / 1000f) / scaleMax
            val color = when {
                !e.mandatory -> optional
                i % 2 == 0 -> primary
                else -> secondary
            }
            drawRoundRect(
                color,
                topLeft = Offset(x0 + 1f, 0f),
                size = Size((x1 - x0 - 2f).coerceAtLeast(2f), h),
                cornerRadius = CornerRadius(6f, 6f),
            )
        }
        if (availableSec != null) {
            val x = w * availableSec / scaleMax
            drawRect(if (total > availableSec) error else Color.Black.copy(alpha = 0.6f), topLeft = Offset(x - 2f, -4f), size = Size(4f, h + 8f))
        }
    }
}

@Composable
private fun TaskRow(
    index: Int,
    task: RoutineTask,
    tasks: List<RoutineTask>,
    issues: List<ScheduleIssue>,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onShift: (Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val start = TimelineBuilder.startSecOf(tasks, index)
    val end = start + task.durationSec
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (task.gapBeforeSec > 0) {
                    Text(
                        "przerwa ${TimeText.short(task.gapBeforeSec)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${TimeText.clockSec(start.toLong())}–${TimeText.clockSec(end.toLong())}",
                    style = MonoSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (task.spokenText.isNotBlank() && task.spokenText != task.title) {
                    Text(
                        "„${task.spokenText}”",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    TimeText.short(task.durationSec) + if (task.mandatory) " · obowiązkowe" else " · opcjonalne",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                issues.filter { it.severity != Severity.INFO }.forEach {
                    Text(describeIssue(it, tasks), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Opcje zadania") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edytuj") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = {
                        menu = false
                        onClick()
                    })
                    DropdownMenuItem(
                        text = { Text("Kopiuj zadanie") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_copy), null) },
                        onClick = {
                            menu = false
                            onDuplicate()
                        },
                    )
                    if (index > 0) {
                        DropdownMenuItem(text = { Text("Wyżej") }, leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, null) }, onClick = {
                            menu = false
                            onMoveUp()
                        })
                    }
                    if (index < tasks.lastIndex) {
                        DropdownMenuItem(text = { Text("Niżej") }, leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, null) }, onClick = {
                            menu = false
                            onMoveDown()
                        })
                    }
                    DropdownMenuItem(text = { Text("Przesuń o 5 s później") }, onClick = { onShift(5) })
                    if (task.gapBeforeSec > 0) {
                        DropdownMenuItem(text = { Text("Przesuń o 5 s wcześniej") }, onClick = { onShift(-5) })
                    }
                    DropdownMenuItem(text = { Text("Usuń") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = {
                        menu = false
                        onDelete()
                    })
                }
            }
        }
    }
}

@Composable
private fun MusicSection(r: Routine, vm: EditorViewModel, onPickMusic: () -> Unit) {
    SectionCard(
        "Muzyka z SoundCloud",
        subtitle = "Bez logowania i bez kluczy – oficjalny odtwarzacz SoundCloud.",
    ) {
        val music = r.music
        if (music == null) {
            Text("Nie wybrano muzyki – rutyna będzie prowadzona samym głosem.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_music), null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(music.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(music.artist.takeIf { it.isNotBlank() }, if (music.kind.name == "PLAYLIST") "playlista" else "utwór")
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = { vm.setMusic(null) }) { Icon(Icons.Default.Clear, contentDescription = "Usuń muzykę") }
            }
        }
        Button(onClick = onPickMusic) { Text(if (music == null) "Wybierz z SoundCloud" else "Zmień muzykę") }
        if (music != null) {
            RadioGroup(
                options = listOf(
                    MusicPlayback.EMBEDDED to "Odtwarzaj w Rytmie (zalecane)",
                    MusicPlayback.EXTERNAL_APP to "Odtwarzaj w aplikacji SoundCloud",
                ),
                selected = if (r.musicPlayback == MusicPlayback.NONE) MusicPlayback.EMBEDDED else r.musicPlayback,
                onSelect = vm::setMusicPlayback,
                descriptions = mapOf(
                    MusicPlayback.EMBEDDED to "Pełna kontrola: delikatne przyciszanie na czas komunikatów, pauza razem z rutyną.",
                    MusicPlayback.EXTERNAL_APP to "Rytm otworzy utwór w SoundCloud. Nie może sterować jego głośnością – " +
                        "przyciszeniem zajmuje się Android (zwykle mocniejszym).",
                ),
            )
            if (r.musicPlayback != MusicPlayback.EXTERNAL_APP) {
                SliderRow(
                    title = "Głośność muzyki",
                    value = r.musicVolume.toFloat(),
                    onValueChange = { vm.setMusicVolume(it.toInt()) },
                    valueRange = 10f..100f,
                    valueLabel = "${r.musicVolume}%",
                    description = "Głośność w odtwarzaczu – nie zmienia głośności telefonu.",
                )
                SwitchRow("Zapętlaj muzykę", checked = r.musicLoop, onCheckedChange = vm::setMusicLoop)
            }
        }
    }
}

@Composable
private fun VoiceSection(r: Routine, state: EditorState, vm: EditorViewModel) {
    val a = r.announcements
    SectionCard("Komunikaty głosowe") {
        SwitchRow("Wypowiadaj polecenia", checked = a.enabled, onCheckedChange = { v -> vm.setAnnouncements { it.copy(enabled = v) } })
        if (a.enabled) {
            SliderRow(
                title = "Głośność komunikatów",
                value = a.volume.toFloat(),
                onValueChange = { v -> vm.setAnnouncements { it.copy(volume = v.toInt()) } },
                valueRange = 20f..100f,
                valueLabel = "${a.volume}%",
            )
            SwitchRow(
                "Podawaj czas na zadanie",
                description = "„Przygotuj ubrania. Masz jedną minutę.”",
                checked = a.includeDuration,
                onCheckedChange = { v -> vm.setAnnouncements { it.copy(includeDuration = v) } },
            )
            Text("Ostrzeżenie przed końcem zadania", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 10, 15, 30, 60).forEach { sec ->
                    FilterChip(
                        selected = a.warningBeforeEndSec == sec,
                        onClick = { vm.setAnnouncements { it.copy(warningBeforeEndSec = sec) } },
                        label = { Text(if (sec == 0) "brak" else "$sec s") },
                    )
                }
            }
            OutlinedTextField(
                value = a.startMessage,
                onValueChange = { v -> vm.setAnnouncements { it.copy(startMessage = v) } },
                label = { Text("Powitanie na start (opcjonalnie)") },
                placeholder = { Text("np. Dzień dobry! Zaczynamy.") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = a.endMessage,
                onValueChange = { v -> vm.setAnnouncements { it.copy(endMessage = v) } },
                label = { Text("Komunikat na koniec") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { vm.previewSpeech(r.tasks.firstOrNull()?.announcementText ?: "Wstań z łóżka.") }) {
                Icon(painterResource(R.drawable.ic_volume), null)
                Spacer(Modifier.width(6.dp))
                Text("Odsłuchaj głos")
            }
            if (state.speech == pl.rytm.app.domain.session.SpeechAvailability.UNAVAILABLE) {
                Text(
                    "Brak polskiego głosu w telefonie. Zainstaluj go w Ustawieniach aplikacji → Głos.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ProposalDialog(result: RoutinePlanner.Result, onApply: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dopasowanie do dostępnego czasu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (result) {
                    is RoutinePlanner.Result.Fits -> Text("Plan już się mieści. Zapas: ${TimeText.short(result.slackSec)}.")
                    is RoutinePlanner.Result.Proposal -> {
                        Text(
                            "Plan ${TimeText.clockSec(result.originalTotalSec.toLong())} → ${TimeText.clockSec(result.newTotalSec.toLong())} " +
                                "(dostępne ${TimeText.clockSec(result.availableSec.toLong())}). Zadania obowiązkowe bez zmian.",
                        )
                        result.changes.forEach { Text("• " + describeChange(it), style = MaterialTheme.typography.bodySmall) }
                    }
                    is RoutinePlanner.Result.Impossible -> Text(
                        "Same zadania obowiązkowe trwają ${TimeText.clockSec(result.mandatorySec.toLong())}, a dostępne jest " +
                            "${TimeText.clockSec(result.availableSec.toLong())}. Skróć zadania obowiązkowe ręcznie, " +
                            "oznacz część jako opcjonalne albo zmień godziny.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (result is RoutinePlanner.Result.Proposal) TextButton(onClick = onApply) { Text("Zastosuj") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}
