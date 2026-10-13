package pl.rytm.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pl.rytm.app.R
import pl.rytm.app.domain.model.ScheduleMode
import pl.rytm.app.domain.session.LiveSession
import pl.rytm.app.domain.session.Phase
import pl.rytm.app.domain.text.TimeText
import pl.rytm.app.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Notifications {
    const val CHANNEL_SESSION = "session"
    const val CHANNEL_ALARM = "alarm"
    const val CHANNEL_INFO = "info"

    const val ID_SESSION = 1001
    const val ID_ALARM = 1002
    const val ID_INFO = 1003

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SESSION, "Trwająca rutyna", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Bieżące zadanie, odliczanie i sterowanie sesją."
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, "Start rutyny z budzika", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pokazuje ekran rutyny, gdy uruchomi się o godzinie pobudki."
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_INFO, "Informacje", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Np. informacja o przerwanej sesji."
            },
        )
    }

    fun openAppIntent(context: Context, openSession: Boolean = true): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_SESSION, openSession)
        return PendingIntent.getActivity(
            context, if (openSession) 1 else 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun serviceAction(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            context, code,
            Intent(context, RoutineSessionService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun preparing(context: Context, title: String = "Przygotowuję rutynę…"): Notification =
        NotificationCompat.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    /** Opis stanu do powiadomienia / ekranu blokady. */
    data class Texts(val title: String, val text: String, val countdownEndWall: Long?)

    fun texts(live: LiveSession): Texts {
        val st = live.status
        val entries = live.plan.entries
        val current = st.taskIndex?.let { entries.getOrNull(it) }
        val next = st.nextTaskIndex?.let { entries.getOrNull(it) }
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val title = when {
            st.phase == Phase.FINISHED -> "Rutyna zakończona"
            st.paused -> "Wstrzymano: ${current?.title ?: live.plan.routineName}"
            st.phase == Phase.ACTIVE -> current?.title ?: ""
            st.phase == Phase.AWAITING_CONFIRMATION -> "Czas minął: ${current?.title} – potwierdź"
            st.phase == Phase.INTRO -> "Za chwilę: ${current?.title}"
            st.phase == Phase.GAP -> "Przerwa · dalej: ${current?.title}"
            else -> live.plan.routineName
        }
        val text = when {
            st.phase == Phase.FINISHED -> live.plan.routineName
            next != null && st.phase == Phase.ACTIVE ->
                "Następnie: ${next.title} o ${fmt.format(Date(live.wallTimeOf(st.effectiveStartsMs[next.index])))}"
            st.phase == Phase.ACTIVE -> "Ostatnie zadanie · koniec o ${fmt.format(Date(live.etaWall))}"
            else -> "${live.plan.routineName} · koniec ok. ${fmt.format(Date(live.etaWall))}"
        }
        val countdownEnd = when {
            st.paused || st.phase == Phase.FINISHED || st.phase == Phase.AWAITING_CONFIRMATION -> null
            else -> live.nowWall + st.remainingInPhaseMs
        }
        return Texts(title, text, countdownEnd)
    }

    fun session(context: Context, live: LiveSession): Notification {
        val t = texts(live)
        val st = live.status
        val b = NotificationCompat.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(t.title)
            .setContentText(t.text)
            .setSubText(live.plan.routineName)
            .setContentIntent(openAppIntent(context))
            .setOngoing(st.phase != Phase.FINISHED)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (t.countdownEndWall != null) {
            // System sam odlicza czas w powiadomieniu – bez aktualizacji co sekundę.
            b.setUsesChronometer(true).setWhen(t.countdownEndWall).setShowWhen(true)
            b.setChronometerCountDown(true)
        } else {
            b.setShowWhen(false)
        }

        if (st.phase != Phase.FINISHED) {
            if (st.paused) {
                b.addAction(R.drawable.ic_play, "Wznów", serviceAction(context, RoutineSessionService.ACTION_RESUME, 11))
            } else {
                b.addAction(R.drawable.ic_pause, "Pauza", serviceAction(context, RoutineSessionService.ACTION_PAUSE, 12))
            }
            if (live.plan.mode == ScheduleMode.FLEXIBLE && !st.paused &&
                (st.phase == Phase.ACTIVE || st.phase == Phase.AWAITING_CONFIRMATION)
            ) {
                b.addAction(R.drawable.ic_check, "Zrobione", serviceAction(context, RoutineSessionService.ACTION_CONFIRM, 13))
            }
            b.addAction(R.drawable.ic_stop, "Zakończ", serviceAction(context, RoutineSessionService.ACTION_STOP, 14))
        }
        return b.build()
    }

    /** Powiadomienie bez treści zadań (gdy użytkownik wyłączył powiadomienia o zadaniach). */
    fun sessionMinimal(context: Context, live: LiveSession): Notification {
        val st = live.status
        val b = NotificationCompat.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(live.plan.routineName)
            .setContentText(if (st.paused) "Rutyna wstrzymana" else "Rutyna w toku")
            .setContentIntent(openAppIntent(context))
            .setOngoing(st.phase != Phase.FINISHED)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (st.phase != Phase.FINISHED) {
            if (st.paused) {
                b.addAction(R.drawable.ic_play, "Wznów", serviceAction(context, RoutineSessionService.ACTION_RESUME, 11))
            } else {
                b.addAction(R.drawable.ic_pause, "Pauza", serviceAction(context, RoutineSessionService.ACTION_PAUSE, 12))
            }
            b.addAction(R.drawable.ic_stop, "Zakończ", serviceAction(context, RoutineSessionService.ACTION_STOP, 14))
        }
        return b.build()
    }

    /** Pełnoekranowe powiadomienie przy starcie z budzika (włącza ekran nad blokadą). */
    fun alarmStarted(context: Context, routineName: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Czas na rutynę: $routineName")
            .setContentText("Dotknij, aby zobaczyć bieżące zadanie.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(openAppIntent(context), true)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setTimeoutAfter(5 * 60_000L)
            .build()

    fun info(context: Context, title: String, text: String, openSession: Boolean = false) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(context, openSession))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(ID_INFO, n)
    }

    fun canPost(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    @Suppress("unused")
    fun formatRemaining(ms: Long) = TimeText.countdown(ms)
}
