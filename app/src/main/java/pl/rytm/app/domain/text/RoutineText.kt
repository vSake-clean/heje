package pl.rytm.app.domain.text

import pl.rytm.app.domain.model.AnnouncementConfig
import pl.rytm.app.domain.model.InterruptionBehavior
import pl.rytm.app.domain.model.MusicPlayback
import pl.rytm.app.domain.model.Routine
import pl.rytm.app.domain.model.RoutineTask
import pl.rytm.app.domain.model.ScheduleMode
import java.text.Normalizer

/**
 * Rutyna jako zwykły tekst – do wpisania ręcznie albo wklejenia odpowiedzi od AI.
 *
 * ```
 * NAZWA: Poranek przed szkołą
 * TRYB: ścisły
 * POBUDKA: 6:40
 * WYJŚCIE: 7:25
 * ZADANIA:
 * - 20 s | Wstań | Dzień dobry. Wstań z łóżka. | przerwa 10 s
 * - 15 min | Śniadanie | Śniadanie. Jedz spokojnie. | opcjonalne, min 10 min
 * ```
 *
 * Parser jest wyrozumiały: ignoruje bloki ``` i tekst wokół nich, pogrubienia `**`, numerację,
 * rozumie „2 minuty”, „półtorej minuty”, „1:30”, „1 min 30 s”, a błędy zgłasza z numerem linii.
 */
object RoutineText {

    data class Issue(val line: Int?, val message: String, val isError: Boolean)

    /** Wartości dla pól, których tekst nie podaje (z ustawień aplikacji). */
    data class Defaults(
        val mode: ScheduleMode = ScheduleMode.STRICT,
        val flexibleTimeoutSec: Int = 60,
        val interruption: InterruptionBehavior = InterruptionBehavior.CONTINUE_SCHEDULE,
        val musicPlayback: MusicPlayback = MusicPlayback.EMBEDDED,
    )

    data class Result(val routine: Routine?, val issues: List<Issue>, val musicLink: String?) {
        val errors: List<Issue> get() = issues.filter { it.isError }
        val warnings: List<Issue> get() = issues.filter { !it.isError }
        val canCreate: Boolean get() = routine != null && errors.isEmpty()
    }

    // ------------------------------------------------------------------ parsowanie

    private enum class Key {
        NAME, GOAL, MODE, TIMEOUT, WAKE, LEAVE, TARGET, ALARM, DAYS, INTERRUPTION, INCLUDE_DURATION,
        WARNING, START, END, VOICE, VOICE_VOLUME, MUSIC_VOLUME, MUSIC, LOOP, TASKS,
    }

    private val KEYS: Map<String, Key> = buildMap {
        listOf("nazwa", "tytul", "nazwarutyny").forEach { put(it, Key.NAME) }
        listOf("cel", "opis", "celrutyny").forEach { put(it, Key.GOAL) }
        listOf("tryb", "trybharmonogramu").forEach { put(it, Key.MODE) }
        listOf("czekanie", "limitczekania", "maksymalneczekanie", "limit", "czekanienapotwierdzenie").forEach { put(it, Key.TIMEOUT) }
        listOf("pobudka", "wstawanie", "godzinapobudki", "wstaje").forEach { put(it, Key.WAKE) }
        listOf("wyjscie", "godzinawyjscia", "wychodze").forEach { put(it, Key.LEAVE) }
        listOf("czassesji", "czascalkowity", "lacznyczas", "calkowityczas", "czascalosci").forEach { put(it, Key.TARGET) }
        listOf("budzik", "autostart", "uruchamiajautomatycznie").forEach { put(it, Key.ALARM) }
        listOf("dni", "dnitygodnia", "dnibudzika").forEach { put(it, Key.DAYS) }
        listOf("poprzerwaniu", "przerwanie", "przerwaniesesji", "gdyprzerwane").forEach { put(it, Key.INTERRUPTION) }
        listOf("podawajczas", "mowczas", "podawajczasnazadanie", "czaswkomunikacie").forEach { put(it, Key.INCLUDE_DURATION) }
        listOf("ostrzezenie", "ostrzezenieprzedkoncem", "przypomnienie").forEach { put(it, Key.WARNING) }
        listOf("powitanie", "nastart", "komunikatnastart", "start").forEach { put(it, Key.START) }
        listOf("koniec", "zakonczenie", "komunikatkoncowy", "komunikatnakoniec", "nakoniec").forEach { put(it, Key.END) }
        listOf("komunikaty", "glos", "komunikatyglosowe").forEach { put(it, Key.VOICE) }
        listOf("glosnoscglosu", "glosnosckomunikatow").forEach { put(it, Key.VOICE_VOLUME) }
        listOf("glosnoscmuzyki").forEach { put(it, Key.MUSIC_VOLUME) }
        listOf("muzyka", "soundcloud", "playlista", "utwor").forEach { put(it, Key.MUSIC) }
        listOf("zapetlaj", "zapetlanie", "zapetlajmuzyke").forEach { put(it, Key.LOOP) }
        listOf("zadania", "plan", "harmonogram", "czynnosci", "lista").forEach { put(it, Key.TASKS) }
    }

    private val BULLET = Regex("""^\s*(?:[-*•–—+]|\d{1,3}\s*[.)])\s+""")
    /** Notatka jako osobne pole zadania (po „|”, „,” lub „;”), np. „| notatka: klucze i telefon”. */
    private val NOTE = Regex("""(?i)[|,;]\s*(notatka|notka|instrukcje|instrukcja|uwagi|wskazówka|wskazowka)\s*:\s*(.*)$""")

    /** Notatka w osobnej linii pod zadaniem. */
    private val NOTE_LINE = Regex("""(?i)^(notatka|notka|instrukcje|instrukcja|uwagi|wskazówka|wskazowka)\s*:\s*(.+)$""")

    fun parse(text: String, defaults: Defaults = Defaults()): Result {
        val issues = mutableListOf<Issue>()
        val allLines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        // Odpowiedź AI z tekstem wokół bloku kodu: bierzemy tylko zawartość pierwszego bloku.
        val fenced = allLines.indices.filter { allLines[it].trim().startsWith("```") }
        val range = if (fenced.size >= 2) (fenced[0] + 1) until fenced[1] else allLines.indices

        var name: String? = null
        var goal = ""
        var mode: ScheduleMode? = null
        var timeout: Int? = null
        var wake: Int? = null
        var leave: Int? = null
        var target: Int? = null
        var alarm: Boolean? = null
        var alarmLine: Int? = null
        var days: Int? = null
        var interruption: InterruptionBehavior? = null
        var announcements = AnnouncementConfig()
        var musicVolume = 100
        var musicLink: String? = null
        var loop = true
        val tasks = mutableListOf<RoutineTask>()
        var inTasks = false

        for (i in range) {
            val lineNo = i + 1
            var line = allLines[i].trim()
            if (line.isEmpty() || line.startsWith("```") || line.startsWith("#") || line.startsWith("//")) continue
            line = line.replace("**", "").replace("__", "").replace("`", "").trim()
            if (line.isEmpty()) continue

            val bullet = BULLET.find(line)
            if (bullet != null) {
                parseTask(line.substring(bullet.range.last + 1), lineNo, issues)?.let { tasks += it }
                inTasks = true
                continue
            }

            val colon = line.indexOf(':')
            val key = if (colon > 0) KEYS[norm(line.substring(0, colon))] else null
            if (key != null) {
                val value = line.substring(colon + 1).trim()
                val empty = value.isEmpty() || norm(value) in setOf("brak", "nic", "", "opcjonalnie", "nd")
                fun err(msg: String) = issues.add(Issue(lineNo, msg, true))
                when (key) {
                    Key.TASKS -> inTasks = true
                    Key.NAME -> if (value.isNotEmpty()) name = value
                    Key.GOAL -> goal = value
                    Key.MODE -> if (!empty) mode = parseMode(value) ?: run { err("Tryb „$value” – wpisz „ścisły” albo „elastyczny”."); null }
                    Key.TIMEOUT -> if (!empty) timeout = parseDuration(value)?.seconds ?: run { err("Nie rozumiem czasu czekania „$value” (np. 60 s)."); null }
                    Key.WAKE -> if (!empty && !isNo(value)) wake = parseTimeOfDay(value) ?: run { err("Godzina pobudki „$value” – wpisz np. 6:30."); null }
                    Key.LEAVE -> if (!empty && !isNo(value)) leave = parseTimeOfDay(value) ?: run { err("Godzina wyjścia „$value” – wpisz np. 7:25."); null }
                    Key.TARGET -> if (!empty) target = parseDuration(value)?.seconds ?: run { err("Nie rozumiem czasu sesji „$value” (np. 30 min)."); null }
                    Key.ALARM -> if (!empty) {
                        alarm = parseYesNo(value.substringBefore(',').substringBefore(';'))
                            ?: run { err("Budzik: wpisz „tak” albo „nie”."); null }
                        alarmLine = lineNo
                        // „BUDZIK: tak, pn-pt”
                        val rest = value.substringAfter(',', "").ifBlank { value.substringAfter(';', "") }
                        if (rest.isNotBlank()) days = parseDays(rest) ?: days
                    }
                    Key.DAYS -> if (!empty) days = parseDays(value) ?: run { err("Dni „$value” – wpisz np. pn-pt, codziennie, weekend albo pn, śr, pt."); null }
                    Key.INTERRUPTION -> if (!empty) interruption = parseInterruption(value)
                        ?: run { err("Po przerwaniu: wpisz „kontynuuj”, „wstrzymaj” albo „wznów zadanie”."); null }
                    Key.INCLUDE_DURATION -> if (!empty) parseYesNo(value)?.let { announcements = announcements.copy(includeDuration = it) }
                        ?: err("Podawaj czas: wpisz „tak” albo „nie”.")
                    Key.WARNING -> {
                        val sec = if (empty || isNo(value)) 0 else parseDuration(value.substringBefore(" przed"))?.seconds
                        if (sec == null) err("Ostrzeżenie „$value” – wpisz np. 30 s albo „nie”.") else announcements = announcements.copy(warningBeforeEndSec = sec)
                    }
                    Key.START -> announcements = announcements.copy(startMessage = if (isNo(value)) "" else value)
                    Key.END -> announcements = announcements.copy(endMessage = if (isNo(value)) "" else value)
                    Key.VOICE -> if (!empty) parseYesNo(value)?.let { announcements = announcements.copy(enabled = it) }
                    Key.VOICE_VOLUME -> if (!empty) parsePercent(value)?.let { announcements = announcements.copy(volume = it) }
                        ?: err("Głośność głosu „$value” – wpisz np. 80%.")
                    Key.MUSIC_VOLUME -> if (!empty) parsePercent(value)?.let { musicVolume = it } ?: err("Głośność muzyki „$value” – wpisz np. 70%.")
                    Key.MUSIC -> if (!empty && !isNo(value)) musicLink = value
                    Key.LOOP -> if (!empty) parseYesNo(value)?.let { loop = it }
                }
                continue
            }

            val noteLine = if (inTasks && tasks.isNotEmpty()) NOTE_LINE.find(line) else null
            if (noteLine != null) {
                val last = tasks.removeAt(tasks.lastIndex)
                tasks += last.copy(notes = listOf(last.notes, noteLine.groupValues[2].trim()).filter { it.isNotBlank() }.joinToString(" "))
                continue
            }
            if (inTasks || line.contains('|')) {
                parseTask(line, lineNo, issues)?.let { tasks += it }
                inTasks = true
                continue
            }
            issues += Issue(lineNo, "Pominięto linię: „${line.take(60)}”.", false)
        }

        if (name.isNullOrBlank()) {
            issues += Issue(null, "Brak nazwy (linia „NAZWA: …”) – nadano nazwę „Rutyna z tekstu”.", false)
            name = "Rutyna z tekstu"
        }
        if (tasks.isEmpty()) issues += Issue(null, "Brak zadań. Dodaj linie w stylu „- 2 min | Ubierz się | Ubierz się.”.", true)
        if ((wake == null) != (leave == null)) {
            issues += Issue(null, "Podaj i POBUDKĘ, i WYJŚCIE – wtedy aplikacja sprawdzi, czy plan się mieści.", false)
        }
        var alarmEnabled = alarm ?: false
        if (alarmEnabled && wake == null) {
            issues += Issue(alarmLine, "Budzik wymaga godziny pobudki (POBUDKA: …) – wyłączono budzik.", false)
            alarmEnabled = false
        }
        if (days == 0) {
            issues += Issue(null, "Nie wybrano żadnego dnia budzika – przyjęto dni robocze.", false)
            days = null
        }

        val routine = Routine(
            name = name.trim(),
            goal = goal.trim(),
            tasks = tasks,
            mode = mode ?: defaults.mode,
            flexibleTimeoutSec = timeout ?: defaults.flexibleTimeoutSec,
            interruptionBehavior = interruption ?: defaults.interruption,
            targetDurationSec = target,
            wakeTimeMinutes = wake,
            leaveTimeMinutes = leave,
            alarmEnabled = alarmEnabled,
            alarmDays = days ?: Routine.WEEKDAYS,
            musicPlayback = defaults.musicPlayback,
            musicVolume = musicVolume,
            musicLoop = loop,
            announcements = announcements,
        )
        return Result(if (tasks.isEmpty()) null else routine, issues, musicLink)
    }

    private fun parseTask(body: String, lineNo: Int, issues: MutableList<Issue>): RoutineTask? {
        var text = body.trim()
        var notes = ""
        NOTE.find(text)?.let { m ->
            notes = m.groupValues[2].trim().trimEnd('|').trim()
            text = text.substring(0, m.range.first).trimEnd().trimEnd('|', ',', ';').trim()
        }
        val fields = splitFields(text)
        if (fields.isEmpty()) {
            issues += Issue(lineNo, "Puste zadanie.", true)
            return null
        }
        val durIdx = fields.indexOfFirst { parseDuration(it) != null }
        if (durIdx < 0) {
            issues += Issue(lineNo, "Brak czasu trwania w zadaniu „${fields.first().take(40)}” (np. „2 min | Ubierz się”).", true)
            return null
        }
        val duration = parseDuration(fields[durIdx])!!
        if (duration.unitless) {
            issues += Issue(lineNo, "„${fields[durIdx]}” bez jednostki – przyjęto ${duration.seconds} s. Dopisz „s” albo „min”.", false)
        }
        if (duration.seconds <= 0) {
            issues += Issue(lineNo, "Czas zadania musi być dłuższy niż 0.", true)
            return null
        }
        val rest = fields.filterIndexed { i, _ -> i != durIdx }.toMutableList()
        val title = rest.removeFirstOrNull()?.trim().orEmpty()
        if (title.isBlank()) {
            issues += Issue(lineNo, "Zadanie bez nazwy.", true)
            return null
        }
        // Drugie pole to polecenie głosowe – chyba że wygląda na same opcje („opcjonalne, min 1 min”).
        var spoken = ""
        if (rest.isNotEmpty() && !looksLikeOptions(rest.first())) spoken = rest.removeAt(0).trim()

        var mandatory = true
        var min: Int? = null
        var gap = 0
        for (field in rest) {
            for (raw in field.split(',', ';')) {
                val part = raw.trim()
                if (part.isEmpty()) continue
                when (val opt = parseOption(part)) {
                    is Opt.Optional -> mandatory = false
                    is Opt.Mandatory -> mandatory = true
                    is Opt.Min -> min = opt.seconds
                    is Opt.Gap -> gap = opt.seconds
                    null -> issues += Issue(lineNo, "Nieznana opcja „$part” – pominięto (znane: opcjonalne, min …, przerwa …, notatka: …).", false)
                }
            }
        }
        val minFixed = min?.let {
            if (it > duration.seconds) {
                issues += Issue(lineNo, "Minimum dłuższe niż czas zadania – pominięto minimum.", false)
                null
            } else {
                it
            }
        }
        if (minFixed != null && mandatory) {
            issues += Issue(lineNo, "„min” dotyczy tylko zadań opcjonalnych – zadanie oznaczono jako opcjonalne.", false)
            mandatory = false
        }
        return RoutineTask(
            title = title,
            spokenText = spoken,
            durationSec = duration.seconds,
            gapBeforeSec = gap,
            mandatory = mandatory,
            minDurationSec = minFixed,
            notes = notes,
        )
    }

    private sealed interface Opt {
        data object Optional : Opt
        data object Mandatory : Opt
        data class Min(val seconds: Int) : Opt
        data class Gap(val seconds: Int) : Opt
    }

    private val OPT_MIN = Regex("""(?i)^(?:min(?:imum|imalnie|\.)?|skróć do|skroc do|najkrócej|najkrocej)\s*:?\s*(.+)$""")
    private val OPT_GAP = Regex("""(?i)^(?:przerwa(?: przed)?|pauza|odstęp|odstep|po przerwie)\s*:?\s*(.+)$""")

    private fun parseOption(part: String): Opt? {
        val n = norm(part)
        if (n in setOf("opcjonalne", "opcjonalny", "opcjonalna", "opcjonalnie", "nieobowiazkowe", "nieobowiazkowy", "opcja", "optional")) return Opt.Optional
        if (n in setOf("obowiazkowe", "obowiazkowy", "obowiazkowa", "wymagane", "mandatory")) return Opt.Mandatory
        OPT_MIN.find(part.trim())?.let { m -> parseDuration(m.groupValues[1])?.let { return Opt.Min(it.seconds) } }
        OPT_GAP.find(part.trim())?.let { m -> parseDuration(m.groupValues[1])?.let { return Opt.Gap(it.seconds) } }
        return null
    }

    private fun looksLikeOptions(field: String): Boolean {
        val parts = field.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        return parts.isNotEmpty() && parts.all { parseOption(it) != null }
    }

    private val LOOSE_LEADING = Regex("""^(\d+:\d{2}(?::\d{2})?|\d+(?:[.,]\d+)?\s*[\p{L}.]*(?:\s+(?:i\s+)?\d+\s*[\p{L}.]+)?)\s*[-–—:.)]?\s+(.+)$""")
    private val LOOSE_TRAILING = Regex("""^(.+?)\s*(?:[-–—:(]\s*)(\d+:\d{2}|\d+(?:[.,]\d+)?\s*[\p{L}.]+(?:\s+(?:i\s+)?\d+\s*[\p{L}.]+)?)\s*\)?$""")

    /** Pola zadania: „|” (zalecane), „;” albo luźny zapis „2 min Ubierz się” / „Ubierz się – 2 min”. */
    private fun splitFields(text: String): List<String> {
        val parts = when {
            text.contains('|') -> text.split('|')
            text.contains(';') -> text.split(';')
            else -> {
                LOOSE_LEADING.find(text)?.takeIf { parseDuration(it.groupValues[1]) != null }?.let { listOf(it.groupValues[1], it.groupValues[2]) }
                    ?: LOOSE_TRAILING.find(text)?.takeIf { parseDuration(it.groupValues[2]) != null }?.let { listOf(it.groupValues[2], it.groupValues[1]) }
                    ?: listOf(text)
            }
        }
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }

    // ------------------------------------------------------------------ wartości

    data class Duration(val seconds: Int, val unitless: Boolean)

    private val DURATION_TOKEN = Regex("""(\d+(?:\.\d+)?)\s*(h|min|m|s)\b""")

    /** „15 s”, „2 min”, „2 minuty”, „1 min 30 s”, „1:30”, „1,5 min”, „półtorej minuty”, „kwadrans”. */
    fun parseDuration(input: String): Duration? {
        var t = input.trim().lowercase().replace(',', '.').trimEnd('.')
        if (t.isEmpty()) return null
        when (norm(t)) {
            "polminuty" -> return Duration(30, false)
            "poltorejminuty", "poltoraminuty" -> return Duration(90, false)
            "kwadrans" -> return Duration(900, false)
            "polgodziny" -> return Duration(1800, false)
            "godzina", "godzine" -> return Duration(3600, false)
            "minuta", "minute" -> return Duration(60, false)
        }
        if (Regex("""^\d+:\d{2}(:\d{2})?$""").matches(t)) return TimeText.parseDuration(t)?.let { Duration(it, false) }
        if (Regex("""^\d+$""").matches(t)) return t.toIntOrNull()?.let { Duration(it, true) }
        t = t.replace(Regex("""godzin\p{L}*|godz\.?"""), "h")
            .replace(Regex("""minut\p{L}*|min\.?"""), "min")
            .replace(Regex("""sekund\p{L}*|sek\.?|sec\p{L}*"""), "s")
            .replace(Regex("""\b(i|oraz|and)\b"""), " ")
        val matches = DURATION_TOKEN.findAll(t).toList()
        if (matches.isEmpty()) return null
        val consumed = matches.joinToString("") { it.value.replace(" ", "") }
        if (consumed != t.replace(" ", "")) return null
        var total = 0.0
        for (m in matches) {
            val n = m.groupValues[1].toDouble()
            total += when (m.groupValues[2]) {
                "h" -> n * 3600
                "min", "m" -> n * 60
                else -> n
            }
        }
        return Duration(Math.round(total).toInt(), false)
    }

    /** „6:30”, „06.30”, „7”, „6:30 rano” → minuty od północy. */
    fun parseTimeOfDay(input: String): Int? {
        val t = input.trim().lowercase()
        Regex("""(\d{1,2})[:.](\d{2})""").find(t)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            return if (h in 0..23 && min in 0..59) h * 60 + min else null
        }
        Regex("""^(\d{1,2})(?:\s*(?:rano|h|godz\.?))?$""").find(t)?.let { m ->
            val h = m.groupValues[1].toInt()
            return if (h in 0..23) h * 60 else null
        }
        return null
    }

    /** Skróty (dokładnie) i rdzenie pełnych nazw dni – „czasami” nie jest czwartkiem. */
    private val DAYS = listOf(
        setOf("pn", "pon") to "poniedzial",
        setOf("wt", "wto") to "wtor",
        setOf("sr", "sro") to "srod",
        setOf("cz", "czw") to "czwart",
        setOf("pt", "pia") to "piat",
        setOf("so", "sb", "sob") to "sobot",
        setOf("nd", "ndz", "niedz") to "niedziel",
    )

    private fun dayIndex(token: String): Int? {
        val n = norm(token)
        if (n.isEmpty()) return null
        return DAYS.indexOfFirst { (abbr, stem) -> n in abbr || n.startsWith(stem) }.takeIf { it >= 0 }
    }

    /** „pn-pt”, „codziennie”, „weekend”, „dni robocze”, „pn, śr, pt” → maska dni (bit 0 = poniedziałek). */
    fun parseDays(input: String): Int? {
        val n = norm(input)
        when {
            n.isEmpty() -> return null
            n in setOf("codziennie", "kazdegodnia", "wszystkie", "calytydzien", "zawsze", "pnnd", "ponnd") -> return Routine.ALL_DAYS
            n in setOf("dnirobocze", "wtygodniu", "robocze", "pnpt", "ponpt", "poniedzialekpiatek") -> return Routine.WEEKDAYS
            n in setOf("weekend", "weekendy", "wweekend", "sobotaniedziela", "sobnd", "sond") -> return 0b1100000
        }
        var mask = 0
        val tokens = input.lowercase().split(',', ';', '/', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        for (tok in tokens) {
            val range = tok.split(Regex("""\s*(?:-|–|—|\bdo\b)\s*""")).filter { it.isNotBlank() }
            if (range.size == 2) {
                val a = dayIndex(range[0].removePrefix("od").trim()) ?: return null
                val b = dayIndex(range[1]) ?: return null
                var d = a
                while (true) {
                    mask = mask or (1 shl d)
                    if (d == b) break
                    d = (d + 1) % 7
                }
            } else {
                for (word in tok.split(' ').filter { it.isNotBlank() }) {
                    val d = dayIndex(word) ?: return null
                    mask = mask or (1 shl d)
                }
            }
        }
        return mask
    }

    private fun parseMode(v: String): ScheduleMode? {
        val n = norm(v)
        return when {
            n.startsWith("elast") || n.startsWith("flex") || n.startsWith("luzn") -> ScheduleMode.FLEXIBLE
            n.startsWith("scis") || n.startsWith("strict") || n.startsWith("sztyw") -> ScheduleMode.STRICT
            else -> null
        }
    }

    private fun parseInterruption(v: String): InterruptionBehavior? {
        val n = norm(v)
        return when {
            n.startsWith("kontynu") || n.contains("wgplanu") || n.contains("dalej") -> InterruptionBehavior.CONTINUE_SCHEDULE
            n.startsWith("wznow") || n.contains("odbiezacego") || n.contains("odnowa") || n.contains("powtorz") -> InterruptionBehavior.RESUME_CURRENT_TASK
            n.startsWith("wstrzym") || n.startsWith("pauz") || n.startsWith("zatrzym") -> InterruptionBehavior.PAUSE
            else -> null
        }
    }

    private fun parseYesNo(v: String): Boolean? = when (norm(v)) {
        "tak", "t", "yes", "y", "true", "wlaczone", "wlaczony", "wlacz", "on", "1" -> true
        "nie", "n", "no", "false", "wylaczone", "wylaczony", "wylacz", "off", "0", "brak" -> false
        else -> null
    }

    private fun isNo(v: String) = parseYesNo(v) == false

    private fun parsePercent(v: String): Int? = Regex("""(\d{1,3})""").find(v)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..100 }

    /** Małe litery bez polskich znaków i bez spacji/interpunkcji – do porównywania kluczy. */
    fun norm(s: String): String {
        val lower = s.lowercase().replace('ł', 'l')
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        return decomposed.replace(Regex("""\p{M}"""), "").replace(Regex("""[^a-z0-9]"""), "")
    }

    /** Czy udostępniony tekst wygląda na rutynę w tym formacie. */
    fun looksLikeRoutine(text: String): Boolean {
        val lines = text.lines().map { it.trim().replace("**", "") }
        val hasHeader = lines.any { l -> l.indexOf(':').let { c -> c > 0 && KEYS[norm(l.substring(0, c))] in setOf(Key.NAME, Key.TASKS) } }
        val hasTask = lines.any { l ->
            val body = BULLET.find(l)?.let { l.substring(it.range.last + 1) } ?: l
            (BULLET.containsMatchIn(l) || l.contains('|')) && splitFields(body).any { parseDuration(it) != null }
        }
        return hasHeader && hasTask
    }

    // ------------------------------------------------------------------ zapis do tekstu

    fun format(r: Routine): String = buildString {
        fun line(key: String, value: String) = appendLine("$key: $value")
        line("NAZWA", clean(r.name))
        if (r.goal.isNotBlank()) line("CEL", clean(r.goal))
        line("TRYB", if (r.mode == ScheduleMode.FLEXIBLE) "elastyczny" else "ścisły")
        if (r.mode == ScheduleMode.FLEXIBLE) line("CZEKANIE", TimeText.short(r.flexibleTimeoutSec))
        r.wakeTimeMinutes?.let { line("POBUDKA", TimeText.timeOfDay(it)) }
        r.leaveTimeMinutes?.let { line("WYJŚCIE", TimeText.timeOfDay(it)) }
        r.targetDurationSec?.let { line("CZAS SESJI", TimeText.short(it)) }
        if (r.wakeTimeMinutes != null) line("BUDZIK", if (r.alarmEnabled) "tak" else "nie")
        if (r.alarmEnabled) line("DNI", daysText(r.alarmDays))
        line(
            "PO PRZERWANIU",
            when (r.interruptionBehavior) {
                InterruptionBehavior.CONTINUE_SCHEDULE -> "kontynuuj"
                InterruptionBehavior.PAUSE -> "wstrzymaj"
                InterruptionBehavior.RESUME_CURRENT_TASK -> "wznów zadanie"
            },
        )
        val a = r.announcements
        if (!a.enabled) line("KOMUNIKATY", "nie")
        line("PODAWAJ CZAS", if (a.includeDuration) "tak" else "nie")
        line("OSTRZEŻENIE", if (a.warningBeforeEndSec > 0) TimeText.short(a.warningBeforeEndSec) else "nie")
        if (a.startMessage.isNotBlank()) line("POWITANIE", clean(a.startMessage))
        line("KONIEC", if (a.endMessage.isBlank()) "nie" else clean(a.endMessage))
        if (a.volume != 100) line("GŁOŚNOŚĆ GŁOSU", "${a.volume}%")
        if (r.musicVolume != 100) line("GŁOŚNOŚĆ MUZYKI", "${r.musicVolume}%")
        if (!r.musicLoop) line("ZAPĘTLAJ", "nie")
        r.music?.let { line("MUZYKA", it.url) }
        appendLine("ZADANIA:")
        r.tasks.forEach { t ->
            val fields = mutableListOf(TimeText.short(t.durationSec), clean(t.title))
            if (t.spokenText.isNotBlank()) fields += clean(t.spokenText)
            val opts = buildList {
                if (t.gapBeforeSec > 0) add("przerwa ${TimeText.short(t.gapBeforeSec)}")
                if (!t.mandatory) add("opcjonalne")
                t.minDurationSec?.let { if (!t.mandatory) add("min ${TimeText.short(it)}") }
            }
            // Bez polecenia głosowego opcje stoją od razu po nazwie – parser rozpoznaje je jako opcje.
            if (opts.isNotEmpty()) fields += opts.joinToString(", ")
            if (t.notes.isNotBlank()) fields += "notatka: ${clean(t.notes)}"
            appendLine("- " + fields.joinToString(" | "))
        }
    }.trimEnd() + "\n"

    private fun clean(s: String) = s.replace('|', '/').replace('\n', ' ').trim()

    fun daysText(mask: Int): String {
        val names = listOf("pn", "wt", "śr", "cz", "pt", "so", "nd")
        return when (mask and 0x7F) {
            0x7F -> "codziennie"
            0x1F -> "pn-pt"
            0x60 -> "weekend"
            else -> names.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.joinToString(", ")
        }
    }

    // ------------------------------------------------------------------ polecenie dla AI

    val EXAMPLE: String = """
        NAZWA: Poranek przed szkołą
        CEL: Wyjść z domu o 7:25 po śniadaniu i bez pośpiechu.
        TRYB: ścisły
        POBUDKA: 6:40
        WYJŚCIE: 7:25
        BUDZIK: tak
        DNI: pn-pt
        PO PRZERWANIU: kontynuuj
        PODAWAJ CZAS: tak
        OSTRZEŻENIE: 60 s
        KONIEC: Czas wyjść z domu. Powodzenia!
        ZADANIA:
        - 20 s | Wstań | Dzień dobry. Wstań z łóżka. | przerwa 10 s
        - 1 min 30 s | Pościel łóżko | Pościel łóżko. | opcjonalne, min 45 s
        - 3 min | Łazienka | Toaleta i umycie twarzy.
        - 4 min | Ubierz się | Ubierz się.
        - 15 min | Śniadanie | Śniadanie. Jedz spokojnie. | opcjonalne, min 10 min
        - 2 min 30 s | Zęby | Umyj zęby.
        - 3 min | Plecak | Spakuj plecak. | notatka: Klucze, telefon, portfel, słuchawki.
        - 2 min | Buty i kurtka | Załóż buty i kurtkę.
        - 30 s | Wyjście | Wychodzisz. Powodzenia!
    """.trimIndent()

    val FORMAT_HELP: String = """
        NAZWA: <nazwa rutyny>
        CEL: <jedno zdanie>
        TRYB: ścisły | elastyczny
        CZEKANIE: <tylko tryb elastyczny: najdłuższe czekanie na „Zrobione”, np. 60 s>
        POBUDKA: <GG:MM – opcjonalnie>
        WYJŚCIE: <GG:MM – opcjonalnie>
        CZAS SESJI: <łączny czas, gdy nie ma pobudki i wyjścia – opcjonalnie>
        BUDZIK: tak | nie   (start o godzinie pobudki)
        DNI: pn-pt | codziennie | weekend | pn, śr, pt
        PO PRZERWANIU: kontynuuj | wstrzymaj | wznów zadanie
        PODAWAJ CZAS: tak | nie   (głos dodaje „Masz dwie minuty.”)
        OSTRZEŻENIE: <np. 30 s przed końcem zadania> | nie
        POWITANIE: <komunikat na start – opcjonalnie>
        KONIEC: <komunikat po ostatnim zadaniu>
        ZADANIA:
        - <czas> | <nazwa> | <polecenie głosowe> | <opcje>
          opcje (oddzielone przecinkami): opcjonalne, min <czas>, przerwa <czas przed zadaniem>, notatka: <tekst na ekran>
          czas: „15 s”, „2 min”, „1 min 30 s” albo „1:30”
    """.trimIndent()

    /** Gotowe polecenie dla ChatGPT / Gemini / Claude – po nim użytkownik opisuje swoje wymagania. */
    fun aiPrompt(requirements: String = ""): String = buildString {
        appendLine("Ułóż mi rutynę dla aplikacji Rytm (Android). Aplikacja prowadzi przez rutynę: gra muzyka, a o wyznaczonych porach polski głos mówi polecenie. W trybie ścisłym kolejne zadanie zaczyna się dokładnie po czasie poprzedniego – bez czekania na potwierdzenie.")
        appendLine()
        appendLine("Zasady:")
        appendLine("- Czasy realistyczne – tyle, ile czynność naprawdę trwa (np. ścielenie łóżka 1–2 min, mycie zębów 2–3 min, prysznic 5–10 min, ubieranie 3–5 min, śniadanie 10–20 min).")
        appendLine("- Polecenie głosowe krótkie i konkretne (do ok. 10 słów), po polsku, w trybie rozkazującym, zakończone kropką.")
        appendLine("- Każde zadanie trwa co najmniej 15 s (polecenie musi się zmieścić).")
        appendLine("- Zadania, które można skrócić lub pominąć przy braku czasu, oznacz „opcjonalne” i podaj „min” – najkrótszy sensowny czas.")
        appendLine("- Jeśli podam godzinę pobudki i wyjścia, suma czasów zadań i przerw ma się zmieścić między nimi z zapasem 3–10 minut.")
        appendLine("- Pierwsze zadanie może mieć „przerwa 10 s” – to wstęp muzyczny przed pierwszym poleceniem.")
        appendLine("- Odpowiedz WYŁĄCZNIE jednym blokiem kodu w formacie poniżej, bez komentarzy przed ani po.")
        appendLine()
        appendLine("FORMAT:")
        appendLine("```")
        appendLine(FORMAT_HELP)
        appendLine("```")
        appendLine()
        appendLine("PRZYKŁAD:")
        appendLine("```")
        appendLine(EXAMPLE)
        appendLine("```")
        appendLine()
        appendLine("MOJE WYMAGANIA:")
        append(
            requirements.trim().ifEmpty {
                "(opisz tutaj: o której wstajesz i o której musisz wyjść, jakie czynności chcesz zrobić, ile mniej więcej czasu na nie masz i co jest dla Ciebie najważniejsze)"
            },
        )
    }
}
