package pl.rytm.app.domain.model

import kotlinx.serialization.Serializable

/** Sposób prowadzenia harmonogramu. */
@Serializable
enum class ScheduleMode {
    /** Każde zadanie ma z góry ustalony start i koniec. Brak czekania na potwierdzenie. */
    STRICT,

    /** Można potwierdzać zadania; po czasie zadania aplikacja czeka maksymalnie `flexibleTimeoutSec`. */
    FLEXIBLE,
}

/** Co zrobić, gdy sesja zostanie przerwana (połączenie telefoniczne, zamknięcie procesu przez system). */
@Serializable
enum class InterruptionBehavior {
    /** Harmonogram biegnie dalej według pierwotnego planu. */
    CONTINUE_SCHEDULE,

    /** Rutyna zostaje wstrzymana do ręcznego wznowienia. */
    PAUSE,

    /** Po przerwie rutyna wraca do początku zadania, które trwało w chwili przerwania. */
    RESUME_CURRENT_TASK,
}

/** Kto odtwarza muzykę. */
@Serializable
enum class MusicPlayback {
    /** Bez muzyki. */
    NONE,

    /** Oficjalny odtwarzacz SoundCloud (HTML5 Widget) wewnątrz aplikacji – pełna kontrola głośności. */
    EMBEDDED,

    /** Oficjalna aplikacja SoundCloud – Rytm nie steruje jej głośnością ani odtwarzaniem. */
    EXTERNAL_APP,
}

@Serializable
enum class MusicKind { TRACK, PLAYLIST }

@Serializable
data class MusicSelection(
    val url: String,
    val title: String,
    val artist: String = "",
    val artworkUrl: String? = null,
    val kind: MusicKind = MusicKind.TRACK,
)

@Serializable
data class AnnouncementConfig(
    /** Czy wypowiadać komunikaty w tej rutynie. */
    val enabled: Boolean = true,
    /** Głośność komunikatów 0..100 (względem głośności systemowej). */
    val volume: Int = 100,
    /** Dodaje „Masz trzydzieści sekund.” po poleceniu. */
    val includeDuration: Boolean = false,
    /** Ostrzeżenie „Zostało dziesięć sekund.” przed końcem zadania; 0 = wyłączone. */
    val warningBeforeEndSec: Int = 0,
    /** Komunikat na samym początku sesji (opcjonalny). */
    val startMessage: String = "",
    /** Komunikat po ostatnim zadaniu. */
    val endMessage: String = "Rutyna zakończona.",
)

@Serializable
data class RoutineTask(
    val id: Long = 0,
    val title: String,
    /** Treść polecenia głosowego. Pusta = wypowiadany jest tytuł. */
    val spokenText: String = "",
    val durationSec: Int,
    /** Przerwa (cisza) przed tym zadaniem, liczona od końca poprzedniego. */
    val gapBeforeSec: Int = 0,
    /** Zadania obowiązkowe nigdy nie są automatycznie skracane ani usuwane. */
    val mandatory: Boolean = true,
    /** Minimalny czas przy automatycznym skracaniu (tylko zadania opcjonalne). */
    val minDurationSec: Int? = null,
    /** Dodatkowe instrukcje wyświetlane na ekranie sesji. */
    val notes: String = "",
) {
    val announcementText: String get() = spokenText.ifBlank { title }
}

data class Routine(
    val id: Long = 0,
    val name: String,
    val goal: String = "",
    val tasks: List<RoutineTask> = emptyList(),
    val mode: ScheduleMode = ScheduleMode.STRICT,
    val flexibleTimeoutSec: Int = 60,
    val interruptionBehavior: InterruptionBehavior = InterruptionBehavior.CONTINUE_SCHEDULE,
    /** Docelowy czas całej sesji, gdy nie podano godzin pobudki i wyjścia. */
    val targetDurationSec: Int? = null,
    /** Godzina pobudki w minutach od północy. */
    val wakeTimeMinutes: Int? = null,
    /** Godzina wyjścia w minutach od północy. */
    val leaveTimeMinutes: Int? = null,
    /** Automatyczny start rutyny o godzinie pobudki. */
    val alarmEnabled: Boolean = false,
    /** Dni tygodnia budzika: bit 0 = poniedziałek … bit 6 = niedziela. */
    val alarmDays: Int = WEEKDAYS,
    val music: MusicSelection? = null,
    val musicPlayback: MusicPlayback = MusicPlayback.EMBEDDED,
    /** Głośność muzyki w odtwarzaczu 0..100 (nie zmienia głośności systemowej). */
    val musicVolume: Int = 100,
    val musicLoop: Boolean = true,
    val announcements: AnnouncementConfig = AnnouncementConfig(),
    val lastUsedAt: Long? = null,
) {
    /**
     * Dostępny czas na rutynę w sekundach: różnica między wyjściem a pobudką
     * (również przez północ) albo docelowy czas sesji.
     */
    val availableWindowSec: Int?
        get() {
            val wake = wakeTimeMinutes
            val leave = leaveTimeMinutes
            if (wake != null && leave != null) {
                val minutes = ((leave - wake) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY
                return if (minutes == 0) null else minutes * 60
            }
            return targetDurationSec
        }

    val effectiveMusicPlayback: MusicPlayback
        get() = if (music == null) MusicPlayback.NONE else musicPlayback

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        const val WEEKDAYS = 0b0011111
        const val ALL_DAYS = 0b1111111
    }
}
