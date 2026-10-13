package pl.rytm.app.domain.speech

import pl.rytm.app.domain.model.AnnouncementConfig
import pl.rytm.app.domain.text.PolishNumbers

/** Szacuje, ile potrwa wypowiedzenie tekstu (ms). */
fun interface SpeechDurationEstimator {
    fun estimateMs(text: String): Long
}

/**
 * Heurystyka dla polskiej mowy syntetycznej przy tempie 1.0:
 * ok. 75 ms na literę, krótkie pauzy na spacjach i dłuższe na znakach interpunkcyjnych.
 * „Wstań z łóżka.” ≈ 1,5 s; „Rozpocznij poranną higienę.” ≈ 2,5 s.
 */
class HeuristicSpeechEstimator(private val speechRate: Float = 1f) : SpeechDurationEstimator {
    override fun estimateMs(text: String): Long {
        val t = text.trim()
        if (t.isEmpty()) return 0
        var ms = BASE_MS
        for (c in t) {
            ms += when {
                c.isLetterOrDigit() -> LETTER_MS
                c == ' ' -> SPACE_MS
                c == ',' || c == ';' || c == ':' || c == '–' || c == '-' -> COMMA_MS
                c == '.' || c == '!' || c == '?' -> SENTENCE_MS
                else -> 0.0
            }
        }
        val rate = speechRate.coerceIn(0.3f, 3f)
        return (ms / rate).toLong()
    }

    companion object {
        const val BASE_MS = 250.0
        const val LETTER_MS = 75.0
        const val SPACE_MS = 40.0
        const val COMMA_MS = 200.0
        const val SENTENCE_MS = 350.0
    }
}

/** Używa zmierzonych (zsyntezowanych) długości, a dla pozostałych tekstów – heurystyki. */
class MeasuredOrHeuristicEstimator(
    private val measured: Map<String, Long>,
    private val fallback: SpeechDurationEstimator,
) : SpeechDurationEstimator {
    override fun estimateMs(text: String): Long = measured[text.trim()] ?: fallback.estimateMs(text)
}

/** Składanie treści komunikatów z ustawień rutyny. */
object AnnouncementTexts {

    fun taskStart(spokenText: String, durationSec: Int, config: AnnouncementConfig, late: Boolean = false): String {
        val base = spokenText.trim().let { if (it.isNotEmpty() && it.last() !in ".!?") "$it." else it }
        val withDuration = if (config.includeDuration && durationSec > 0) {
            "$base ${PolishNumbers.youHave(durationSec)}"
        } else {
            base
        }
        return if (late) "Teraz: $withDuration" else withDuration
    }

    fun warning(remainingSec: Int): String = PolishNumbers.remaining(remainingSec)

    const val AWAITING_CONFIRMATION = "Czas minął. Potwierdź, gdy skończysz."
    const val RESUMED_PREFIX = "Wznawiam."
}
