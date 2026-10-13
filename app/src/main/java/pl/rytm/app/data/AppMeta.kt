package pl.rytm.app.data

import android.content.Context

/** Drobne metadane aplikacji (np. wersja zestawu przykładowych rutyn). */
class AppMeta(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_meta", Context.MODE_PRIVATE)

    /** -1 = brak zapisu (pierwsze uruchomienie albo aktualizacja z wersji 1.0.0). */
    var samplesVersion: Int
        get() = prefs.getInt(KEY_SAMPLES, -1)
        set(value) {
            prefs.edit().putInt(KEY_SAMPLES, value).apply()
        }

    private companion object {
        const val KEY_SAMPLES = "samples_version"
    }
}
