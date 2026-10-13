package pl.rytm.app.data.session

import android.content.Context
import kotlinx.serialization.json.Json
import pl.rytm.app.domain.session.SessionPersistence
import pl.rytm.app.domain.session.SessionSnapshot

/**
 * Trwały zapis bieżącej sesji – pozwala odtworzyć harmonogram po zamknięciu procesu przez system.
 * Zapis synchroniczny (commit) przy zmianach istotnych, asynchroniczny przy „biciu serca”.
 */
class SessionStore(context: Context) : SessionPersistence {

    private val prefs = context.applicationContext.getSharedPreferences("active_session", Context.MODE_PRIVATE)

    override fun save(snapshot: SessionSnapshot, sync: Boolean) {
        val editor = prefs.edit().putString(KEY, SessionJson.encode(snapshot))
        if (sync) editor.commit() else editor.apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY).commit()
    }

    fun load(): SessionSnapshot? = prefs.getString(KEY, null)?.let { SessionJson.decodeOrNull(it) }

    fun hasSession(): Boolean = prefs.contains(KEY)

    private companion object {
        const val KEY = "snapshot_v1"
    }
}

object SessionJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(s: SessionSnapshot): String = json.encodeToString(SessionSnapshot.serializer(), s)

    fun decodeOrNull(text: String): SessionSnapshot? =
        runCatching { json.decodeFromString(SessionSnapshot.serializer(), text) }.getOrNull()

    fun encodePlan(p: pl.rytm.app.domain.session.SessionPlan): String =
        json.encodeToString(pl.rytm.app.domain.session.SessionPlan.serializer(), p)

    fun decodePlanOrNull(text: String): pl.rytm.app.domain.session.SessionPlan? =
        runCatching { json.decodeFromString(pl.rytm.app.domain.session.SessionPlan.serializer(), text) }.getOrNull()
}
