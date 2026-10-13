package pl.rytm.app.data

import pl.rytm.app.domain.model.AnnouncementConfig
import pl.rytm.app.domain.model.Routine
import pl.rytm.app.domain.model.RoutineTask
import pl.rytm.app.domain.model.ScheduleMode

/**
 * Przykładowe rutyny tworzone przy pierwszym uruchomieniu – można je dowolnie edytować i usuwać.
 *
 * Czasy są dobrane realistycznie: tyle, ile czynność zwykle zajmuje, bez zawyżania
 * (mycie zębów 2–3 min, prysznic ~8 min, śniadanie ~15 min, ubieranie 3–5 min).
 * Zadania „opcjonalne” to te, które można skrócić lub pominąć, gdy brakuje czasu do wyjścia.
 */
object SampleRoutines {

    /** Wersja zestawu przykładów – zwiększana, gdy przykłady się zmieniają. */
    const val VERSION = 2

    /** Nazwy przykładów z wersji 1.0.0 (aktualizowane, jeśli użytkownik ich nie zmieniał). */
    val V1_NAMES = setOf("Poranne wstawanie", "Przygotowanie do wyjścia", "Sprzątanie pokoju", "Wieczorne przygotowanie do snu")

    fun all(): List<Routine> = listOf(
        morning(),
        schoolOrWorkMorning(),
        quickMorning(),
        morningWorkout(),
        study(),
        cleaning(),
        chores(),
        leavingChecklist(),
        evening(),
    )

    private fun t(
        title: String,
        spoken: String,
        sec: Int,
        gap: Int = 0,
        mandatory: Boolean = true,
        min: Int? = null,
        notes: String = "",
    ) = RoutineTask(
        title = title,
        spokenText = spoken,
        durationSec = sec,
        gapBeforeSec = gap,
        mandatory = mandatory,
        minDurationSec = min,
        notes = notes,
    )

    /**
     * Dokładnie przykład z założeń (00:10 wstań, 00:25 biurko, 00:40 ubrania, 01:40 łazienka,
     * 02:10–05:10 higiena), dalej ubieranie i ścielenie łóżka. Ok. 10 minut.
     */
    fun morning() = Routine(
        name = "Poranne wstawanie",
        goal = "Wstać i ogarnąć się w 10 minut – bez leżenia i bez zastanawiania się, co dalej.",
        mode = ScheduleMode.STRICT,
        tasks = listOf(
            t("Wstań z łóżka", "Wstań z łóżka.", 15, gap = 10),
            t("Podejdź do biurka", "Podejdź do biurka.", 15),
            t("Przygotuj ubrania", "Przygotuj ubrania na dziś.", 60),
            t("Idź do łazienki", "Idź do łazienki.", 30),
            t("Poranna higiena", "Rozpocznij poranną higienę.", 180, notes = "Toaleta, umyj twarz, zęby (2 minuty), uczesz się."),
            t("Ubierz się", "Ubierz się.", 180),
            t("Pościel łóżko", "Pościel łóżko i odsłoń okno.", 60, mandatory = false, min = 30),
            t("Szklanka wody", "Wypij szklankę wody.", 30),
        ),
        announcements = AnnouncementConfig(includeDuration = false, endMessage = "Gotowe. Dobrego dnia!"),
    )

    /** Pełny poranek: pobudka 6:30, wyjście 7:25 (55 min, plan ok. 45 min + zapas). */
    fun schoolOrWorkMorning() = Routine(
        name = "Poranek do szkoły lub pracy",
        goal = "Od pobudki do wyjścia z domu na czas, ze śniadaniem i bez pośpiechu.",
        mode = ScheduleMode.STRICT,
        wakeTimeMinutes = 6 * 60 + 30,
        leaveTimeMinutes = 7 * 60 + 25,
        tasks = listOf(
            t("Wstań", "Dzień dobry. Wstań z łóżka.", 20, gap = 10),
            t("Okno", "Odsłoń zasłony i uchyl okno.", 30),
            t("Pościel łóżko", "Pościel łóżko.", 90, mandatory = false, min = 45),
            t("Woda", "Wypij szklankę wody.", 30),
            t("Toaleta i twarz", "Idź do łazienki. Toaleta i umycie twarzy.", 180),
            t("Prysznic", "Czas na prysznic.", 480, mandatory = false, min = 240),
            t("Ubierz się", "Ubierz się.", 300),
            t("Śniadanie", "Śniadanie. Jedz spokojnie.", 900, mandatory = false, min = 600),
            t("Umyj zęby", "Umyj zęby.", 150),
            t("Fryzura", "Uczesz się i spójrz w lustro.", 120, mandatory = false, min = 30),
            t("Spakuj się", "Spakuj plecak lub torbę.", 180),
            t("Sprawdź rzeczy", "Sprawdź: klucze, telefon, portfel, słuchawki.", 60),
            t("Buty i kurtka", "Załóż buty i kurtkę.", 120),
            t("Wyjście", "Wychodzisz. Powodzenia!", 30),
        ),
        announcements = AnnouncementConfig(includeDuration = true, warningBeforeEndSec = 60, endMessage = "Czas wyjść z domu."),
    )

    /** Gdy zaspałeś: ok. 19 minut od łóżka do drzwi. */
    fun quickMorning() = Routine(
        name = "Szybki poranek",
        goal = "Zaspane? Najważniejsze rzeczy w niecałe 20 minut.",
        mode = ScheduleMode.STRICT,
        targetDurationSec = 20 * 60,
        tasks = listOf(
            t("Wstań", "Wstajemy. Szybki poranek.", 15, gap = 5),
            t("Woda", "Wypij kilka łyków wody.", 20),
            t("Łazienka", "Łazienka: toaleta, twarz i zęby.", 240),
            t("Ubierz się", "Ubierz się.", 240),
            t("Szybkie śniadanie", "Szybkie śniadanie albo coś na wynos.", 300, mandatory = false, min = 120),
            t("Spakuj się", "Spakuj najważniejsze rzeczy.", 120),
            t("Sprawdź rzeczy", "Klucze, telefon, portfel.", 30),
            t("Buty i kurtka", "Buty i kurtka.", 90),
            t("Wyjście", "Wychodzisz. Dasz radę!", 20),
        ),
        announcements = AnnouncementConfig(includeDuration = true, warningBeforeEndSec = 30, endMessage = "Czas wyjść."),
    )

    /** Krótka rozgrzewka: ćwiczenia po 40–60 s z 10-sekundowymi przerwami. Ok. 10 minut. */
    fun morningWorkout() = Routine(
        name = "Rozruch – poranne ćwiczenia",
        goal = "Obudzić ciało w 10 minut. Ćwicz we własnym tempie, ale nie przerywaj.",
        mode = ScheduleMode.STRICT,
        tasks = listOf(
            t("Krążenia ramion", "Krążenia ramion, do przodu i do tyłu.", 40, gap = 10),
            t("Krążenia bioder", "Krążenia bioder.", 40, gap = 10),
            t("Przysiady", "Przysiady. Plecy proste.", 45, gap = 10),
            t("Pajacyki", "Pajacyki.", 40, gap = 15, notes = "Wersja cicha: wykrok w bok z unoszeniem rąk."),
            t("Deska", "Deska na przedramionach.", 40, gap = 15),
            t("Wykroki", "Wykroki, na zmianę nogi.", 45, gap = 10),
            t("Rozciąganie nóg", "Rozciąganie tyłu ud. Spokojnie, bez szarpania.", 60, gap = 10),
            t("Koci grzbiet", "Koci grzbiet na czworakach.", 45, gap = 10),
            t("Skłony w bok", "Skłony w bok.", 40, gap = 10),
            t("Oddech", "Trzy głębokie wdechy nosem, powolny wydech ustami.", 45, gap = 10),
        ),
        musicVolume = 85,
        announcements = AnnouncementConfig(includeDuration = true, warningBeforeEndSec = 10, endMessage = "Koniec ćwiczeń. Brawo!"),
    )

    /** Dwa bloki skupienia po 25 minut z przerwą (technika pomodoro). Ok. 62 minuty. */
    fun study() = Routine(
        name = "Nauka – dwa bloki skupienia",
        goal = "Godzina skupionej nauki lub pracy z jedną przerwą.",
        mode = ScheduleMode.STRICT,
        tasks = listOf(
            t("Przygotuj biurko", "Przygotuj biurko i wycisz telefon.", 120, gap = 5),
            t("Plan", "Zapisz, co chcesz dziś zrobić.", 120),
            t("Blok 1", "Pierwszy blok skupienia. Tylko jedno zadanie.", 1500, notes = "Gdy coś przyjdzie do głowy – zapisz i wróć do pracy."),
            t("Przerwa", "Przerwa. Wstań, rozprostuj się, napij się wody.", 300),
            t("Blok 2", "Drugi blok skupienia.", 1500),
            t("Podsumowanie", "Podsumuj: co udało się zrobić i od czego zaczniesz następnym razem.", 180),
        ),
        musicVolume = 60,
        announcements = AnnouncementConfig(includeDuration = true, warningBeforeEndSec = 60, endMessage = "Koniec nauki. Dobra robota."),
    )

    /** Tryb elastyczny: potwierdzasz „Zrobione”, gdy skończysz wcześniej. Ok. 20 minut. */
    fun cleaning() = Routine(
        name = "Sprzątanie pokoju",
        goal = "Posprzątany pokój w około 20 minut. Naciśnij „Zrobione”, gdy skończysz wcześniej.",
        mode = ScheduleMode.FLEXIBLE,
        flexibleTimeoutSec = 60,
        tasks = listOf(
            t("Okno", "Otwórz okno.", 30, gap = 5),
            t("Ubrania", "Zbierz ubrania: brudne do kosza, czyste do szafy.", 180),
            t("Naczynia", "Odnieś naczynia do kuchni.", 90),
            t("Śmieci", "Wyrzuć śmieci i niepotrzebne papiery.", 120),
            t("Biurko", "Uporządkuj biurko.", 240),
            t("Łóżko", "Pościel łóżko.", 90),
            t("Kurze", "Przetrzyj kurze.", 180, mandatory = false, min = 90),
            t("Odkurzanie", "Odkurz podłogę.", 300, mandatory = false, min = 180),
        ),
        announcements = AnnouncementConfig(warningBeforeEndSec = 30, endMessage = "Pokój posprzątany. Brawo!"),
    )

    fun chores() = Routine(
        name = "Obowiązki domowe",
        goal = "Najważniejsze obowiązki w około pół godziny.",
        mode = ScheduleMode.FLEXIBLE,
        flexibleTimeoutSec = 60,
        tasks = listOf(
            t("Pranie", "Nastaw pranie.", 180, gap = 5),
            t("Naczynia", "Pozmywaj albo załaduj zmywarkę.", 600),
            t("Blaty", "Przetrzyj blaty i stół.", 180),
            t("Śmieci", "Wynieś śmieci.", 180),
            t("Pranie z suszarki", "Poskładaj suche pranie.", 420, mandatory = false, min = 180),
            t("Łazienka", "Szybki porządek w łazience: umywalka i lustro.", 300, mandatory = false, min = 120),
        ),
        announcements = AnnouncementConfig(warningBeforeEndSec = 60, endMessage = "Obowiązki zrobione."),
    )

    /** Ostatnie 5 minut przed wyjściem – nic nie zostaje w domu. */
    fun leavingChecklist() = Routine(
        name = "Wyjście z domu – lista",
        goal = "Pięć minut przed wyjściem: wszystko zabrane, wszystko wyłączone.",
        mode = ScheduleMode.STRICT,
        tasks = listOf(
            t("Telefon", "Telefon i ładowarka.", 20, gap = 3),
            t("Klucze i portfel", "Klucze i portfel.", 20),
            t("Słuchawki", "Słuchawki.", 15),
            t("Pogoda", "Sprawdź pogodę – parasol?", 30, mandatory = false),
            t("Woda i przekąska", "Woda i coś do jedzenia.", 40, mandatory = false),
            t("Wyłącz", "Wyłącz światła i sprzęty. Zamknij okna.", 60),
            t("Buty i kurtka", "Buty i kurtka.", 90),
            t("Drzwi", "Wyjdź i zamknij drzwi na klucz.", 20),
        ),
        announcements = AnnouncementConfig(endMessage = "Wszystko masz. Do zobaczenia!"),
    )

    /** Spokojny wieczór: ok. 27 minut, ciszej i bez pośpiechu. */
    fun evening() = Routine(
        name = "Wieczorne przygotowanie do snu",
        goal = "Spokojnie zakończyć dzień i przygotować się na jutro.",
        mode = ScheduleMode.STRICT,
        tasks = listOf(
            t("Odłóż telefon", "Odłóż telefon na biurko. Od teraz bez ekranu.", 20, gap = 5),
            t("Ubrania na jutro", "Przygotuj ubrania na jutro.", 180),
            t("Plecak na jutro", "Spakuj plecak lub torbę na jutro.", 240),
            t("Porządek", "Szybki porządek w pokoju.", 180, mandatory = false, min = 90),
            t("Prysznic", "Prysznic albo mycie.", 480, mandatory = false, min = 240),
            t("Zęby", "Umyj zęby. Pamiętaj o nitce.", 180),
            t("Piżama", "Przebierz się w piżamę.", 120),
            t("Wietrzenie", "Wywietrz pokój.", 120, mandatory = false),
            t("Światło", "Zgaś światło i połóż się.", 60),
        ),
        musicVolume = 65,
        announcements = AnnouncementConfig(volume = 75, endMessage = "Dobranoc. Śpij dobrze."),
    )
}
