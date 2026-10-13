package pl.rytm.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import pl.rytm.app.container
import pl.rytm.app.service.Notifications
import pl.rytm.app.service.RoutineSessionService

/** O godzinie pobudki uruchamia sesję rutyny i planuje kolejny budzik. */
class RoutineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val routineId = intent.getLongExtra(EXTRA_ROUTINE_ID, 0L)
        if (routineId == 0L) return
        // Alarm dokładny pozwala uruchomić usługę pierwszoplanową z tła.
        RoutineSessionService.start(context, routineId, fromAlarm = true)
        val pending = goAsync()
        val c = context.container
        c.appScope.launch {
            try {
                c.routines.getRoutine(routineId)?.let { c.alarms.schedule(it) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "pl.rytm.app.ALARM_FIRE"
        const val EXTRA_ROUTINE_ID = "routine_id"
    }
}

/** Po restarcie telefonu, zmianie czasu lub aktualizacji aplikacji: ponownie ustawia budziki. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val c = context.container
        c.appScope.launch {
            try {
                c.alarms.rescheduleAll()
                if (intent.action == Intent.ACTION_BOOT_COMPLETED && c.sessionStore.hasSession()) {
                    // Android 15 nie pozwala uruchomić usługi odtwarzania zaraz po starcie systemu –
                    // prosimy użytkownika o dotknięcie powiadomienia.
                    Notifications.info(
                        context,
                        "Rutyna została przerwana",
                        "Telefon został uruchomiony ponownie w trakcie rutyny. Dotknij, aby ją wznowić.",
                        openSession = true,
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
