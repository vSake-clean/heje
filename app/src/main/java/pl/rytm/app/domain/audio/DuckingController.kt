package pl.rytm.app.domain.audio

/**
 * Delikatne przyciszanie muzyki na czas komunikatów.
 *
 * Zwraca mnożnik głośności (1.0 = poziom ustawiony przez użytkownika). Muzyka:
 *  - nigdy nie jest zatrzymywana ani wyciszana (minimum to 1 − [duckFraction], domyślnie 75%),
 *  - przycisza się płynnie w czasie [attackMs] (krzywa „smoothstep”, bez skoków),
 *  - po zakończeniu komunikatu czeka [holdMs] (jeśli w tym czasie padnie kolejny komunikat,
 *    głośność nie wraca – brak „pompowania” między bliskimi komunikatami),
 *  - wraca płynnie do dokładnie poprzedniego poziomu w czasie [releaseMs].
 *
 * Bezpieczna wątkowo; czas podawany z zewnątrz (zegar monotoniczny) – w pełni testowalna.
 */
class DuckingController(
    duckFraction: Float = DEFAULT_DUCK_FRACTION,
    val attackMs: Long = DEFAULT_ATTACK_MS,
    val holdMs: Long = DEFAULT_HOLD_MS,
    val releaseMs: Long = DEFAULT_RELEASE_MS,
) {
    @Volatile
    var duckFraction: Float = duckFraction.coerceIn(0f, MAX_DUCK_FRACTION)
        set(value) {
            field = value.coerceIn(0f, MAX_DUCK_FRACTION)
        }

    val duckedLevel: Float get() = 1f - duckFraction

    private var segFrom = 1f
    private var segTo = 1f
    private var segStart = 0L
    private var segDuration = 0L
    private var activeRequests = 0
    private var releaseAt: Long? = null

    /** Rozpoczyna przyciszanie (np. tuż przed komunikatem). Wywołania można zagnieżdżać. */
    @Synchronized
    fun duck(now: Long) {
        activeRequests++
        releaseAt = null
        val current = levelLocked(now)
        val target = duckedLevel
        if (current <= target + EPS && segTo <= target + EPS) return
        startSegment(now, current, target, proportional(attackMs, current - target))
    }

    /** Kończy jedno żądanie przyciszenia; powrót zaczyna się po [holdMs], jeśli nie przyjdzie kolejne. */
    @Synchronized
    fun release(now: Long) {
        if (activeRequests == 0) return
        activeRequests--
        if (activeRequests == 0) releaseAt = now + holdMs
    }

    /** Natychmiast kończy wszystkie żądania (np. koniec sesji); powrót nadal jest płynny. */
    @Synchronized
    fun releaseAll(now: Long) {
        if (activeRequests == 0 && releaseAt == null) return
        activeRequests = 0
        releaseAt = now
    }

    /** Bieżący mnożnik głośności 0..1. */
    @Synchronized
    fun level(now: Long): Float = levelLocked(now)

    /** Czy obwiednia jest w spoczynku (pełna głośność, nic zaplanowanego) – wtedy można nie odświeżać. */
    @Synchronized
    fun isIdle(now: Long): Boolean {
        levelLocked(now)
        return activeRequests == 0 && releaseAt == null && segTo >= 1f - EPS && now >= segStart + segDuration
    }

    /** Czy poziom się w tej chwili nie zmienia (brak trwającego przejścia) – wtedy należy wysłać dokładną wartość. */
    @Synchronized
    fun isSettled(now: Long): Boolean {
        levelLocked(now)
        val pendingRelease = releaseAt
        return now >= segStart + segDuration && (pendingRelease == null || activeRequests > 0)
    }

    private fun levelLocked(now: Long): Float {
        val release = releaseAt
        if (release != null && now >= release && activeRequests == 0) {
            // Zaplanowany powrót: zaczyna się od poziomu w chwili `release`.
            val from = evalSegment(release)
            releaseAt = null
            startSegment(release, from, 1f, proportional(releaseMs, 1f - from))
        }
        return evalSegment(now)
    }

    private fun startSegment(now: Long, from: Float, to: Float, duration: Long) {
        segFrom = from
        segTo = to
        segStart = now
        segDuration = duration
    }

    private fun evalSegment(now: Long): Float {
        if (segDuration <= 0 || now >= segStart + segDuration) return segTo
        if (now <= segStart) return segFrom
        val t = (now - segStart).toFloat() / segDuration
        val eased = t * t * (3f - 2f * t)
        return segFrom + (segTo - segFrom) * eased
    }

    /** Krótszy ruch → proporcjonalnie krótszy czas (stała „prędkość”, brak gwałtownych zmian). */
    private fun proportional(fullMs: Long, distance: Float): Long {
        val full = duckFraction.takeIf { it > EPS } ?: return 0
        return (fullMs * (distance / full).coerceIn(0f, 1f)).toLong()
    }

    companion object {
        const val DEFAULT_DUCK_FRACTION = 0.25f
        const val MAX_DUCK_FRACTION = 0.6f
        const val DEFAULT_ATTACK_MS = 400L
        const val DEFAULT_HOLD_MS = 1_200L
        const val DEFAULT_RELEASE_MS = 900L
        private const val EPS = 0.0005f
    }
}
