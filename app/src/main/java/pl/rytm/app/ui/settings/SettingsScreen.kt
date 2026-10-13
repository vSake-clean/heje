package pl.rytm.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.rytm.app.data.settings.AppSettings
import pl.rytm.app.data.settings.ExternalDucking
import pl.rytm.app.data.settings.VoiceStream
import pl.rytm.app.domain.model.InterruptionBehavior
import pl.rytm.app.domain.model.MusicPlayback
import pl.rytm.app.domain.model.ScheduleMode
import pl.rytm.app.domain.session.AnnounceTiming
import pl.rytm.app.domain.session.SpeechAvailability
import pl.rytm.app.domain.text.TimeText
import pl.rytm.app.ui.SystemChecks
import pl.rytm.app.ui.components.DurationField
import pl.rytm.app.ui.components.RadioGroup
import pl.rytm.app.ui.components.SectionCard
import pl.rytm.app.ui.components.SliderRow
import pl.rytm.app.ui.components.SwitchRow
import pl.rytm.app.ui.home.LoadingBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onOpenSoundCloud: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    RefreshOnResume(vm::refreshSystem)
    LaunchedEffect(Unit) { vm.initSpeech() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") } },
            )
        },
    ) { padding ->
        val s = settings
        if (s == null) {
            LoadingBox()
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SoundCloudSection(s, vm, onOpenSoundCloud)
            VoiceSection(s, vm)
            DuckingSection(s, vm)
            ScheduleSection(s, vm)
            BackgroundSection(s, vm)
            PrivacySection()
        }
    }
}

@Composable
fun RefreshOnResume(onResume: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) onResume() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

@Composable
private fun SoundCloudSection(s: AppSettings, vm: SettingsViewModel, onOpenSoundCloud: () -> Unit) {
    var cleared by remember { mutableStateOf(false) }
    SectionCard(
        "SoundCloud",
        subtitle = "Działa od razu – bez kluczy API i bez zakładania konta w Rytmie.",
    ) {
        Text(
            "Muzykę wybierasz w edytorze rutyny: wyszukiwarka SoundCloud, wklejony link albo „Udostępnij → Rytm” " +
                "w aplikacji SoundCloud. Logowanie do SoundCloud jest opcjonalne (np. by zobaczyć własne playlisty) – " +
                "hasło wpisujesz tylko na stronie SoundCloud, Rytm go nie widzi i nie zapisuje.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = onOpenSoundCloud) { Text("Otwórz SoundCloud (logowanie / przeglądanie)") }
        Text("Domyślny sposób odtwarzania", style = MaterialTheme.typography.bodyLarge)
        RadioGroup(
            options = listOf(
                MusicPlayback.EMBEDDED to "W Rytmie – oficjalny odtwarzacz SoundCloud",
                MusicPlayback.EXTERNAL_APP to "W aplikacji SoundCloud",
            ),
            selected = if (s.defaultMusicPlayback == MusicPlayback.NONE) MusicPlayback.EMBEDDED else s.defaultMusicPlayback,
            onSelect = { v -> vm.update { it.copy(defaultMusicPlayback = v) } },
        )
        Text("Przyciszanie, gdy muzyka gra w aplikacji SoundCloud", style = MaterialTheme.typography.bodyLarge)
        RadioGroup(
            options = listOf(
                ExternalDucking.SYSTEM to "Poproś Androida o przyciszenie",
                ExternalDucking.NONE to "Nie przyciszaj",
            ),
            selected = s.externalDucking,
            onSelect = { v -> vm.update { it.copy(externalDucking = v) } },
            descriptions = mapOf(
                ExternalDucking.SYSTEM to "Poziom ustala system (zwykle mocniej niż ustawione niżej %). Rytm nie ma dostępu do głośności innej aplikacji.",
                ExternalDucking.NONE to "Głos nakłada się na muzykę w pełnej głośności.",
            ),
        )
        TextButton(onClick = {
            vm.clearSoundCloudData()
            cleared = true
        }) { Text(if (cleared) "Wylogowano z SoundCloud w Rytmie ✓" else "Wyloguj z SoundCloud w Rytmie") }
    }
}

@Composable
private fun VoiceSection(s: AppSettings, vm: SettingsViewModel) {
    val context = LocalContext.current
    val voices by vm.voices.collectAsStateWithLifecycle()
    val state by vm.speechState.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    SectionCard("Głos (polska synteza mowy)") {
        SwitchRow(
            "Komunikaty głosowe",
            description = "Wyłączenie zostawia harmonogram, powiadomienia i wibracje.",
            checked = s.ttsEnabled,
            onCheckedChange = { v -> vm.update { it.copy(ttsEnabled = v) } },
        )
        StatusLine(
            ok = state == SpeechAvailability.READY,
            text = when (state) {
                SpeechAvailability.READY -> "Polski głos dostępny" + (engine?.let { " ($it)" } ?: "")
                SpeechAvailability.INITIALIZING -> "Sprawdzam syntezę mowy…"
                else -> "Brak polskiego głosu. Zainstaluj go (np. „Usługi mowy Google”) i wróć tutaj."
            },
        )
        if (state == SpeechAvailability.UNAVAILABLE) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { SystemChecks.installTtsData(context) }) { Text("Zainstaluj głos") }
                TextButton(onClick = vm::restartSpeech) { Text("Sprawdź ponownie") }
            }
        }
        Box {
            OutlinedButton(onClick = { menu = true }, enabled = voices.isNotEmpty()) {
                Text("Głos: " + (voices.firstOrNull { it.name == s.voiceName }?.label ?: "automatyczny (najlepszy offline)"))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Automatyczny (najlepszy offline)") }, onClick = {
                    menu = false
                    vm.update { it.copy(voiceName = null) }
                })
                voices.forEach { v ->
                    DropdownMenuItem(text = { Text(v.label) }, onClick = {
                        menu = false
                        vm.update { it.copy(voiceName = v.name) }
                    })
                }
            }
        }
        SliderRow(
            title = "Tempo mowy",
            value = s.speechRate,
            onValueChange = { v -> vm.update { it.copy(speechRate = (v * 20).toInt() / 20f) } },
            valueRange = 0.6f..1.6f,
            valueLabel = String.format(java.util.Locale.ROOT, "%.2f×", s.speechRate),
        )
        SliderRow(
            title = "Wysokość głosu",
            value = s.speechPitch,
            onValueChange = { v -> vm.update { it.copy(speechPitch = (v * 20).toInt() / 20f) } },
            valueRange = 0.6f..1.6f,
            valueLabel = String.format(java.util.Locale.ROOT, "%.2f", s.speechPitch),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.testVoice() }, enabled = state == SpeechAvailability.READY) { Text("Odsłuchaj") }
            TextButton(onClick = { SystemChecks.openTtsSettings(context) }) { Text("Ustawienia mowy systemu") }
        }
        HorizontalDivider()
        Text("Głośność komunikatów reguluje suwak", style = MaterialTheme.typography.bodyLarge)
        RadioGroup(
            options = listOf(VoiceStream.MEDIA to "Multimediów (jak muzyka)", VoiceStream.ALARM to "Budzika"),
            selected = s.voiceStream,
            onSelect = { v -> vm.update { it.copy(voiceStream = v) } },
        )
        SwitchRow(
            "Rutyny z budzika: głos na głośności budzika",
            description = "Słychać polecenia rano, nawet gdy multimedia są ściszone. Gdy podłączone są słuchawki, " +
                "głos zawsze idzie do słuchawek (strumień multimediów).",
            checked = s.alarmSessionsUseAlarmStream,
            onCheckedChange = { v -> vm.update { it.copy(alarmSessionsUseAlarmStream = v) } },
        )
    }
}

@Composable
private fun DuckingSection(s: AppSettings, vm: SettingsViewModel) {
    SectionCard(
        "Przyciszanie muzyki podczas komunikatów",
        subtitle = "Muzyka nie zatrzymuje się – tylko lekko cichnie i płynnie wraca do Twojego poziomu.",
    ) {
        SliderRow(
            title = "Siła przyciszenia",
            value = s.duckPercent.toFloat(),
            onValueChange = { v -> vm.update { it.copy(duckPercent = v.toInt()) } },
            valueRange = 10f..60f,
            valueLabel = "−${s.duckPercent}%",
            description = "Zalecane 20–30%. Muzyka gra wtedy na ${100 - s.duckPercent}% poprzedniej głośności.",
        )
        SliderRow(
            title = "Płynność ściszania",
            value = s.duckAttackMs.toFloat(),
            onValueChange = { v -> vm.update { it.copy(duckAttackMs = (v / 50).toInt() * 50) } },
            valueRange = 150f..1500f,
            valueLabel = "${s.duckAttackMs} ms",
        )
        SliderRow(
            title = "Płynność powrotu",
            value = s.duckReleaseMs.toFloat(),
            onValueChange = { v -> vm.update { it.copy(duckReleaseMs = (v / 50).toInt() * 50) } },
            valueRange = 300f..3000f,
            valueLabel = "${s.duckReleaseMs} ms",
        )
        SliderRow(
            title = "Przytrzymanie między komunikatami",
            value = s.duckHoldMs.toFloat(),
            onValueChange = { v -> vm.update { it.copy(duckHoldMs = (v / 100).toInt() * 100) } },
            valueRange = 0f..4000f,
            valueLabel = "${s.duckHoldMs} ms",
            description = "Gdy komunikaty padają blisko siebie, muzyka nie „skacze” między nimi.",
        )
    }
}

@Composable
private fun ScheduleSection(s: AppSettings, vm: SettingsViewModel) {
    SectionCard("Harmonogram (domyślne dla nowych rutyn)") {
        RadioGroup(
            options = listOf(ScheduleMode.STRICT to "Tryb ścisły", ScheduleMode.FLEXIBLE to "Tryb elastyczny"),
            selected = s.defaultMode,
            onSelect = { v -> vm.update { it.copy(defaultMode = v) } },
            descriptions = mapOf(
                ScheduleMode.STRICT to "Zadania zmieniają się dokładnie o czasie, bez czekania.",
                ScheduleMode.FLEXIBLE to "Można potwierdzać; czekanie jest ograniczone limitem.",
            ),
        )
        DurationField(
            label = "Limit czekania w trybie elastycznym",
            seconds = s.defaultFlexTimeoutSec,
            onChange = { v -> vm.update { it.copy(defaultFlexTimeoutSec = v) } },
            allowZero = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Po przerwaniu sesji", style = MaterialTheme.typography.bodyLarge)
        RadioGroup(
            options = listOf(
                InterruptionBehavior.CONTINUE_SCHEDULE to "Kontynuuj według planu",
                InterruptionBehavior.PAUSE to "Wstrzymaj rutynę",
                InterruptionBehavior.RESUME_CURRENT_TASK to "Wznów od bieżącego zadania",
            ),
            selected = s.defaultInterruption,
            onSelect = { v -> vm.update { it.copy(defaultInterruption = v) } },
        )
        HorizontalDivider()
        Text("Moment wypowiedzenia polecenia", style = MaterialTheme.typography.bodyLarge)
        RadioGroup(
            options = listOf(
                AnnounceTiming.AT_TASK_START to "Na początku zadania",
                AnnounceTiming.FINISH_AT_TASK_START to "Tak, by skończyć na początku zadania",
            ),
            selected = s.announceTiming,
            onSelect = { v -> vm.update { it.copy(announceTiming = v) } },
            descriptions = mapOf(
                AnnounceTiming.FINISH_AT_TASK_START to "Polecenie pada odrobinę wcześniej – zaczynasz dokładnie o czasie.",
            ),
        )
        SwitchRow(
            "Kompensuj opóźnienie syntezy mowy",
            description = "Aplikacja mierzy, ile trwa uruchomienie mowy, i wywołuje ją odpowiednio wcześniej.",
            checked = s.latencyCompensation,
            onCheckedChange = { v -> vm.update { it.copy(latencyCompensation = v) } },
        )
    }
}

@Composable
private fun BackgroundSection(s: AppSettings, vm: SettingsViewModel) {
    val context = LocalContext.current
    val system by vm.system.collectAsStateWithLifecycle()
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshSystem() }
    SectionCard(
        "Powiadomienia i działanie w tle",
        subtitle = "Rytm prowadzi rutynę jako usługa z powiadomieniem – działa przy wygaszonym ekranie.",
    ) {
        StatusLine(system.notificationsGranted, if (system.notificationsGranted) "Powiadomienia włączone" else "Powiadomienia wyłączone – nie zobaczysz bieżącego zadania na ekranie blokady.")
        if (!system.notificationsGranted) {
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else SystemChecks.openNotificationSettings(context)
            }) { Text("Włącz powiadomienia") }
        }
        StatusLine(
            system.batteryUnrestricted,
            if (system.batteryUnrestricted) "Bez ograniczeń baterii" else
                "Optymalizacja baterii włączona. Niektórzy producenci (Xiaomi, Huawei, Samsung) mogą wtedy zatrzymywać aplikacje w tle.",
        )
        if (!system.batteryUnrestricted) {
            OutlinedButton(onClick = { SystemChecks.requestIgnoreBatteryOptimizations(context) }) { Text("Zezwól na działanie w tle") }
        }
        StatusLine(system.exactAlarmsAllowed, if (system.exactAlarmsAllowed) "Budziki rutyn działają" else "Brak zgody na dokładne alarmy – budzik rutyny nie zadziała.")
        if (!system.exactAlarmsAllowed) {
            OutlinedButton(onClick = { SystemChecks.openExactAlarmSettings(context) }) { Text("Zezwól na alarmy") }
        }
        StatusLine(system.fullScreenAllowed, if (system.fullScreenAllowed) "Ekran rutyny może pokazać się nad blokadą" else "Brak zgody na powiadomienia pełnoekranowe (budzik pokaże zwykłe powiadomienie).")
        if (!system.fullScreenAllowed) {
            OutlinedButton(onClick = { SystemChecks.openFullScreenSettings(context) }) { Text("Zezwól") }
        }
        HorizontalDivider()
        SwitchRow("Powiadomienie o bieżącym zadaniu", checked = s.taskNotifications, onCheckedChange = { v -> vm.update { it.copy(taskNotifications = v) } })
        SwitchRow("Wibracja przy zmianie zadania", checked = s.vibrateOnTaskChange, onCheckedChange = { v -> vm.update { it.copy(vibrateOnTaskChange = v) } })
        SwitchRow(
            "Pokazuj sesję na ekranie blokady",
            checked = s.showOverLockScreen,
            onCheckedChange = { v -> vm.update { it.copy(showOverLockScreen = v) } },
        )
        SwitchRow(
            "Nie wygaszaj ekranu podczas sesji",
            description = "Zużywa więcej baterii. Rutyna działa także przy wygaszonym ekranie.",
            checked = s.keepScreenOn,
            onCheckedChange = { v -> vm.update { it.copy(keepScreenOn = v) } },
        )
    }
}

@Composable
private fun PrivacySection() {
    SectionCard("Prywatność") {
        Text(
            "Rutyny i ustawienia są zapisywane tylko na tym telefonie. Rytm nie ma kont, serwerów ani analityki. " +
                "Połączenia sieciowe: oficjalny odtwarzacz i strona SoundCloud oraz publiczne informacje o utworze (oEmbed). " +
                "Rytm nie przechowuje haseł.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("Wersja ${pl.rytm.app.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun StatusLine(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
            null,
            tint = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Suppress("unused")
private fun label(sec: Int) = TimeText.short(sec)
