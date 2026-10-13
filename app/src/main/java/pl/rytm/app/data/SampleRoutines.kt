package pl.rytm.app.data

import pl.rytm.app.domain.model.AnnouncementConfig
import pl.rytm.app.domain.model.Routine
import pl.rytm.app.domain.model.RoutineTask
import pl.rytm.app.domain.model.ScheduleMode

/** Przykładowe rutyny tworzone przy pierwszym uruchomieniu – można je dowolnie edytować i usuwać. */
object SampleRoutines {

    fun all(): List<Routine> = listOf(morning(), leaving(), cleaning(), evening())

    /** Dokładnie przykład z założeń: 00:10 wstań, 00:25 biurko, 00:40 ubrania, 01:40 łazienka, 02:10 higiena. */
    fun morning() = Routine(
        name = "Poranne wstawanie",
        goal = "Wstać i przygotować się bez zastanawiania się, co dalej.",
        mode = ScheduleMode.STRICT,
        tasks = listOf(
            RoutineTask(title = "Wstań z łóżka", spokenText = "Wstań z łóżka.", durationSec = 15, gapBeforeSec = 10),
            RoutineTask(title = "Podejdź do biurka", spokenText = "Podejdź do biurka.", durationSec = 15),
            RoutineTask(title = "Przygotuj ubrania", spokenText = "Przygotuj ubrania.", durationSec = 60),
            RoutineTask(title = "Idź do łazienki", spokenText = "Idź do łazienki.", durationSec = 30),
            RoutineTask(
                title = "Poranna higiena",
                spokenText = "Rozpocznij poranną higienę.",
                durationSec = 180,
                notes = "Umyj twarz, zęby, uczesz się.",
            ),
            RoutineTask(title = "Pościel łóżko", spokenText = "Pościel łóżko.", durationSec = 45, mandatory = false),
            RoutineTask(title = "Ubierz się", spokenText = "Ubierz się.", durationSec = 120),
        ),
        announcements = AnnouncementConfig(endMessage = "Rutyna zakończona. Dobrego dnia!"),
    )

    /** Przykład planowania między godziną pobudki a wyjścia. */
    fun leaving() = Routine(
        name = "Przygotowanie do wyjścia",
        goal = "Zdążyć na czas – od pobudki do wyjścia z domu.",
        mode = ScheduleMode.STRICT,
        wakeTimeMinutes = 6 * 60 + 45,
        leaveTimeMinutes = 7 * 60 + 30,
        tasks = listOf(
            RoutineTask(title = "Wstań", spokenText = "Dzień dobry. Wstań z łóżka.", durationSec = 20, gapBeforeSec = 10),
            RoutineTask(title = "Pościel łóżko", spokenText = "Pościel łóżko.", durationSec = 60, mandatory = false),
            RoutineTask(title = "Łazienka", spokenText = "Idź do łazienki. Umyj twarz i zęby.", durationSec = 300),
            RoutineTask(title = "Ubierz się", spokenText = "Ubierz się.", durationSec = 240),
            RoutineTask(title = "Śniadanie", spokenText = "Czas na śniadanie.", durationSec = 900),
            RoutineTask(
                title = "Spakuj plecak",
                spokenText = "Spakuj plecak.",
                durationSec = 180,
                mandatory = false,
                minDurationSec = 90,
            ),
            RoutineTask(
                title = "Sprawdź rzeczy",
                spokenText = "Sprawdź klucze, telefon i portfel.",
                durationSec = 60,
            ),
            RoutineTask(title = "Buty i kurtka", spokenText = "Załóż buty i kurtkę.", durationSec = 120),
            RoutineTask(title = "Wyjście", spokenText = "Wychodzimy. Powodzenia!", durationSec = 30),
        ),
        announcements = AnnouncementConfig(includeDuration = true, endMessage = "Czas wyjść z domu."),
    )

    fun cleaning() = Routine(
        name = "Sprzątanie pokoju",
        goal = "Szybko ogarnąć pokój – możesz potwierdzać zadania wcześniej.",
        mode = ScheduleMode.FLEXIBLE,
        flexibleTimeoutSec = 30,
        tasks = listOf(
            RoutineTask(title = "Ubrania", spokenText = "Zbierz ubrania z podłogi i krzesła.", durationSec = 120, gapBeforeSec = 5),
            RoutineTask(title = "Naczynia", spokenText = "Odnieś naczynia do kuchni.", durationSec = 60),
            RoutineTask(title = "Śmieci", spokenText = "Wyrzuć śmieci.", durationSec = 90),
            RoutineTask(title = "Biurko", spokenText = "Uporządkuj i przetrzyj biurko.", durationSec = 180),
            RoutineTask(title = "Odkurzanie", spokenText = "Odkurz podłogę.", durationSec = 300, mandatory = false),
        ),
        announcements = AnnouncementConfig(warningBeforeEndSec = 15, endMessage = "Pokój posprzątany. Brawo!"),
    )

    fun evening() = Routine(
        name = "Wieczorne przygotowanie do snu",
        goal = "Spokojnie zakończyć dzień i przygotować się na jutro.",
        mode = ScheduleMode.STRICT,
        tasks = listOf(
            RoutineTask(title = "Odłóż telefon", spokenText = "Odłóż telefon na biurko.", durationSec = 15, gapBeforeSec = 5),
            RoutineTask(title = "Ubrania na jutro", spokenText = "Przygotuj ubrania na jutro.", durationSec = 180),
            RoutineTask(title = "Plecak", spokenText = "Spakuj plecak na jutro.", durationSec = 180, mandatory = false),
            RoutineTask(title = "Mycie zębów", spokenText = "Umyj zęby.", durationSec = 180),
            RoutineTask(title = "Piżama", spokenText = "Przebierz się w piżamę.", durationSec = 120),
            RoutineTask(title = "Światło", spokenText = "Zgaś światło i połóż się.", durationSec = 30),
        ),
        musicVolume = 70,
        announcements = AnnouncementConfig(volume = 80, endMessage = "Dobranoc."),
    )
}
