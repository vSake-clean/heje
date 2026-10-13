package pl.rytm.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pl.rytm.app.domain.model.InterruptionBehavior
import pl.rytm.app.domain.model.MusicPlayback
import pl.rytm.app.domain.model.ScheduleMode
import pl.rytm.app.domain.session.AnnounceTiming

/** Jak ściszać zewnętrzną aplikację SoundCloud podczas komunikatów (nie mamy dostępu do jej głośności). */
enum class ExternalDucking {
    /** Prosimy system o przyciszenie (AudioFocus „may duck”) – poziom ustala Android, zwykle mocniej niż 25%. */
    SYSTEM,

    /** Bez przyciszania – głos nakłada się na muzykę. */
    NONE,
}

/** Którym suwakiem głośności steruje się komunikatami. */
enum class VoiceStream { MEDIA, ALARM }

data class AppSettings(
    val onboardingDone: Boolean = false,
    val ttsEnabled: Boolean = true,
    val voiceName: String? = null,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val duckPercent: Int = 25,
    val duckAttackMs: Int = 400,
    val duckReleaseMs: Int = 900,
    val duckHoldMs: Int = 1200,
    val announceTiming: AnnounceTiming = AnnounceTiming.AT_TASK_START,
    val latencyCompensation: Boolean = true,
    val defaultMode: ScheduleMode = ScheduleMode.STRICT,
    val defaultFlexTimeoutSec: Int = 60,
    val defaultInterruption: InterruptionBehavior = InterruptionBehavior.CONTINUE_SCHEDULE,
    val defaultMusicPlayback: MusicPlayback = MusicPlayback.EMBEDDED,
    val vibrateOnTaskChange: Boolean = true,
    val taskNotifications: Boolean = true,
    val keepScreenOn: Boolean = false,
    val showOverLockScreen: Boolean = true,
    val externalDucking: ExternalDucking = ExternalDucking.SYSTEM,
    val voiceStream: VoiceStream = VoiceStream.MEDIA,
    /**
     * Dla sesji uruchamianych budzikiem komunikaty idą strumieniem budzika (słychać je nawet przy ściszonych
     * multimediach). Domyślnie wyłączone – przy słuchawkach i tak zawsze używamy multimediów.
     */
    val alarmSessionsUseAlarmStream: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val ttsEnabled = booleanPreferencesKey("tts_enabled")
        val voiceName = stringPreferencesKey("voice_name")
        val speechRate = floatPreferencesKey("speech_rate")
        val speechPitch = floatPreferencesKey("speech_pitch")
        val duckPercent = intPreferencesKey("duck_percent")
        val duckAttackMs = intPreferencesKey("duck_attack_ms")
        val duckReleaseMs = intPreferencesKey("duck_release_ms")
        val duckHoldMs = intPreferencesKey("duck_hold_ms")
        val announceTiming = stringPreferencesKey("announce_timing")
        val latencyCompensation = booleanPreferencesKey("latency_compensation")
        val defaultMode = stringPreferencesKey("default_mode")
        val defaultFlexTimeoutSec = intPreferencesKey("default_flex_timeout_sec")
        val defaultInterruption = stringPreferencesKey("default_interruption")
        val defaultMusicPlayback = stringPreferencesKey("default_music_playback")
        val vibrateOnTaskChange = booleanPreferencesKey("vibrate_on_task_change")
        val taskNotifications = booleanPreferencesKey("task_notifications")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val showOverLockScreen = booleanPreferencesKey("show_over_lock_screen")
        val externalDucking = stringPreferencesKey("external_ducking")
        val voiceStream = stringPreferencesKey("voice_stream")
        val alarmSessionsUseAlarmStream = booleanPreferencesKey("alarm_sessions_alarm_stream")
        val settingsVersion = intPreferencesKey("settings_version")
    }

    /** Jednorazowe poprawki ustawień po aktualizacji aplikacji. */
    suspend fun migrateIfNeeded() {
        context.dataStore.edit { p ->
            val version = p[Keys.settingsVersion] ?: 1
            if (version < 2) {
                // 1.0.0 domyślnie kierował głos z budzika na głośnik – przy słuchawkach to przeszkadza.
                p[Keys.alarmSessionsUseAlarmStream] = false
            }
            p[Keys.settingsVersion] = CURRENT_VERSION
        }
    }

    private companion object {
        const val CURRENT_VERSION = 2
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { fromPrefs(it) }

    private fun fromPrefs(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            onboardingDone = p[Keys.onboardingDone] ?: d.onboardingDone,
            ttsEnabled = p[Keys.ttsEnabled] ?: d.ttsEnabled,
            voiceName = p[Keys.voiceName]?.takeIf { it.isNotBlank() },
            speechRate = p[Keys.speechRate] ?: d.speechRate,
            speechPitch = p[Keys.speechPitch] ?: d.speechPitch,
            duckPercent = p[Keys.duckPercent] ?: d.duckPercent,
            duckAttackMs = p[Keys.duckAttackMs] ?: d.duckAttackMs,
            duckReleaseMs = p[Keys.duckReleaseMs] ?: d.duckReleaseMs,
            duckHoldMs = p[Keys.duckHoldMs] ?: d.duckHoldMs,
            announceTiming = enumOr(p[Keys.announceTiming], d.announceTiming),
            latencyCompensation = p[Keys.latencyCompensation] ?: d.latencyCompensation,
            defaultMode = enumOr(p[Keys.defaultMode], d.defaultMode),
            defaultFlexTimeoutSec = p[Keys.defaultFlexTimeoutSec] ?: d.defaultFlexTimeoutSec,
            defaultInterruption = enumOr(p[Keys.defaultInterruption], d.defaultInterruption),
            defaultMusicPlayback = enumOr(p[Keys.defaultMusicPlayback], d.defaultMusicPlayback),
            vibrateOnTaskChange = p[Keys.vibrateOnTaskChange] ?: d.vibrateOnTaskChange,
            taskNotifications = p[Keys.taskNotifications] ?: d.taskNotifications,
            keepScreenOn = p[Keys.keepScreenOn] ?: d.keepScreenOn,
            showOverLockScreen = p[Keys.showOverLockScreen] ?: d.showOverLockScreen,
            externalDucking = enumOr(p[Keys.externalDucking], d.externalDucking),
            voiceStream = enumOr(p[Keys.voiceStream], d.voiceStream),
            alarmSessionsUseAlarmStream = p[Keys.alarmSessionsUseAlarmStream] ?: d.alarmSessionsUseAlarmStream,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val n = transform(fromPrefs(p))
            p[Keys.onboardingDone] = n.onboardingDone
            p[Keys.ttsEnabled] = n.ttsEnabled
            p[Keys.voiceName] = n.voiceName.orEmpty()
            p[Keys.speechRate] = n.speechRate
            p[Keys.speechPitch] = n.speechPitch
            p[Keys.duckPercent] = n.duckPercent
            p[Keys.duckAttackMs] = n.duckAttackMs
            p[Keys.duckReleaseMs] = n.duckReleaseMs
            p[Keys.duckHoldMs] = n.duckHoldMs
            p[Keys.announceTiming] = n.announceTiming.name
            p[Keys.latencyCompensation] = n.latencyCompensation
            p[Keys.defaultMode] = n.defaultMode.name
            p[Keys.defaultFlexTimeoutSec] = n.defaultFlexTimeoutSec
            p[Keys.defaultInterruption] = n.defaultInterruption.name
            p[Keys.defaultMusicPlayback] = n.defaultMusicPlayback.name
            p[Keys.vibrateOnTaskChange] = n.vibrateOnTaskChange
            p[Keys.taskNotifications] = n.taskNotifications
            p[Keys.keepScreenOn] = n.keepScreenOn
            p[Keys.showOverLockScreen] = n.showOverLockScreen
            p[Keys.externalDucking] = n.externalDucking.name
            p[Keys.voiceStream] = n.voiceStream.name
            p[Keys.alarmSessionsUseAlarmStream] = n.alarmSessionsUseAlarmStream
        }
    }

    private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
        value?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default
}
