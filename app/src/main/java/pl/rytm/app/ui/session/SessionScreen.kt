package pl.rytm.app.ui.session

import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.rytm.app.R
import pl.rytm.app.domain.model.MusicPlayback
import pl.rytm.app.domain.model.ScheduleMode
import pl.rytm.app.domain.session.LiveSession
import pl.rytm.app.domain.session.MusicStatus
import pl.rytm.app.domain.session.Phase
import pl.rytm.app.domain.session.SpeechAvailability
import pl.rytm.app.domain.text.TimeText
import pl.rytm.app.music.ExternalSoundCloudOutput
import pl.rytm.app.music.SoundCloudWidgetPlayer
import pl.rytm.app.service.SessionHub
import pl.rytm.app.ui.components.ConfirmDialog
import pl.rytm.app.ui.theme.CountdownStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ekran aktywnej rutyny – tylko WYŚWIETLA stan liczony przez usługę.
 * Duże, czytelne z daleka elementy; najważniejsze informacje i tak przekazuje głos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val live by SessionHub.live.collectAsStateWithLifecycle()
    val player by SessionHub.player.collectAsStateWithLifecycle()
    var confirmStop by remember { mutableStateOf(false) }
    var hadSession by remember { mutableStateOf(false) }
    // Po zakończeniu rutyny wróć na ekran główny (tam pojawi się podsumowanie).
    androidx.compose.runtime.LaunchedEffect(live == null) {
        if (live != null) hadSession = true else if (hadSession) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(live?.plan?.routineName ?: "Rutyna", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") } },
            )
        },
    ) { padding ->
        val session = live
        if (session == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Brak trwającej rutyny", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = onBack) { Text("Wróć") }
                }
            }
            return@Scaffold
        }
        SessionContent(
            live = session,
            player = player,
            modifier = Modifier.padding(padding),
            onPause = { SessionHub.controller?.pause() },
            onResume = { SessionHub.controller?.resume() },
            onConfirm = { SessionHub.controller?.confirm() },
            onStop = { confirmStop = true },
            onRetryMusic = { SessionHub.controller?.retryMusic() },
            onOpenSettings = onOpenSettings,
        )
    }

    if (confirmStop) {
        ConfirmDialog(
            title = "Zakończyć rutynę?",
            text = "Sesja zostanie przerwana, a muzyka zatrzymana.",
            confirmLabel = "Zakończ",
            onConfirm = {
                confirmStop = false
                SessionHub.controller?.stop()
                onBack()
            },
            onDismiss = { confirmStop = false },
        )
    }
}

@Composable
private fun SessionContent(
    live: LiveSession,
    player: SoundCloudWidgetPlayer?,
    modifier: Modifier,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onConfirm: () -> Unit,
    onStop: () -> Unit,
    onRetryMusic: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val st = live.status
    val plan = live.plan
    val entries = plan.entries
    val current = st.taskIndex?.let { entries.getOrNull(it) }
    val next = st.nextTaskIndex?.let { entries.getOrNull(it) }
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // --- Komunikaty o stanie (przerwania, brak głosu) ---
        live.notice?.let { NoticeCard(it) }
        if (live.callActive) NoticeCard("Trwa połączenie – komunikaty głosowe są wstrzymane.")
        if (live.speech == SpeechAvailability.UNAVAILABLE) {
            NoticeCard("Brak polskiego głosu – polecenia widać tylko na ekranie i w powiadomieniu.", action = "Ustawienia" to onOpenSettings)
        }

        // --- Faza i bieżące zadanie ---
        val phaseLabel = when {
            st.phase == Phase.FINISHED -> "KONIEC"
            st.paused -> "WSTRZYMANO"
            st.phase == Phase.ACTIVE -> "TERAZ"
            st.phase == Phase.AWAITING_CONFIRMATION -> "CZAS MINĄŁ"
            st.phase == Phase.INTRO -> "ZA CHWILĘ"
            else -> "PRZERWA"
        }
        Text(
            phaseLabel,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp, letterSpacing = 3.sp),
            color = if (st.paused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Text(
            when (st.phase) {
                Phase.FINISHED -> plan.announcements.endMessage.ifBlank { "Rutyna zakończona" }
                else -> current?.title ?: ""
            },
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        if (current != null && current.spokenText.isNotBlank() && current.spokenText != current.title && st.phase != Phase.FINISHED) {
            Text("„${current.spokenText}”", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (current != null && current.notes.isNotBlank() && st.phase != Phase.FINISHED) {
            Text(current.notes, style = MaterialTheme.typography.bodyLarge)
        }

        // --- Odliczanie ---
        if (st.phase != Phase.FINISHED) {
            Text(
                if (st.phase == Phase.AWAITING_CONFIRMATION) "+" + TimeText.clock(st.positionMs - (st.currentEndMs ?: st.positionMs))
                else TimeText.countdown(st.remainingInPhaseMs),
                style = CountdownStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                color = if (st.phase == Phase.AWAITING_CONFIRMATION) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
            )
            LinearProgressIndicator(
                progress = { st.taskProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
            if (st.phase == Phase.AWAITING_CONFIRMATION) {
                val timeoutEnd = (st.currentEndMs ?: 0) + plan.effectiveTimeoutMs
                Text(
                    "Potwierdź wykonanie. Przejdę dalej za ${TimeText.countdown(timeoutEnd - st.positionMs)}.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        // --- Następne zadanie ---
        if (next != null && st.phase != Phase.FINISHED) {
            val nextStartWall = live.wallTimeOf(st.effectiveStartsMs[next.index])
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Następnie", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(next.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(clock.format(Date(nextStartWall)), style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        // --- Postęp całej rutyny ---
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LinearProgressIndicator(progress = { st.totalProgress }, modifier = Modifier.fillMaxWidth())
            Row {
                Text(
                    "Upłynęło ${TimeText.clock(st.positionMs.coerceAtMost(st.effectiveTotalMs))}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text("Zostało ${TimeText.clock(st.totalRemainingMs)}", style = MaterialTheme.typography.bodyMedium)
            }
            val leave = plan.leaveTimeMinutes
            Text(
                "Zadanie ${((st.taskIndex ?: entries.lastIndex) + 1).coerceAtMost(entries.size)} z ${entries.size} · koniec ok. " +
                    clock.format(Date(live.etaWall)) +
                    (leave?.let { " · wyjście ${TimeText.timeOfDay(it)}" } ?: "") +
                    if (plan.mode == ScheduleMode.STRICT) " · tryb ścisły" else " · tryb elastyczny",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // --- Sterowanie ---
        if (st.phase != Phase.FINISHED) {
            if (plan.mode == ScheduleMode.FLEXIBLE && !st.paused &&
                (st.phase == Phase.ACTIVE || st.phase == Phase.AWAITING_CONFIRMATION)
            ) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                ) {
                    Icon(painterResource(R.drawable.ic_check), null, Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Zrobione", style = MaterialTheme.typography.titleLarge)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (st.paused) {
                    Button(onClick = onResume, modifier = Modifier
                        .weight(1f)
                        .height(64.dp)) {
                        Icon(painterResource(R.drawable.ic_play), null)
                        Spacer(Modifier.width(6.dp))
                        Text("Wznów", style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    FilledTonalButton(onClick = onPause, modifier = Modifier
                        .weight(1f)
                        .height(64.dp)) {
                        Icon(painterResource(R.drawable.ic_pause), null)
                        Spacer(Modifier.width(6.dp))
                        Text("Pauza", style = MaterialTheme.typography.titleMedium)
                    }
                }
                OutlinedButton(onClick = onStop, modifier = Modifier
                    .weight(1f)
                    .height(64.dp)) {
                    Icon(painterResource(R.drawable.ic_stop), null)
                    Spacer(Modifier.width(6.dp))
                    Text("Zakończ", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // --- Muzyka ---
        MusicCard(live, player, onRetryMusic)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NoticeCard(text: String, action: Pair<String, () -> Unit>? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
        }
    }
}

@Composable
private fun MusicCard(live: LiveSession, player: SoundCloudWidgetPlayer?, onRetry: () -> Unit) {
    val m = live.music
    val context = LocalContext.current
    if (m.playback == MusicPlayback.NONE) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_music), null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.title ?: "Muzyka", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            m.artist?.takeIf { it.isNotBlank() },
                            when (m.status) {
                                MusicStatus.PLAYING -> "gra"
                                MusicStatus.PAUSED -> "wstrzymana"
                                MusicStatus.LOADING -> "wczytywanie…"
                                MusicStatus.WAITING_FOR_NETWORK -> "czeka na internet"
                                MusicStatus.INTERRUPTED -> "przerwana przez inną aplikację"
                                MusicStatus.ERROR -> "błąd odtwarzania"
                                MusicStatus.EXTERNAL -> "w aplikacji SoundCloud"
                                MusicStatus.NONE -> null
                            },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            m.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (m.status == MusicStatus.ERROR || m.status == MusicStatus.WAITING_FOR_NETWORK || m.status == MusicStatus.INTERRUPTED) {
                TextButton(onClick = onRetry) { Text("Spróbuj ponownie") }
            }
            if (m.playback == MusicPlayback.EMBEDDED && player != null) {
                // Oficjalny odtwarzacz SoundCloud – widoczny z logo i linkiem do utworu.
                AndroidView(
                    factory = { ctx -> FrameLayout(ctx).also { player.attachTo(it) } },
                    onRelease = { player.detachFrom(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(166.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
            if (m.playback == MusicPlayback.EXTERNAL_APP) {
                OutlinedButton(onClick = { m.permalinkUrl?.let { ExternalSoundCloudOutput.open(context, it) } }) {
                    Icon(painterResource(R.drawable.ic_open_external), null)
                    Spacer(Modifier.width(6.dp))
                    Text("Otwórz w SoundCloud")
                }
            }
        }
    }
}
