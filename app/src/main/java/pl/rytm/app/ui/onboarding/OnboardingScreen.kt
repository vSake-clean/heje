package pl.rytm.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.rytm.app.domain.session.SpeechAvailability
import pl.rytm.app.ui.SystemChecks
import pl.rytm.app.ui.components.SectionCard
import pl.rytm.app.ui.settings.RefreshOnResume
import pl.rytm.app.ui.settings.SettingsViewModel
import pl.rytm.app.ui.settings.StatusLine

/** Pierwsze uruchomienie: krótko wyjaśnia, co robi aplikacja, i prosi o potrzebne zgody (z uzasadnieniem). */
@Composable
fun OnboardingScreen(vm: SettingsViewModel, onDone: () -> Unit, onOpenSoundCloud: () -> Unit) {
    val context = LocalContext.current
    val system by vm.system.collectAsStateWithLifecycle()
    val speech by vm.speechState.collectAsStateWithLifecycle()
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshSystem() }
    RefreshOnResume(vm::refreshSystem)
    LaunchedEffect(Unit) { vm.initSpeech() }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Witaj w Rytmie", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Rytm prowadzi Cię przez rutynę: gra muzyka, a w ustalonych momentach głos mówi, co robisz teraz. " +
                    "W trybie ścisłym nie czeka na potwierdzenia – po 15 sekundach ścielenia łóżka od razu usłyszysz następne zadanie.",
                style = MaterialTheme.typography.bodyLarge,
            )

            SectionCard("1. Powiadomienia", subtitle = "Pokazują bieżące zadanie i odliczanie na ekranie blokady oraz pozwalają sterować rutyną.") {
                StatusLine(system.notificationsGranted, if (system.notificationsGranted) "Włączone" else "Wyłączone")
                if (!system.notificationsGranted) {
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else SystemChecks.openNotificationSettings(context)
                    }) { Text("Zezwól na powiadomienia") }
                }
            }

            SectionCard(
                "2. Praca w tle",
                subtitle = "Żeby polecenia padały punktualnie przy wygaszonym ekranie, zezwól Rytmowi działać bez ograniczeń baterii. " +
                    "Aplikacja zużywa energię tylko w trakcie rutyny.",
            ) {
                StatusLine(system.batteryUnrestricted, if (system.batteryUnrestricted) "Bez ograniczeń" else "Ograniczona przez oszczędzanie baterii")
                if (!system.batteryUnrestricted) {
                    Button(onClick = { SystemChecks.requestIgnoreBatteryOptimizations(context) }) { Text("Zezwól na działanie w tle") }
                }
                if (!system.exactAlarmsAllowed) {
                    StatusLine(false, "Budzik rutyny wymaga zgody na dokładne alarmy.")
                    OutlinedButton(onClick = { SystemChecks.openExactAlarmSettings(context) }) { Text("Zezwól na alarmy") }
                }
                if (!system.fullScreenAllowed) {
                    StatusLine(false, "Aby rutyna z budzika pokazała się nad blokadą ekranu, zezwól na powiadomienia pełnoekranowe.")
                    OutlinedButton(onClick = { SystemChecks.openFullScreenSettings(context) }) { Text("Zezwól") }
                }
            }

            SectionCard("3. Polski głos", subtitle = "Polecenia mówi wbudowana synteza mowy Androida – działa bez internetu.") {
                StatusLine(
                    speech == SpeechAvailability.READY,
                    when (speech) {
                        SpeechAvailability.READY -> "Polski głos gotowy"
                        SpeechAvailability.INITIALIZING -> "Sprawdzam…"
                        else -> "Brak polskiego głosu"
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (speech == SpeechAvailability.READY) {
                        OutlinedButton(onClick = { vm.testVoice("Cześć! Wstań z łóżka.") }) { Text("Odsłuchaj") }
                    } else if (speech == SpeechAvailability.UNAVAILABLE) {
                        Button(onClick = { SystemChecks.installTtsData(context) }) { Text("Zainstaluj polski głos") }
                        TextButton(onClick = vm::restartSpeech) { Text("Sprawdź ponownie") }
                    }
                }
            }

            SectionCard(
                "4. Muzyka z SoundCloud",
                subtitle = "Nie potrzebujesz kluczy ani konta w Rytmie.",
            ) {
                Text(
                    "W edytorze rutyny naciśnij „Wybierz z SoundCloud” i wyszukaj utwór lub playlistę – zagra w oficjalnym " +
                        "odtwarzaczu SoundCloud, a Rytm będzie go delikatnie przyciszać na czas poleceń. " +
                        "Możesz też w aplikacji SoundCloud wybrać „Udostępnij → Rytm”. Logowanie do SoundCloud jest opcjonalne.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onOpenSoundCloud) { Text("Zaloguj się do SoundCloud (opcjonalnie)") }
            }

            Button(
                onClick = {
                    vm.completeOnboarding()
                    onDone()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) { Text("Zaczynamy") }
        }
    }
}
