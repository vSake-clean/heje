package pl.rytm.app.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import pl.rytm.app.container
import pl.rytm.app.domain.model.MusicSelection
import pl.rytm.app.music.SoundCloudLinks
import pl.rytm.app.service.RoutineSessionService
import pl.rytm.app.service.SessionHub
import pl.rytm.app.ui.editor.EditorScreen
import pl.rytm.app.ui.editor.EditorViewModel
import pl.rytm.app.ui.home.HomeScreen
import pl.rytm.app.ui.home.HomeViewModel
import pl.rytm.app.ui.home.LoadingBox
import pl.rytm.app.ui.onboarding.OnboardingScreen
import pl.rytm.app.ui.picker.SoundCloudPickerScreen
import pl.rytm.app.ui.session.SessionScreen
import pl.rytm.app.ui.settings.SettingsScreen
import pl.rytm.app.ui.settings.SettingsViewModel
import pl.rytm.app.ui.theme.RytmTheme

class MainActivity : ComponentActivity() {

    private var pendingShare by mutableStateOf<String?>(null)
    private var openSessionRequest by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        observeSessionWindowFlags()
        setContent {
            RytmTheme {
                AppNavigation(
                    pendingShare = pendingShare,
                    onShareConsumed = { pendingShare = null },
                    openSession = openSessionRequest,
                    onOpenSessionConsumed = { openSessionRequest = false },
                )
            }
        }
        parkMusicPlayer()
    }

    /**
     * Niewidoczne miejsce (1×1 px) w oknie aktywności na odtwarzacz SoundCloud. Dopóki WebView jest
     * w oknie, przeglądarka nie usypia strony – muzyka gra przy wygaszonym ekranie i w słuchawkach,
     * także gdy ekran sesji nie jest otwarty.
     */
    private fun parkMusicPlayer() {
        val holder = android.widget.FrameLayout(this).apply {
            alpha = 0f
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        addContentView(holder, android.view.ViewGroup.LayoutParams(1, 1))
        musicHolder = holder
        lifecycleScope.launch {
            var parkedIn: pl.rytm.app.music.SoundCloudWidgetPlayer? = null
            SessionHub.player.collect { player ->
                if (parkedIn != null && parkedIn !== player) parkedIn?.unpark(holder)
                player?.park(holder)
                parkedIn = player
            }
        }
    }

    private var musicHolder: android.widget.FrameLayout? = null

    override fun onDestroy() {
        musicHolder?.let { h -> SessionHub.player.value?.unpark(h) }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Sesja zapisana na dysku, a usługa nie działa (np. system zamknął proces) – odtwórz ją.
        if (!SessionHub.isActive && container.sessionStore.hasSession()) {
            runCatching { RoutineSessionService.recover(this) }
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            if (SoundCloudLinks.extractUrl(text) != null) pendingShare = text
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_SESSION, false)) openSessionRequest = true
    }

    /** Podczas sesji: ekran nad blokadą (opcjonalnie), włączenie ekranu przy starcie z budzika, brak wygaszania. */
    private fun observeSessionWindowFlags() {
        lifecycleScope.launch {
            // Stan sesji odświeża się 4× na sekundę – reagujemy tylko na faktyczne zmiany.
            combine(SessionHub.live.map { it != null }.distinctUntilChanged(), SessionHub.fromAlarm, container.settings.settings) { active, alarm, s ->
                Triple(active, alarm, s)
            }.distinctUntilChanged().collect { (active, alarm, s) ->
                val overLock = active && (s.showOverLockScreen || alarm)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    setShowWhenLocked(overLock)
                    setTurnScreenOn(active && alarm)
                } else {
                    @Suppress("DEPRECATION")
                    if (overLock) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                }
                if (active && s.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_SESSION = "open_session"
    }
}

private const val KEY_PICKED_MUSIC = "picked_music"
private val musicJson = Json { ignoreUnknownKeys = true }

@Composable
private fun AppNavigation(
    pendingShare: String?,
    onShareConsumed: () -> Unit,
    openSession: Boolean,
    onOpenSessionConsumed: () -> Unit,
) {
    val nav = rememberNavController()
    val settingsVm: SettingsViewModel = viewModel()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    // Tylko „czy trwa sesja” – bez przerysowywania całej nawigacji przy każdym odświeżeniu stanu sesji.
    val sessionFlow = remember { SessionHub.live.map { it != null }.distinctUntilChanged() }
    val sessionActive by sessionFlow.collectAsStateWithLifecycle(initialValue = false)

    val s = settings
    if (s == null) {
        LoadingBox()
        return
    }
    val start = if (s.onboardingDone) "home" else "onboarding"

    LaunchedEffect(openSession, sessionActive) {
        if (openSession && sessionActive) {
            nav.navigate("session") { launchSingleTop = true }
            onOpenSessionConsumed()
        }
    }

    NavHost(navController = nav, startDestination = start) {
        composable("onboarding") {
            OnboardingScreen(
                vm = settingsVm,
                onDone = { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } },
                onOpenSoundCloud = { nav.navigate("browse") },
            )
        }
        composable("home") {
            val vm: HomeViewModel = viewModel()
            val activity = androidx.activity.compose.LocalActivity.current
            // Trwa rutyna: „Wstecz” chowa aplikację zamiast ją zamykać – odtwarzacz zostaje w oknie (muzyka gra dalej).
            androidx.activity.compose.BackHandler(enabled = sessionActive) { activity?.moveTaskToBack(true) }
            LaunchedEffect(pendingShare) {
                if (pendingShare != null) {
                    vm.handleSharedText(pendingShare)
                    onShareConsumed()
                }
            }
            HomeScreen(
                vm = vm,
                onOpenSession = { nav.navigate("session") { launchSingleTop = true } },
                onEdit = { id -> nav.navigate("editor/$id") },
                onCreate = { nav.navigate("editor/0") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("editor/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as Application
            val vm: EditorViewModel = viewModel(
                key = "editor-$id",
                factory = viewModelFactory { initializer { EditorViewModel(app, id) } },
            )
            val pickedJson by entry.savedStateHandle.getStateFlow<String?>(KEY_PICKED_MUSIC, null).collectAsStateWithLifecycle()
            val picked = pickedJson?.let { runCatching { musicJson.decodeFromString(MusicSelection.serializer(), it) }.getOrNull() }
            EditorScreen(
                vm = vm,
                pickedMusic = picked,
                onPickedMusicConsumed = { entry.savedStateHandle[KEY_PICKED_MUSIC] = null },
                onBack = { nav.popBackStack() },
                onPickMusic = { nav.navigate("picker") },
            )
        }
        composable("picker") {
            SoundCloudPickerScreen(
                onBack = { nav.popBackStack() },
                onPicked = { selection ->
                    nav.previousBackStackEntry?.savedStateHandle?.set(
                        KEY_PICKED_MUSIC,
                        musicJson.encodeToString(MusicSelection.serializer(), selection),
                    )
                    nav.popBackStack()
                },
            )
        }
        composable("browse") {
            SoundCloudPickerScreen(
                onBack = { nav.popBackStack() },
                onPicked = { nav.popBackStack() },
                startUrl = "https://soundcloud.com/signin",
            )
        }
        composable("session") {
            SessionScreen(
                onBack = {
                    if (!nav.popBackStack()) nav.navigate("home")
                },
                onOpenSettings = { nav.navigate("settings") },
            )
        }
        composable("settings") {
            SettingsScreen(
                vm = settingsVm,
                onBack = { nav.popBackStack() },
                onOpenSoundCloud = { nav.navigate("browse") },
            )
        }
    }
}
