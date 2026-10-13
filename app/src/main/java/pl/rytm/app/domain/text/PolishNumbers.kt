package pl.rytm.app.domain.text

import java.util.Locale

/**
 * Polskie liczebniki i odmiana jednostek czasu (godzina, minuta, sekunda – rodzaj żeński),
 * żeby synteza mowy mówiła poprawnie: „Masz trzydzieści sekund”, „Zostały dwie minuty”.
 */
object PolishNumbers {

    private val units = arrayOf(
        "zero", "jeden", "dwa", "trzy", "cztery", "pięć", "sześć", "siedem", "osiem", "dziewięć",
        "dziesięć", "jedenaście", "dwanaście", "trzynaście", "czternaście", "piętnaście",
        "szesnaście", "siedemnaście", "osiemnaście", "dziewiętnaście",
    )
    private val tens = arrayOf(
        "", "", "dwadzieścia", "trzydzieści", "czterdzieści", "pięćdziesiąt",
        "sześćdziesiąt", "siedemdziesiąt", "osiemdziesiąt", "dziewięćdziesiąt",
    )
    private val hundreds = arrayOf(
        "", "sto", "dwieście", "trzysta", "czterysta", "pięćset",
        "sześćset", "siedemset", "osiemset", "dziewięćset",
    )

    enum class Case { NOMINATIVE, ACCUSATIVE }

    /** Liczebnik w rodzaju żeńskim (0..999). Dla 1 zależy od przypadku: „jedna” / „jedną”. */
    fun feminine(n: Int, case: Case = Case.NOMINATIVE): String {
        require(n in 0..999) { "Obsługiwany zakres 0..999" }
        if (n == 1) return if (case == Case.ACCUSATIVE) "jedną" else "jedna"
        if (n == 0) return units[0]
        val parts = mutableListOf<String>()
        val h = n / 100
        val rest = n % 100
        if (h > 0) parts += hundreds[h]
        if (rest in 1..19) {
            // W liczebnikach złożonych „jeden” się nie odmienia; „dwa” → „dwie”.
            parts += if (rest == 2) "dwie" else units[rest]
        } else if (rest >= 20) {
            parts += tens[rest / 10]
            val u = rest % 10
            if (u > 0) parts += if (u == 2) "dwie" else units[u]
        }
        return parts.joinToString(" ")
    }

    enum class PluralForm { ONE, FEW, MANY }

    fun pluralForm(n: Int): PluralForm = when {
        n == 1 -> PluralForm.ONE
        n % 10 in 2..4 && n % 100 !in 12..14 -> PluralForm.FEW
        else -> PluralForm.MANY
    }

    private data class Noun(val nomOne: String, val accOne: String, val few: String, val many: String) {
        fun form(n: Int, case: Case) = when (pluralForm(n)) {
            PluralForm.ONE -> if (case == Case.ACCUSATIVE) accOne else nomOne
            PluralForm.FEW -> few
            PluralForm.MANY -> many
        }
    }

    private val hour = Noun("godzina", "godzinę", "godziny", "godzin")
    private val minute = Noun("minuta", "minutę", "minuty", "minut")
    private val second = Noun("sekunda", "sekundę", "sekundy", "sekund")

    /** „trzydzieści sekund”, „jedną minutę i dziesięć sekund” (biernik po „Masz”). */
    fun duration(totalSec: Int, case: Case): String {
        val s = totalSec.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        val parts = mutableListOf<String>()
        if (h > 0) parts += "${feminine(h, case)} ${hour.form(h, case)}"
        if (m > 0) parts += "${feminine(m, case)} ${minute.form(m, case)}"
        if (sec > 0 || parts.isEmpty()) parts += "${feminine(sec, case)} ${second.form(sec, case)}"
        return when (parts.size) {
            1 -> parts[0]
            else -> parts.dropLast(1).joinToString(" ") + " i " + parts.last()
        }
    }

    /** „Masz trzydzieści sekund.” */
    fun youHave(totalSec: Int): String = "Masz ${duration(totalSec, Case.ACCUSATIVE)}."

    /** „Zostało dziesięć sekund.” / „Zostały dwie minuty.” / „Została jedna minuta.” */
    fun remaining(totalSec: Int): String {
        val text = duration(totalSec, Case.NOMINATIVE)
        // Orzeczenie uzgadniamy z pierwszą (największą) jednostką.
        val leading = when {
            totalSec >= 3600 -> totalSec / 3600
            totalSec >= 60 -> (totalSec % 3600) / 60
            else -> totalSec
        }
        val verb = when (pluralForm(leading)) {
            PluralForm.ONE -> "Została"
            PluralForm.FEW -> "Zostały"
            PluralForm.MANY -> "Zostało"
        }
        return "$verb $text."
    }
}

/** Formatowanie i parsowanie czasu w interfejsie: „1:05”, „01:02:03”. */
object TimeText {

    fun clock(totalMs: Long): String {
        val totalSec = (totalMs.coerceAtLeast(0) / 1000)
        return clockSec(totalSec)
    }

    /** Zaokrągla w górę – odliczanie pokazuje „0:01” aż do końca ostatniej sekundy. */
    fun countdown(remainingMs: Long): String {
        val sec = (remainingMs.coerceAtLeast(0) + 999) / 1000
        return clockSec(sec)
    }

    fun clockSec(totalSec: Long): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%02d:%02d", m, s)
    }

    /** Krótki opis długości: „15 s”, „1 min 30 s”, „1 h 5 min”. */
    fun short(totalSec: Int): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return buildList {
            if (h > 0) add("$h h")
            if (m > 0) add("$m min")
            if (s > 0 || isEmpty()) add("$s s")
        }.joinToString(" ")
    }

    /** Godzina zegarowa z minut od północy: „06:30”. */
    fun timeOfDay(minutes: Int): String {
        val m = ((minutes % 1440) + 1440) % 1440
        return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60)
    }

    /**
     * Parsuje „90”, „1:30”, „1:02:03”, „1m30s”, „45s”, „2 min”. Zwraca sekundy albo null.
     */
    fun parseDuration(input: String): Int? {
        val text = input.trim().lowercase().replace(',', '.')
        if (text.isEmpty()) return null
        if (text.contains(':')) {
            val parts = text.split(':').map { it.trim() }
            if (parts.size > 3 || parts.any { it.isEmpty() || it.any { c -> !c.isDigit() } }) return null
            val nums = parts.map { it.toInt() }
            return when (nums.size) {
                2 -> if (nums[1] < 60) nums[0] * 60 + nums[1] else null
                3 -> if (nums[1] < 60 && nums[2] < 60) nums[0] * 3600 + nums[1] * 60 + nums[2] else null
                else -> null
            }
        }
        if (text.all { it.isDigit() }) return text.toIntOrNull()
        val regex = Regex("""(\d+)\s*(godz|h|min|m|sek|s)""")
        val matches = regex.findAll(text).toList()
        if (matches.isEmpty()) return null
        val consumed = matches.joinToString("") { it.value.replace(" ", "") }
        if (consumed.length != text.replace(" ", "").length) return null
        var total = 0
        for (m in matches) {
            val n = m.groupValues[1].toInt()
            total += when (m.groupValues[2]) {
                "h", "godz" -> n * 3600
                "min", "m" -> n * 60
                else -> n
            }
        }
        return total
    }
}
