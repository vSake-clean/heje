package pl.rytm.app.domain.speech

import pl.rytm.app.domain.model.ScheduleMode
import pl.rytm.app.domain.session.SessionPlan

/**
 * Wszystkie teksty, które mogą paść w danej sesji – znane z góry, więc można je zsyntezować
 * przed czasem i odtwarzać natychmiast (bez opóźnienia syntezy i bez zajmowania dźwięku przez silnik mowy).
 */
object SpeechCatalog {

    fun textsFor(plan: SessionPlan): List<String> {
        val cfg = plan.announcements
        if (!cfg.enabled) return emptyList()
        val out = LinkedHashSet<String>()
        plan.entries.forEachIndexed { i, e ->
            val text = AnnouncementTexts.taskStart(e.spokenText, plan.taskDurationsSec.getOrElse(i) { 0 }, cfg)
            out += text
        }
        // Pierwsze polecenie w kolejności – najpierw najbliższe, potem warianty „Teraz: …” (po przerwach).
        plan.entries.forEachIndexed { i, e ->
            out += "Teraz: " + AnnouncementTexts.taskStart(e.spokenText, plan.taskDurationsSec.getOrElse(i) { 0 }, cfg)
        }
        if (cfg.warningBeforeEndSec > 0) out += AnnouncementTexts.warning(cfg.warningBeforeEndSec)
        if (plan.mode == ScheduleMode.FLEXIBLE && plan.flexibleTimeoutMs > 0) out += AnnouncementTexts.AWAITING_CONFIRMATION
        cfg.startMessage.trim().takeIf { it.isNotEmpty() }?.let { out.add(it) }
        cfg.endMessage.trim().takeIf { it.isNotEmpty() }?.let { out.add(it) }
        return out.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    /** Kolejność syntezy: komunikat startowy i pierwsze polecenia jak najwcześniej. */
    fun prioritized(plan: SessionPlan): List<String> {
        val all = textsFor(plan)
        val start = plan.announcements.startMessage.trim()
        return (listOfNotNull(start.takeIf { it.isNotEmpty() }) + all).distinct()
    }
}
