package pl.rytm.app.ui.importer

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.rytm.app.R
import pl.rytm.app.domain.model.Routine
import pl.rytm.app.domain.model.ScheduleMode
import pl.rytm.app.domain.schedule.ScheduleIssue
import pl.rytm.app.domain.schedule.Severity
import pl.rytm.app.domain.schedule.TimelineBuilder
import pl.rytm.app.domain.text.RoutineText
import pl.rytm.app.domain.text.TimeText
import pl.rytm.app.ui.components.CodeBlock
import pl.rytm.app.ui.components.SectionCard
import pl.rytm.app.ui.components.TextTransfer
import pl.rytm.app.ui.editor.describeIssue
import pl.rytm.app.ui.home.daysLabel
import pl.rytm.app.ui.theme.MonoSmall

/**
 * Rutyna z tekstu: (1) gotowe polecenie dla AI do skopiowania, (2) pole na rutynę w prostym formacie,
 * (3) podgląd z błędami i „Utwórz rutynę”.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportRoutineScreen(vm: ImportViewModel, onBack: () -> Unit, onCreated: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showFormat by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rutyna z tekstu") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- 1. Polecenie dla AI
            item {
                SectionCard(
                    "1. Poproś AI o rutynę (opcjonalnie)",
                    subtitle = "Skopiuj polecenie i wklej je do ChatGPT, Gemini albo Claude. AI odpowie gotową rutyną – " +
                        "wkleisz ją w punkcie 2.",
                ) {
                    OutlinedTextField(
                        value = state.requirements,
                        onValueChange = vm::setRequirements,
                        label = { Text("Twoje wymagania (opcjonalnie)") },
                        placeholder = {
                            Text("np. Wstaję 6:30, wychodzę 7:20. Prysznic, śniadanie, pakowanie. Chcę mieć 5 min zapasu.")
                        },
                        supportingText = { Text("Trafią na koniec polecenia. Możesz też dopisać je dopiero w czacie z AI.") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CodeBlock(text = vm.prompt, label = "polecenie dla AI")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = { TextTransfer.copy(context, "polecenie dla AI", vm.prompt) }) {
                            Icon(painterResource(R.drawable.ic_copy), null)
                            Spacer(Modifier.width(6.dp))
                            Text("Kopiuj polecenie")
                        }
                        OutlinedButton(onClick = { TextTransfer.share(context, "Wyślij do aplikacji AI", vm.prompt) }) {
                            Icon(painterResource(R.drawable.ic_open_external), null)
                            Spacer(Modifier.width(6.dp))
                            Text("Wyślij do aplikacji AI")
                        }
                    }
                    Text(
                        "Chcesz zmienić istniejącą rutynę? W jej menu wybierz „Kopiuj jako tekst”, wklej ją AI razem z poleceniem " +
                            "i napisz, co zmienić.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- 2. Tekst rutyny
            item {
                SectionCard(
                    "2. Wklej albo wpisz rutynę",
                    subtitle = "Jedno zadanie w linii: „- czas | nazwa | polecenie głosowe | opcje”.",
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            val pasted = TextTransfer.paste(context)
                            if (pasted.isNullOrBlank()) {
                                Toast.makeText(context, "Schowek jest pusty", Toast.LENGTH_SHORT).show()
                            } else {
                                vm.setText(pasted)
                            }
                        }) { Text("Wklej ze schowka") }
                        TextButton(onClick = vm::insertExample) { Text("Wstaw przykład") }
                        TextButton(onClick = { showFormat = !showFormat }) { Text(if (showFormat) "Ukryj format" else "Jak pisać?") }
                        if (state.text.isNotEmpty()) TextButton(onClick = { vm.setText("") }) { Text("Wyczyść") }
                    }
                    if (showFormat) CodeBlock(text = RoutineText.FORMAT_HELP, label = "format rutyny", collapsedMaxHeight = 400)
                    OutlinedTextField(
                        value = state.text,
                        onValueChange = vm::setText,
                        placeholder = {
                            Text(
                                "NAZWA: Mój poranek\nPOBUDKA: 6:30\nWYJŚCIE: 7:15\nZADANIA:\n- 20 s | Wstań | Wstań z łóżka.\n- 3 min | Zęby | Umyj zęby.",
                                style = MonoSmall,
                            )
                        },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp),
                        minLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ---- 3. Podgląd
            val result = state.result
            if (result != null) {
                item { PreviewSection(result, state.scheduleIssues) }
                item {
                    if (state.creating) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            vm.create { id, note ->
                                if (note != null) Toast.makeText(context, note, Toast.LENGTH_LONG).show()
                                onCreated(id)
                            }
                        },
                        enabled = result.canCreate && !state.creating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) { Text(if (result.canCreate) "Utwórz rutynę" else "Popraw błędy, aby utworzyć") }
                    Text(
                        "Po utworzeniu otworzy się edytor – możesz tam jeszcze wszystko zmienić i wybrać muzykę.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewSection(result: RoutineText.Result, scheduleIssues: List<ScheduleIssue>) {
    SectionCard("3. Podgląd") {
        result.errors.forEach { IssueLine(it.line, it.message, error = true) }
        result.warnings.forEach { IssueLine(it.line, it.message, error = false) }
        val r = result.routine ?: return@SectionCard
        scheduleIssues.filter { it.severity != Severity.INFO }.forEach {
            IssueLine(null, describeIssue(it, r.tasks), error = it.severity == Severity.ERROR && it !is ScheduleIssue.ExceedsWindow)
        }
        if (result.errors.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(6.dp))
                Text("Rutyna jest poprawna.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        HorizontalDivider()
        RoutineSummary(r)
        scheduleIssues.filterIsInstance<ScheduleIssue.Slack>().firstOrNull()?.let {
            Text(describeIssue(it, r.tasks), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
        if (result.musicLink != null) {
            Text("Muzyka: ${result.musicLink}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RoutineSummary(r: Routine) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(r.name, style = MaterialTheme.typography.titleLarge)
        if (r.goal.isNotBlank()) Text(r.goal, style = MaterialTheme.typography.bodyMedium)
        val info = buildList {
            add(if (r.mode == ScheduleMode.STRICT) "tryb ścisły" else "tryb elastyczny (czekanie ${TimeText.short(r.flexibleTimeoutSec)})")
            add("łącznie ${TimeText.clockSec(TimelineBuilder.totalSec(r.tasks).toLong())}")
            if (r.wakeTimeMinutes != null && r.leaveTimeMinutes != null) {
                add("${TimeText.timeOfDay(r.wakeTimeMinutes)} → ${TimeText.timeOfDay(r.leaveTimeMinutes)}")
            }
            if (r.alarmEnabled) add("budzik: ${daysLabel(r.alarmDays)}")
        }
        Text(info.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        r.tasks.forEachIndexed { i, t ->
            val start = TimelineBuilder.startSecOf(r.tasks, i)
            Column(Modifier.padding(vertical = 2.dp)) {
                Text(
                    "${TimeText.clockSec(start.toLong())}–${TimeText.clockSec((start + t.durationSec).toLong())}  ${t.title}" +
                        if (!t.mandatory) "  (opcjonalne)" else "",
                    style = MonoSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "„${t.announcementText}”" + if (t.notes.isNotBlank()) " · ${t.notes}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun IssueLine(line: Int?, message: String, error: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                RoundedCornerShape(8.dp),
            )
            .padding(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Default.Warning,
            null,
            tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            (if (line != null) "Linia $line: " else "") + message,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
