package pl.rytm.app.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import pl.rytm.app.container
import pl.rytm.app.data.session.SessionJson
import pl.rytm.app.data.settings.AppSettings
import pl.rytm.app.data.settings.ExternalDucking
import pl.rytm.app.data.settings.VoiceStream
import pl.rytm.app.domain.audio.DuckingController
import pl.rytm.app.domain.model.InterruptionBehavior
import pl.rytm.app.domain.model.MusicPlayback
import pl.rytm.app.domain.model.ScheduleMode
import pl.rytm.app.domain.session.ClockReading
import pl.rytm.app.domain.session.LiveSession
import pl.rytm.app.domain.session.MusicOutput
import pl.rytm.app.domain.session.MusicState
import pl.rytm.app.domain.session.Phase
import pl.rytm.app.domain.session.RunnerConfig
import pl.rytm.app.domain.session.SessionClock
import pl.rytm.app.domain.session.SessionEffects
import pl.rytm.app.domain.session.SessionEngine
import pl.rytm.app.domain.session.SessionPlan
import pl.rytm.app.domain.session.SessionRunner
import pl.rytm.app.domain.session.SessionSnapshot
import pl.rytm.app.domain.speech.HeuristicSpeechEstimator
import pl.rytm.app.domain.text.TimeText
import pl.rytm.app.music.ExternalSoundCloudOutput
import pl.rytm.app.music.SoundCloudWidgetPlayer
import pl.rytm.app.speech.AndroidSpeech
import pl.rytm.app.speech.SpeechConfig
import java.util.UUID
import java.util.concurrent.Executors

/** Zegar monotoniczny Androida (liczy także czas głębokiego uśpienia) + licznik uruchomień systemu. */
class AndroidSessionClock(context: Context) : SessionClock {
    private val bootCount = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    }.getOrDefault(-1)

    override fun now() = ClockReading(SystemClock.elapsedRealtime(), System.currentTimeMillis(), bootCount)
}

/**
 * Usługa pierwszoplanowa prowadząca sesję. Harmonogram, komunikaty i muzyka działają tutaj –
 * niezależnie od tego, czy ekran jest włączony i czy interfejs jest otwarty.
 */
class RoutineSessionService : Service(), SessionController {

    private val main = Handler(Looper.getMainLooper())
    private val engineExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "rytm-engine").apply { priority = Thread.MAX_PRIORITY }
    }
    private val engineScope = CoroutineScope(SupervisorJob() + engineExecutor.asCoroutineDispatcher())
    private val ioScope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    private var runner: SessionRunner? = null
    private var speech: AndroidSpeech? = null
    private var player: SoundCloudWidgetPlayer? = null
    private var music: MusicOutput? = null
    private var systemDucker: SystemDucker? = null
    private var callMonitor: CallMonitor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSessionCompat? = null
    private var settings = AppSettings()
    private var fromAlarm = false
    private var starting = false
    private var lastNotificationSignature: String? = null
    private var lastMediaSignature: String? = null
    private var sessionStartedElapsed = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!ensureForeground()) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> runner?.pause() ?: handleRecover()
            ACTION_RESUME -> runner?.resume() ?: handleRecover()
            ACTION_CONFIRM -> runner?.confirm() ?: handleRecover()
            ACTION_STOP -> if (runner != null) stop() else {
                container.sessionStore.clear()
                shutdown()
            }
            // null = system ponownie uruchomił usługę po zabiciu procesu (START_STICKY).
            else -> handleRecover()
        }
        return START_STICKY
    }

    private fun ensureForeground(): Boolean = try {
        // Każde wywołanie startForegroundService wymaga startForeground – pokazujemy bieżący stan sesji,
        // żeby akcje z powiadomienia (np. „Pauza”) nie podmieniały go na ekran „Przygotowuję…”.
        val current = SessionHub.live.value
        ServiceCompat.startForeground(
            this,
            Notifications.ID_SESSION,
            current?.let { sessionNotification(it) } ?: Notifications.preparing(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        true
    } catch (e: Exception) {
        // Np. ForegroundServiceStartNotAllowedException przy próbie startu z tła.
        Log.w(TAG, "Cannot start foreground", e)
        Notifications.info(this, "Rutyna wstrzymana", "Otwórz aplikację Rytm, aby kontynuować sesję.", openSession = true)
        stopSelf()
        false
    }

    // ------------------------------------------------------------------ start / odtworzenie

    private fun handleStart(intent: Intent) {
        if (runner != null || starting) {
            SessionHub.reportStartError("Inna rutyna już trwa. Zakończ ją, aby rozpocząć nową.")
            return
        }
        starting = true
        fromAlarm = intent.getBooleanExtra(EXTRA_FROM_ALARM, false)
        val routineId = intent.getLongExtra(EXTRA_ROUTINE_ID, 0L)
        val planJson = intent.getStringExtra(EXTRA_PLAN_JSON)
        ioScope.launch {
            val c = container
            settings = c.settings.current()
            val plan: SessionPlan? = planJson?.let { SessionJson.decodePlanOrNull(it) } ?: run {
                val routine = c.routines.getRoutine(routineId)
                when {
                    routine == null -> null
                    routine.tasks.isEmpty() -> null
                    else -> SessionPlan.from(
                        routine,
                        announceTiming = settings.announceTiming,
                        ttsGloballyEnabled = settings.ttsEnabled,
                    )
                }
            }
            if (plan == null || plan.entries.isEmpty()) {
                SessionHub.reportStartError("Ta rutyna nie ma żadnych zadań.")
                main.post {
                    starting = false
                    shutdown()
                }
                return@launch
            }
            c.routines.markUsed(plan.routineId)
            val snapshot = SessionEngine.start(UUID.randomUUID().toString(), plan, AndroidSessionClock(this@RoutineSessionService).now())
            main.post {
                starting = false
                SessionHub.clearEnded()
                SessionHub.clearStartError()
                beginSession(snapshot, announceCurrent = false, notice = null)
                if (fromAlarm) postAlarmFullScreen(plan.routineName)
            }
        }
    }

    private fun handleRecover() {
        if (runner != null || starting) return
        starting = true
        ioScope.launch {
            val c = container
            settings = c.settings.current()
            val saved = c.sessionStore.load()
            main.post {
                starting = false
                if (saved == null || saved.finished || saved.stoppedByUser) {
                    c.sessionStore.clear()
                    shutdown()
                    return@post
                }
                recoverFrom(saved)
            }
        }
    }

    private fun recoverFrom(saved: SessionSnapshot) {
        val clock = AndroidSessionClock(this)
        val recovery = SessionEngine.recover(saved, clock.now())
        val status = SessionEngine.evaluate(recovery.snapshot, clock.now().elapsed)
        if (status.phase == Phase.FINISHED && status.positionMs - status.effectiveTotalMs > STALE_SESSION_MS) {
            container.sessionStore.clear()
            Notifications.info(this, "Rutyna zakończona", "„${saved.plan.routineName}” zakończyła się, gdy aplikacja była zamknięta.")
            shutdown()
            return
        }
        val notice = if (recovery.interrupted) {
            val gap = TimeText.short((recovery.gapMs / 1000).toInt())
            when (saved.plan.interruptionBehavior) {
                InterruptionBehavior.CONTINUE_SCHEDULE -> "Sesja była przerwana ($gap). Kontynuuję według planu."
                InterruptionBehavior.PAUSE -> "Sesja była przerwana ($gap). Rutyna jest wstrzymana – naciśnij „Wznów”."
                InterruptionBehavior.RESUME_CURRENT_TASK -> "Sesja była przerwana ($gap). Wznowiono od bieżącego zadania."
            }
        } else {
            null
        }
        beginSession(recovery.snapshot, announceCurrent = true, notice = notice)
    }

    private fun beginSession(snapshot: SessionSnapshot, announceCurrent: Boolean, notice: String?) {
        val plan = snapshot.plan
        sessionStartedElapsed = SystemClock.elapsedRealtime()
        acquireWakeLock(plan)

        val useAlarmStream = settings.voiceStream == VoiceStream.ALARM || (fromAlarm && settings.alarmSessionsUseAlarmStream)
        val tts = AndroidSpeech(
            this,
            SpeechConfig(
                voiceName = settings.voiceName,
                rate = settings.speechRate,
                pitch = settings.speechPitch,
                volume = plan.announcements.volume / 100f,
                alarmStream = useAlarmStream,
            ),
        )
        speech = tts

        val output: MusicOutput? = when (plan.musicPlayback) {
            MusicPlayback.EMBEDDED -> {
                val p = SoundCloudWidgetPlayer(this)
                player = p
                SessionHub.setPlayer(p)
                FocusAwareMusic(this, p)
            }
            MusicPlayback.EXTERNAL_APP -> {
                if (settings.externalDucking == ExternalDucking.SYSTEM) {
                    systemDucker = SystemDucker(this, settings.duckHoldMs.toLong())
                }
                ExternalSoundCloudOutput()
            }
            MusicPlayback.NONE -> null
        }
        music = output

        val ducking = DuckingController(
            duckFraction = settings.duckPercent / 100f,
            attackMs = settings.duckAttackMs.toLong(),
            holdMs = settings.duckHoldMs.toLong(),
            releaseMs = settings.duckReleaseMs.toLong(),
        )
        val r = SessionRunner(
            scope = engineScope,
            clock = AndroidSessionClock(this),
            speech = tts,
            music = output,
            persistence = container.sessionStore,
            effects = effects,
            estimator = HeuristicSpeechEstimator(settings.speechRate),
            ducking = ducking,
            config = RunnerConfig(latencyCompensation = settings.latencyCompensation),
        )
        runner = r
        SessionHub.controller = this
        SessionHub.setFromAlarm(fromAlarm)
        setupMediaSession(plan)
        callMonitor = CallMonitor(this) { inCall -> runner?.onCallStateChanged(inCall) }.also { it.start() }
        r.start(snapshot, announceCurrent = announceCurrent, notice = notice)
    }

    // ------------------------------------------------------------------ efekty sesji

    private val effects = object : SessionEffects {
        override fun onLiveUpdate(live: LiveSession) {
            SessionHub.publish(live)
            main.post { updateNotification(live) }
        }

        override fun onTaskChanged(live: LiveSession) {
            if (settings.vibrateOnTaskChange && !live.status.paused &&
                (live.status.phase == Phase.ACTIVE || live.status.phase == Phase.AWAITING_CONFIRMATION)
            ) {
                vibrate()
            }
        }

        override fun onEnded(live: LiveSession?, stoppedByUser: Boolean) {
            main.post { finishSession(live, stoppedByUser) }
        }

        override fun onSpeechAudioFocus(speaking: Boolean) {
            systemDucker?.speaking(speaking)
        }
    }

    private fun updateNotification(live: LiveSession) {
        val texts = Notifications.texts(live)
        val signature = listOf(
            texts.title, texts.text, live.status.paused, live.status.phase, live.status.taskIndex,
            live.music.title, (texts.countdownEndWall ?: 0) / 3_000,
        ).joinToString("|")
        val effectiveSignature = if (settings.taskNotifications) signature else "${live.plan.routineName}|${live.status.paused}|${live.status.phase == Phase.FINISHED}"
        if (effectiveSignature != lastNotificationSignature) {
            lastNotificationSignature = effectiveSignature
            if (Notifications.canPost(this)) {
                @Suppress("MissingPermission")
                NotificationManagerCompat.from(this).notify(Notifications.ID_SESSION, sessionNotification(live))
            }
        }
        updateMediaSession(live, texts)
    }

    /** Powiadomienie sesji; przy wyłączonych „powiadomieniach o zadaniach” – wersja ogólna (bez treści zadania). */
    private fun sessionNotification(live: LiveSession) =
        if (settings.taskNotifications) Notifications.session(this, live) else Notifications.sessionMinimal(this, live)

    private fun vibrate() {
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 90, 70, 90), -1)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(effect)
            }
        }
    }

    private fun postAlarmFullScreen(routineName: String) {
        if (!Notifications.canPost(this)) return
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(this).notify(Notifications.ID_ALARM, Notifications.alarmStarted(this, routineName))
    }

    // ------------------------------------------------------------------ MediaSession (słuchawki, ekran blokady)

    private fun setupMediaSession(plan: SessionPlan) {
        mediaSession?.release()
        mediaSession = MediaSessionCompat(this, "Rytm").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onStop() = stop()
                override fun onSkipToNext() {
                    if (plan.mode == ScheduleMode.FLEXIBLE) confirm()
                }
            })
            setSessionActivity(Notifications.openAppIntent(this@RoutineSessionService))
            isActive = true
        }
    }

    private fun updateMediaSession(live: LiveSession, texts: Notifications.Texts) {
        val session = mediaSession ?: return
        val st = live.status
        val signature = "${texts.title}|${texts.text}|${st.paused}|${st.phase}|${st.taskIndex}"
        if (signature == lastMediaSignature) return
        lastMediaSignature = signature
        val idx = st.taskIndex
        val duration = if (idx != null && (st.phase == Phase.ACTIVE)) st.effectiveEndsMs[idx] - st.effectiveStartsMs[idx] else -1L
        val position = if (idx != null && st.phase == Phase.ACTIVE) st.positionMs - st.effectiveStartsMs[idx] else 0L
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, texts.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, texts.text)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, live.plan.routineName)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                .build(),
        )
        var actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP
        if (live.plan.mode == ScheduleMode.FLEXIBLE) actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(
                    if (st.paused) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_PLAYING,
                    position,
                    if (st.paused) 0f else 1f,
                    SystemClock.elapsedRealtime(),
                )
                .build(),
        )
    }

    // ------------------------------------------------------------------ SessionController

    override fun pause() {
        runner?.pause()
    }

    override fun resume() {
        runner?.resume()
    }

    override fun confirm() {
        runner?.confirm()
    }

    override fun stop() {
        runner?.stop() ?: shutdown()
    }

    override fun retryMusic() {
        val p = player ?: return
        if (runner?.currentSnapshot?.paused == true) p.retry() else music?.play()
    }

    // ------------------------------------------------------------------ zakończenie

    private fun finishSession(live: LiveSession?, stoppedByUser: Boolean) {
        val plan = live?.plan ?: runner?.currentSnapshot?.plan
        SessionHub.markEnded(
            plan?.let {
                EndedSession(
                    routineName = it.routineName,
                    stoppedByUser = stoppedByUser,
                    durationMs = SystemClock.elapsedRealtime() - sessionStartedElapsed,
                )
            },
        )
        shutdown()
    }

    private fun shutdown() {
        SessionHub.publish(null)
        SessionHub.controller = null
        SessionHub.setPlayer(null)
        SessionHub.setFromAlarm(false)
        callMonitor?.stop()
        callMonitor = null
        music?.stop()
        music = null
        player = null
        systemDucker?.release()
        systemDucker = null
        speech?.shutdown()
        speech = null
        runner = null
        mediaSession?.run {
            isActive = false
            release()
        }
        mediaSession = null
        releaseWakeLock()
        getSystemService(NotificationManager::class.java)?.cancel(Notifications.ID_ALARM)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (runner != null) {
            // System niszczy usługę w trakcie sesji – stan zostaje zapisany i zostanie odtworzony.
            runner?.currentSnapshot?.let { container.sessionStore.save(it, sync = true) }
            callMonitor?.stop()
            music?.stop()
            speech?.shutdown()
            mediaSession?.release()
            releaseWakeLock()
            SessionHub.publish(null)
            SessionHub.controller = null
            SessionHub.setPlayer(null)
        }
        engineScope.cancel()
        ioScope.cancel()
        engineExecutor.shutdown()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ blokada uśpienia

    private fun acquireWakeLock(plan: SessionPlan) {
        releaseWakeLock()
        val pm = getSystemService(PowerManager::class.java) ?: return
        val timeout = plan.plannedTotalMs + plan.entries.size * plan.effectiveTimeoutMs + 2 * 60 * 60_000L
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Rytm:session").apply {
            setReferenceCounted(false)
            acquire(timeout)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        private const val TAG = "RoutineSession"
        const val ACTION_START = "pl.rytm.app.action.START"
        const val ACTION_RECOVER = "pl.rytm.app.action.RECOVER"
        const val ACTION_PAUSE = "pl.rytm.app.action.PAUSE"
        const val ACTION_RESUME = "pl.rytm.app.action.RESUME"
        const val ACTION_CONFIRM = "pl.rytm.app.action.CONFIRM"
        const val ACTION_STOP = "pl.rytm.app.action.STOP"
        const val EXTRA_ROUTINE_ID = "routine_id"
        const val EXTRA_PLAN_JSON = "plan_json"
        const val EXTRA_FROM_ALARM = "from_alarm"
        private const val STALE_SESSION_MS = 10 * 60_000L

        /** Uruchamia rutynę. [plan] – np. plan dopasowany do godziny wyjścia (bez zapisu w rutynie). */
        fun start(context: Context, routineId: Long, plan: SessionPlan? = null, fromAlarm: Boolean = false) {
            val intent = Intent(context, RoutineSessionService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ROUTINE_ID, routineId)
                .putExtra(EXTRA_FROM_ALARM, fromAlarm)
            plan?.let { intent.putExtra(EXTRA_PLAN_JSON, SessionJson.encodePlan(it)) }
            ContextCompat.startForegroundService(context, intent)
        }

        /** Odtwarza sesję zapisaną na dysku (np. po zamknięciu procesu przez system). */
        fun recover(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RoutineSessionService::class.java).setAction(ACTION_RECOVER),
            )
        }
    }
}

/** Muzyka z fokusem audio: pauza, gdy inna aplikacja przejmie dźwięk; wznowienie, gdy go odda. */
private class FocusAwareMusic(context: Context, private val player: SoundCloudWidgetPlayer) : MusicOutput {
    @Volatile private var sessionWantsPlay = false
    private val focus = MusicFocus(
        context,
        onPause = { player.interrupt() },
        onResume = { if (sessionWantsPlay) player.play() },
    )

    override val state: kotlinx.coroutines.flow.StateFlow<MusicState> get() = player.state

    override fun prepare(plan: SessionPlan, autoPlay: Boolean) {
        sessionWantsPlay = autoPlay
        if (autoPlay) focus.requestForPlayback()
        player.prepare(plan, autoPlay)
    }

    override fun play() {
        sessionWantsPlay = true
        focus.requestForPlayback()
        player.play()
    }

    override fun pause() {
        sessionWantsPlay = false
        player.pause()
    }

    override fun setVolume(volume: Float) = player.setVolume(volume)

    override fun stop() {
        sessionWantsPlay = false
        focus.abandon()
        player.stop()
    }
}
