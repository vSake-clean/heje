package pl.rytm.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat

/** Stan uprawnień i ustawień systemowych potrzebnych do niezawodnej pracy w tle. */
data class SystemStatus(
    val notificationsGranted: Boolean,
    val batteryUnrestricted: Boolean,
    val exactAlarmsAllowed: Boolean,
    val fullScreenAllowed: Boolean,
)

object SystemChecks {

    fun status(context: Context): SystemStatus = SystemStatus(
        notificationsGranted = notificationsGranted(context),
        batteryUnrestricted = isIgnoringBatteryOptimizations(context),
        exactAlarmsAllowed = exactAlarmsAllowed(context),
        fullScreenAllowed = fullScreenAllowed(context),
    )

    fun notificationsGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun exactAlarmsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun fullScreenAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    /** Systemowe okno: „Zezwolić aplikacji na działanie w tle bez ograniczeń?” */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        if (!tryStart(context, direct)) tryStart(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            tryStart(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        }
    }

    fun openFullScreenSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= 34) {
            tryStart(context, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
        }
    }

    fun openNotificationSettings(context: Context) {
        tryStart(
            context,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }

    fun openTtsSettings(context: Context) {
        if (!tryStart(context, Intent("com.android.settings.TTS_SETTINGS"))) {
            tryStart(context, Intent(Settings.ACTION_SETTINGS))
        }
    }

    /** Instalacja danych głosu (np. polskiego) w silniku mowy. */
    fun installTtsData(context: Context) {
        if (!tryStart(context, Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))) openTtsSettings(context)
    }

    fun tryStart(context: Context, intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
