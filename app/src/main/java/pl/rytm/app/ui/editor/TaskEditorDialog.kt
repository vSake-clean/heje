package pl.rytm.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pl.rytm.app.R
import pl.rytm.app.domain.model.Routine
import pl.rytm.app.domain.schedule.ScheduleValidator
import pl.rytm.app.domain.schedule.TimelineBuilder
import pl.rytm.app.domain.speech.AnnouncementTexts
import pl.rytm.app.domain.text.TimeText
import pl.rytm.app.ui.components.DurationField
import pl.rytm.app.ui.components.SwitchRow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorDialog(
    draft: TaskDraft,
    routine: Routine,
    estimateMs: (String) -> Long,
    onPreview: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (TaskDraft) -> Unit,
) {
    var title by remember { mutableStateOf(draft.task.title) }
    var spoken by remember { mutableStateOf(draft.task.spokenText) }
    var duration by remember { mutableIntStateOf(draft.task.durationSec) }
    var startSec by remember { mutableIntStateOf(draft.earliestStartSec + draft.task.gapBeforeSec) }
    var mandatory by remember { mutableStateOf(draft.task.mandatory) }
    var minDuration by remember { mutableIntStateOf(draft.task.minDurationSec ?: 0) }
    var notes by remember { mutableStateOf(draft.task.notes) }

    val overlap = startSec < draft.earliestStartSec
    val gap = (startSec - draft.earliestStartSec).coerceAtLeast(0)
    val endSec = startSec + duration
    val textForSpeech = AnnouncementTexts.taskStart(spoken.ifBlank { title }, duration, routine.announcements)
    val speechMs = estimateMs(textForSpeech)
    // Czas do następnego komunikatu: do startu kolejnego zadania (albo końca, gdy to ostatnie).
    val nextIndex = (draft.index ?: routine.tasks.size) + 1
    val nextStart = if (draft.index != null && nextIndex <= routine.tasks.lastIndex) {
        endSec + routine.tasks[nextIndex].gapBeforeSec
    } else {
        endSec
    }
    val speechTooLong = speechMs + ScheduleValidator.SPEECH_MARGIN_MS > (nextStart - startSec) * 1000L
    val valid = !overlap && duration > 0 && (title.isNotBlank() || spoken.isNotBlank())

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (draft.index == null) "Nowe zadanie" else "Edycja zadania") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Zamknij") } },
                    actions = {
                        TextButton(
                            enabled = valid,
                            onClick = {
                                onSave(
                                    draft.copy(
                                        task = draft.task.copy(
                                            title = title,
                                            spokenText = spoken,
                                            durationSec = duration,
                                            gapBeforeSec = gap,
                                            mandatory = mandatory,
                                            minDurationSec = if (!mandatory && minDuration > 0) minDuration.coerceAtMost(duration) else null,
                                            notes = notes,
                                        ),
                                    ),
                                )
                            },
                        ) { Text("Zapisz") }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nazwa zadania") },
                    placeholder = { Text("np. Pościel łóżko") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = spoken,
                    onValueChange = { spoken = it },
                    label = { Text("Polecenie głosowe") },
                    placeholder = { Text("np. Pościel łóżko.") },
                    supportingText = {
                        Text(
                            "Wypowiedź ≈ ${seconds1(speechMs)}" +
                                if (speechTooLong) " – za długa na ten czas! Skróć tekst albo wydłuż zadanie." else "",
                            color = if (speechTooLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    isError = speechTooLong,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { onPreview(textForSpeech) }) {
                    Icon(painterResource(R.drawable.ic_volume), null)
                    Spacer(Modifier.width(6.dp))
                    Text("Odsłuchaj")
                }

                Text("Czas", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DurationField(
                        label = "Początek",
                        seconds = startSec,
                        onChange = { startSec = it },
                        allowZero = true,
                        supporting = "od startu sesji",
                        modifier = Modifier.weight(1f),
                    )
                    DurationField(
                        label = "Czas trwania",
                        seconds = duration,
                        onChange = { duration = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 15, 30, 45, 60, 120, 180, 300, 600).forEach { sec ->
                        AssistChip(onClick = { duration = sec }, label = { Text(TimeText.short(sec)) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Przesuń:", style = MaterialTheme.typography.bodyMedium)
                    listOf(-15, -5, 5, 15).forEach { d ->
                        AssistChip(
                            onClick = { startSec = (startSec + d).coerceAtLeast(draft.earliestStartSec) },
                            label = { Text(if (d > 0) "+$d s" else "$d s") },
                        )
                    }
                }
                Text(
                    "Od ${TimeText.clockSec(startSec.toLong())} do ${TimeText.clockSec(endSec.toLong())}" +
                        if (gap > 0) " · przerwa przed: ${TimeText.short(gap)}" else "",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (overlap) {
                    Text(
                        "Zadanie nachodziłoby na poprzednie (kończy się o ${TimeText.clockSec(draft.earliestStartSec.toLong())}). " +
                            "Zacznij najwcześniej wtedy albo skróć poprzednie zadanie.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (draft.index != null && draft.index < routine.tasks.lastIndex) {
                    val oldEnd = TimelineBuilder.endSecOf(routine.tasks, draft.index)
                    val delta = endSec - oldEnd
                    if (delta != 0) {
                        Text(
                            "Kolejne zadania przesuną się o ${if (delta > 0) "+" else "−"}${TimeText.short(kotlin.math.abs(delta))}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SwitchRow(
                    "Zadanie obowiązkowe",
                    description = "Obowiązkowych zadań aplikacja nigdy sama nie skraca ani nie usuwa przy dopasowywaniu do godziny wyjścia.",
                    checked = mandatory,
                    onCheckedChange = { mandatory = it },
                )
                if (!mandatory) {
                    DurationField(
                        label = "Minimalny czas przy skracaniu",
                        seconds = minDuration,
                        onChange = { minDuration = it },
                        allowZero = true,
                        supporting = "0:00 = automatycznie (połowa czasu)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Dodatkowe instrukcje (na ekranie)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
