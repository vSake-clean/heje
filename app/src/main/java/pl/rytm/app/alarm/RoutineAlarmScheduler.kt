package pl.rytm.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import pl.rytm.app.data.RoutineRepository
import pl.rytm.app.domain.model.Routine
import pl.rytm.app.ui.MainActivity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Obliczanie najbliższego terminu budzika (czysta funkcja, testowalna). */
object AlarmTimes {

    /** Bit dnia tygodnia: poniedziałek = bit 0 … niedziela = bit 6. */
    fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    /**
     * Najbliższy moment [minutesOfDay] w jeden z dni z maski [daysMask], ściśle po [now].
     * Uwzględnia zmiany czasu (ZonedDateTime).
     */
    fun next(now: ZonedDateTime, minutesOfDay: Int, daysMask: Int): ZonedDateTime? {
        if (daysMask and 0x7F == 0) return null
        val time = LocalTime.of((minutesOfDay / 60) % 24, minutesOfDay % 60)
        var date: LocalDate = now.toLocalDate()
        repeat(8) {
            if (daysMask and dayBit(date.dayOfWeek) != 0) {
                val candidate = ZonedDateTime.of(date, time, now.zone)
                if (candidate.isAfter(now)) return candidate
            }
            date = date.plusDays(1)
        }
        return null
    }
}

/**
 * Budzik rutyny: o godzinie pobudki system uruchamia sesję (AlarmManager.setAlarmClock –
 * najpewniejszy mechanizm, działa także w trybie Doze). Wymaga uprawnienia USE_EXACT_ALARM
 * (przyznawane automatycznie od Androida 13) lub SCHEDULE_EXACT_ALARM (Android 12).
 */
class RoutineAlarmScheduler(private val context: Context, private val repository: RoutineRepository) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    suspend fun rescheduleAll() {
        repository.getAll().forEach { schedule(it) }
    }

    /** Zwraca czas następnego uruchomienia albo null, gdy budzik jest wyłączony. */
    fun schedule(routine: Routine): Long? {
        cancel(routine.id)
        val wake = routine.wakeTimeMinutes ?: return null
        if (!routine.alarmEnabled || routine.id == 0L) return null
        val next = AlarmTimes.next(ZonedDateTime.now(ZoneId.systemDefault()), wake, routine.alarmDays) ?: return null
        val triggerAt = next.toInstant().toEpochMilli()
        if (!canScheduleExact()) return null
        val show = PendingIntent.getActivity(
            context, routine.id.toInt(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), fireIntent(routine.id))
        }.onFailure { return null }
        return triggerAt
    }

    fun cancel(routineId: Long) {
        alarmManager.cancel(fireIntent(routineId))
    }

    private fun fireIntent(routineId: Long): PendingIntent = PendingIntent.getBroadcast(
        context, routineId.toInt(),
        Intent(context, RoutineAlarmReceiver::class.java)
            .setAction(RoutineAlarmReceiver.ACTION_FIRE)
            .putExtra(RoutineAlarmReceiver.EXTRA_ROUTINE_ID, routineId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
