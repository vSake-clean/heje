package pl.rytm.app.music

/**
 * „Strażnik” odtwarzania: muzyka ma grać przez całą sesję, dopóki sesja tego chce.
 *
 * Co kilka sekund odtwarzacz jest odpytywany (isPaused + pozycja – przez postMessage widżetu,
 * co działa także przy wygaszonym ekranie). Jeśli muzyka niespodziewanie stanęła (pauza wymuszona
 * przez system/słuchawki/inną aplikację, zawieszone buforowanie), strażnik najpierw ponawia
 * „play”, a gdy to nie pomaga – przeładowuje odtwarzacz od zapamiętanego miejsca.
 *
 * Czysta logika – bez Androida, w pełni testowalna.
 */
class PlaybackGuard(
    /** Ile kolejnych odczytów bez postępu uznajemy jeszcze za buforowanie. */
    private val toleratedStuckProbes: Int = 2,
    /** Po tylu ponowieniach „play” w oknie czasowym – przeładowanie odtwarzacza. */
    private val maxNudges: Int = 3,
    private val nudgeWindowMs: Long = 60_000,
    /** Odtwarzacz niegotowy tak długo → przeładowanie. */
    private val notReadyTimeoutMs: Long = 25_000,
) {
    enum class Action { NONE, PLAY, RELOAD }

    private var lastPosition = -1L
    private var stuckProbes = 0
    private val nudges = ArrayDeque<Long>()
    private var notReadySince: Long? = null

    fun reset() {
        lastPosition = -1L
        stuckProbes = 0
        nudges.clear()
        notReadySince = null
    }

    /**
     * @param wantPlaying czy sesja chce, żeby muzyka grała (false: pauza sesji, rozmowa, koniec)
     * @param online czy jest internet (bez sieci czekamy – tym zajmuje się logika sieci)
     * @param ready czy widżet zgłosił gotowość
     */
    fun onProbe(now: Long, wantPlaying: Boolean, online: Boolean, ready: Boolean, paused: Boolean, positionMs: Long): Action {
        if (!wantPlaying || !online) {
            reset()
            return Action.NONE
        }
        if (!ready) {
            val since = notReadySince ?: now.also { notReadySince = it }
            if (now - since >= notReadyTimeoutMs) {
                notReadySince = now
                return Action.RELOAD
            }
            return Action.NONE
        }
        notReadySince = null

        val advanced = lastPosition >= 0 && positionMs != lastPosition
        val first = lastPosition < 0
        lastPosition = positionMs
        if (!paused && (advanced || first)) {
            stuckProbes = 0
            return Action.NONE
        }
        if (!paused) {
            // Gra, ale pozycja stoi – może to chwilowe buforowanie.
            stuckProbes++
            if (stuckProbes <= toleratedStuckProbes) return Action.NONE
        }
        stuckProbes = 0
        while (nudges.isNotEmpty() && now - nudges.first() > nudgeWindowMs) nudges.removeFirst()
        return if (nudges.size >= maxNudges) {
            nudges.clear()
            Action.RELOAD
        } else {
            nudges.addLast(now)
            Action.PLAY
        }
    }
}
