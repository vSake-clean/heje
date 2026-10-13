package pl.rytm.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pl.rytm.app.alarm.RoutineAlarmScheduler
import pl.rytm.app.data.RoutineRepository
import pl.rytm.app.data.db.RytmDatabase
import pl.rytm.app.data.session.SessionStore
import pl.rytm.app.data.settings.SettingsRepository
import pl.rytm.app.service.Notifications
import pl.rytm.app.service.SessionHub

class RytmApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        container.appScope.launch {
            container.settings.migrateIfNeeded()
            container.routines.seedOrUpgradeSamples(container.appMeta)
            container.alarms.rescheduleAll()
        }
    }
}

/** Prosty kontener zależności (bez frameworka DI). */
class AppContainer(val app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: RytmDatabase by lazy { RytmDatabase.create(app) }
    val routines: RoutineRepository by lazy { RoutineRepository(database.routineDao()) }
    val settings: SettingsRepository by lazy { SettingsRepository(app) }
    val sessionStore: SessionStore by lazy { SessionStore(app) }
    val alarms: RoutineAlarmScheduler by lazy { RoutineAlarmScheduler(app, routines) }
    val appMeta: pl.rytm.app.data.AppMeta by lazy { pl.rytm.app.data.AppMeta(app) }
    val hub: SessionHub get() = SessionHub
}

val android.content.Context.container: AppContainer
    get() = (applicationContext as RytmApp).container
