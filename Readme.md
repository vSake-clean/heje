# Rytm – rutyny z muzyką z SoundCloud i poleceniami głosowymi

Rytm prowadzi Cię przez codzienne rutyny w czasie rzeczywistym. Gra muzyka z SoundCloud, a w zaplanowanych momentach polski głos mówi, co robisz **teraz**: „Wstań z łóżka”, „Pościel łóżko”, „Idź do łazienki”.

W trybie ścisłym aplikacja nie czeka na potwierdzenia. Jeśli ścielenie łóżka ma trwać 15 sekund, po 15 sekundach od razu usłyszysz kolejne polecenie i możesz odejść od łóżka.

- Bez kont, bez kluczy API, bez serwerów. Wszystko zapisuje się lokalnie na telefonie.
- Gotowy plik do instalacji: **[`release/Rytm-1.1.0.apk`](release/Rytm-1.1.0.apk)** (Android 8.0 lub nowszy). Instaluje się jako aktualizacja wersji 1.0.0, a Twoje rutyny zostają.

## Co nowego w 1.1.0

- **Muzyka już się nie wyłącza, tylko przycisza.** Wersja 1.0.0 wstrzymywała muzykę, gdy silnik mowy telefonu albo przeglądarka przejmowały dźwięk. Teraz:
  - Rytm nie używa własnego fokusu audio.
  - Komunikaty są odtwarzane z nagrań przygotowanych przed sesją, a silnik mowy ich nie odtwarza.
  - Specjalny „strażnik” wznawia muzykę, gdyby coś ją zatrzymało.
- **Słuchawki i wygaszony ekran.** Odtwarzacz SoundCloud zostaje w oknie aplikacji także poza ekranem sesji, więc telefon nie usypia go przy wygaszonym ekranie. „Wstecz” podczas rutyny chowa aplikację zamiast ją zamykać. Przy podłączonych słuchawkach głos zawsze idzie do słuchawek, a nie do głośnika.
- **Prywatne playlisty.** Linki prywatne (z kodem `s-…`), kod „Osadź” i linki API działają. Rytm zapamiętuje dokładny adres osadzenia podany przez SoundCloud. Twoje prywatne listy otwarte w przeglądarce wyboru (po zalogowaniu) są rozpoznawane automatycznie.
- **9 przemyślanych rutyn z realistycznymi czasami** (tabela niżej). Przykłady z 1.0.0, których nie zmieniałeś, zostały zaktualizowane. Edytowane i własne rutyny zostają bez zmian.
- **Mądrzejsze dopasowanie do godziny wyjścia.** Usuwa jak najmniej zadań. Przy 30 minutach do wyjścia wypada prysznic, a śniadanie zostaje skrócone do około 10 minut.
- **Poprawione błędy:**
  - pozycja utworu nie była zapamiętywana (muzyka po przeładowaniu wracała na początek);
  - blokada uśpienia mogła wygasnąć po bardzo długiej pauzie;
  - interfejs niepotrzebnie się przerysowywał.

---

## 1. Instalacja (dla użytkownika)

1. Pobierz na telefon plik `release/Rytm-1.1.0.apk`. Na GitHubie otwórz plik i naciśnij **Download raw file**.
2. Otwórz pobrany plik. Android zapyta o zgodę na instalację z tego źródła (przeglądarki lub menedżera plików). Zezwól i wróć.
3. Naciśnij **Zainstaluj**. Jeśli pojawi się ostrzeżenie Play Protect, wybierz **Zainstaluj mimo to**. Aplikacja nie pochodzi ze Sklepu Play, dlatego system ostrzega.
4. Uruchom **Rytm**.

Nic więcej nie trzeba konfigurować: żadnych kluczy, logowań ani plików.

### Pierwsze uruchomienie (ok. 1 minuty)

Ekran powitalny prowadzi przez 4 kroki. Przy każdej zgodzie wyjaśnia, do czego jest potrzebna.

1. **Powiadomienia**: bieżące zadanie i odliczanie na ekranie blokady oraz przyciski Pauza i Zakończ.
2. **Praca w tle**: zgoda na działanie bez ograniczeń baterii, żeby polecenia padały punktualnie przy wygaszonym ekranie.
3. **Polski głos**: test syntezy mowy. Jeśli telefon nie ma polskiego głosu, przycisk „Zainstaluj polski głos” otwiera instalację, np. „Usługi mowy Google”. Głos działa potem offline.
4. **Muzyka**: krótkie wyjaśnienie, skąd brać muzykę. Logowanie do SoundCloud jest opcjonalne.

Na start dostajesz 9 przykładowych rutyn. Czasy są realistyczne (tyle, ile czynność zwykle trwa), a zadania, które można skrócić lub pominąć, są oznaczone jako opcjonalne. Wszystko możesz edytować, kopiować i usuwać.

| Rutyna | Czas | Tryb | Co zawiera |
|---|---|---|---|
| Poranne wstawanie | ok. 10 min | ścisły | dokładny przykład z założeń (00:10 wstań, 00:25 biurko, 00:40 ubrania, 01:40 łazienka, 02:10–05:10 higiena), dalej ubieranie (3 min), łóżko, woda |
| Poranek do szkoły lub pracy | 45 min (pobudka 6:30, wyjście 7:25) | ścisły | łazienka 3 min, prysznic 8 min, ubieranie 5 min, śniadanie 15 min, zęby 2,5 min, pakowanie, „klucze, telefon, portfel”; mówi „Masz…” i „Została jedna minuta” |
| Szybki poranek | 18 min | ścisły | gdy zaspałeś: łazienka 4 min, ubieranie 4 min, szybkie śniadanie, wyjście |
| Rozruch – poranne ćwiczenia | ok. 9 min | ścisły | 10 ćwiczeń po 40–60 s z przerwami 10–15 s, „Zostało dziesięć sekund” |
| Nauka – dwa bloki skupienia | ok. 62 min | ścisły | 2 × 25 min skupienia (pomodoro), 5 min przerwy, plan i podsumowanie |
| Sprzątanie pokoju | ok. 20 min | elastyczny | ubrania, naczynia, śmieci, biurko, łóżko, kurze i odkurzanie (opcjonalne) |
| Obowiązki domowe | ok. 31 min | elastyczny | pranie, naczynia, blaty, śmieci, składanie prania, łazienka |
| Wyjście z domu – lista | 5 min | ścisły | telefon, klucze, słuchawki, pogoda, światła, buty, drzwi |
| Wieczorne przygotowanie do snu | ok. 26 min | ścisły | bez ekranu, rzeczy na jutro, prysznic, zęby 3 min, piżama, cicho (głos 75%, muzyka 65%) |

### Szybki start

1. Na ekranie głównym naciśnij **Rozpocznij rutynę**. Możesz też nacisnąć **Start** przy wybranej rutynie.
2. Gdy chcesz własną muzykę: **Edytuj → Muzyka z SoundCloud → Wybierz z SoundCloud**, wyszukaj utwór lub playlistę i naciśnij **Wybierz**.
3. Gdy chcesz, żeby rutyna startowała sama rano: w edytorze ustaw **Pobudkę** i **Wyjście**, włącz **Uruchamiaj o godzinie pobudki** i wybierz dni.

---

## 2. Jak działa harmonogram

### Godzina pobudki i wyjścia
Przykład: „Poranek do szkoły lub pracy” (pobudka 6:30, wyjście 7:25). W edytorze podajesz, o której wstajesz i o której musisz wyjść. Aplikacja pokazuje dostępny czas, długość planu i zapas.

Jeśli zadania się nie mieszczą (np. masz 30 minut, a plan trwa 45), pojawia się konflikt i przycisk **Zaproponuj dopasowanie**. Propozycja obejmuje kolejno:

1. skrócenie lub usunięcie przerw,
2. proporcjonalne skrócenie zadań **opcjonalnych** (do ich minimalnego czasu),
3. usunięcie jak najmniejszej liczby zadań opcjonalnych (pozostałe są tylko skracane). Przy 30 minutach zamiast 45 wypada prysznic, a śniadanie skraca się do ok. 10 minut.

**Zadania obowiązkowe nigdy nie są zmieniane automatycznie.** Jeśli same się nie mieszczą, aplikacja to mówi i prosi o decyzję.

Propozycję widzisz przed zastosowaniem i możesz dalej edytować plan ręcznie. To samo sprawdzenie działa przy starcie: gdy uruchamiasz rutynę za późno, Rytm zapyta, czy dopasować plan do godziny wyjścia.

### Tryb ścisły
- Każde zadanie ma z góry ustalony początek i koniec. Wszystkie momenty są wyliczane **przed** startem sesji.
- Brak czekania na potwierdzenie, brak dodatkowych przerw. Zadanie od 00:10 trwające 15 s kończy się o 00:25 i wtedy pada następne polecenie.
- Jeśli nie zdążysz, aplikacja przechodzi dalej zgodnie z planem. Harmonogram nie przesuwa się sam.

### Tryb elastyczny
- Przycisk **Zrobione** (na ekranie, w powiadomieniu albo przycisk „następny” na słuchawkach) od razu zaczyna kolejne zadanie.
- Gdy czas zadania minie, aplikacja czeka na potwierdzenie **najwyżej** tyle, ile ustawisz (np. 30 s), a potem sama przechodzi dalej. Brak reakcji nie może zawiesić rutyny.

### Precyzja i niezawodność
- Czas sesji liczy zegar monotoniczny (`SystemClock.elapsedRealtime`, uwzględnia też uśpienie telefonu). Stan jest **wyliczany z czasu startu i planu**, a nie z liczby „tyknięć” timera. Po wygaszeniu ekranu albo powrocie do aplikacji pokazuje się zawsze właściwe zadanie.
- Komunikaty mają własne, dokładne wybudzenia. Aplikacja mierzy opóźnienie syntezy mowy i uruchamia ją odpowiednio wcześniej (kompensację można wyłączyć).
- Komunikaty **nigdy się nie nakładają**. Nowe polecenie zadania przerywa poprzednie. Ostrzeżenia są pomijane, gdy coś jest właśnie mówione.
- Długie komunikaty są wykrywane **już w edytorze** („Komunikat trwa ok. 3,2 s, a do kolejnego jest tylko 2 s”). Przycisk **Zmierz głosem** mierzy rzeczywistą długość wybranym głosem: synteza do pliku, bez odtwarzania.
- Po przerwie (zamknięcie procesu, połączenie) aplikacja mówi tylko o zadaniu, które trwa teraz („Teraz: Idź do łazienki”). Nie odtwarza zaległych poleceń.

### Po przerwaniu sesji (wybierasz osobno dla każdej rutyny)
| Ustawienie | Co się dzieje po połączeniu telefonicznym albo zamknięciu aplikacji przez system |
|---|---|
| Kontynuuj według planu | godziny zadań się nie zmieniają, po przerwie słyszysz bieżące zadanie |
| Wstrzymaj rutynę | rutyna czeka, aż naciśniesz „Wznów” |
| Wznów od bieżącego zadania | przerwane zadanie zaczyna się od nowa, kolejne przesuwają się o czas przerwy |

---

## 3. SoundCloud: co działa i jakie są ograniczenia

### Co sprawdziłem (październik 2026)
- **Oficjalne API SoundCloud** (wyszukiwanie, strumienie, logowanie OAuth 2.1) wymaga zarejestrowanej aplikacji z kluczem `client_id`/`client_secret`. Klucze są dostępne tylko dla kont **Artist Pro**, a sekret ma działać po stronie serwera. Nie da się więc uczciwie dostarczyć aplikacji „gotowej po instalacji” opartej na tym API. Nie mogę wygenerować kluczy za Ciebie i nie umieszczam w projekcie fikcyjnych kluczy.
- **Oficjalny odtwarzacz SoundCloud (HTML5 Widget API)** działa **bez żadnego klucza**. Służy do osadzania odtwarzacza na stronach i ma oficjalne metody `play`, `pause`, `setVolume(0–100)`, `getCurrentSound`, `seekTo`, `skip` oraz zdarzenia.
- **oEmbed SoundCloud** zwraca tytuł, autora i okładkę utworu bez klucza.

### Wybrane rozwiązanie (bez konfiguracji)
| Funkcja | Jak działa w Rytmie |
|---|---|
| Wyszukiwanie | oficjalna strona SoundCloud w przeglądarce wbudowanej (pole „Szukaj”). Aplikacja tylko odczytuje adres otwartego utworu lub playlisty |
| Twoje playlisty i polubienia | opcjonalne logowanie **na stronie SoundCloud** w tej przeglądarce. Rytm nie widzi ani nie zapisuje hasła |
| Wybór z aplikacji SoundCloud | w SoundCloud: **Udostępnij → Rytm** (albo „Kopiuj link” i wklej w Rytmie) |
| Utwory i playlisty | tak. Playlista gra po kolei, a muzyka może się zapętlać, gdy rutyna jest dłuższa |
| Odtwarzanie podczas rutyny | oficjalny odtwarzacz SoundCloud widoczny na ekranie sesji (z logo i linkiem do SoundCloud) |
| Odtwarzanie w tle / przy wygaszonym ekranie | tak, odtwarzacz należy do usługi pierwszoplanowej |
| Pauza i wznowienie | razem z rutyną (ekran, powiadomienie, przyciski słuchawek) |
| Informacja o utworze | tytuł, wykonawca, stan (gra / wstrzymana / czeka na internet) |
| Utrata internetu | muzyka czeka na sieć i wznawia się od zapamiętanego miejsca. **Harmonogram i głos działają dalej** |

### Prywatne playlisty i utwory
- **Link prywatny** (w SoundCloud: „Udostępnij” → „Kopiuj link”) zawiera kod dostępu `s-…`. Rytm go zachowuje. Wklej link albo użyj „Udostępnij → Rytm”.
- **Kod „Osadź” (embed)** z SoundCloud też można wkleić: Rytm wyciągnie z niego adres odtwarzacza.
- **Twoje prywatne listy w przeglądarce wyboru:** zaloguj się na stronie SoundCloud (opcjonalnie), otwórz listę i naciśnij „Wybierz”. Rytm odczyta link z kodem dostępu z danych strony.
- Rytm zapisuje dokładny adres osadzenia, który podaje SoundCloud (oEmbed). Ten sam adres trafia do odtwarzacza.
- Jeśli wybrałeś prywatną playlistę w wersji 1.0.0 (wtedy kod dostępu był gubiony), wybierz ją ponownie.

### Ograniczenia (bez upiększeń)
- Widżet odtwarza tylko to, co SoundCloud pozwala osadzać. Utwory **SoundCloud Go+** zwykle grają jako **30-sekundowy fragment**. Utwory z wyłączonym osadzaniem lub zablokowane w regionie mogą nie zagrać wcale. Prywatna lista **bez** kodu dostępu (link bez `s-…`) zagra tylko wtedy, gdy SoundCloud wpuści zalogowaną sesję. W razie problemu użyj linku prywatnego. Gdy coś nie gra, aplikacja pokaże błąd i ponowi próbę, a rutyna trwa dalej.
- Muzyka z SoundCloud wymaga internetu. Głos i harmonogram działają bez niego.
- Rytm nie pobiera muzyki, nie zapisuje jej na telefonie i nie omija ograniczeń SoundCloud.
- Logowanie przez Google lub Apple może nie działać w przeglądarce wbudowanej (to blokada po stronie Google/Apple). Zaloguj się e-mailem albo pomiń logowanie: do wyszukiwania i odtwarzania nie jest potrzebne.
- Tryb **„Odtwarzaj w aplikacji SoundCloud”** (do wyboru w rutynie) otwiera utwór w oficjalnej aplikacji SoundCloud. Wtedy muzyką steruje tamta aplikacja: Rytm **nie zna jej stanu i nie może zmieniać jej głośności** ani jej wstrzymywać. Aplikacja wyraźnie to pokazuje.

---

## 4. Przyciszanie muzyki podczas komunikatów

**W odtwarzaczu wbudowanym (domyślnie) funkcja działa w pełni:**
- Muzyka **nigdy nie jest zatrzymywana na czas komunikatu.** Komunikaty są syntezowane do plików przed sesją i odtwarzane przez Rytm bez prośby o fokus audio. Dlatego silnik mowy telefonu nie może wstrzymać muzyki, co w 1.0.0 zdarzało się na części telefonów. Rytm nie prosi też o fokus audio dla muzyki.
- Na czas polecenia muzyka **cichnie o 25%** (ustawiasz 10–60%, zalecane 20–30%), czyli gra na 75% poprzedniej głośności. Nigdy nie jest zatrzymywana ani wyciszana.
- Przyciszanie zaczyna się **przed** komunikatem i jest płynne (krzywa bez skoków, ok. 0,4 s). Powrót do **dokładnie** poprzedniego poziomu też jest płynny (ok. 0,9 s).
- Gdy komunikaty padają blisko siebie, muzyka **nie wraca** między nimi, bo ma przytrzymanie 1,2 s. Dzięki temu nie ma „pompowania”.
- Zmieniana jest wyłącznie głośność samego odtwarzacza (`setVolume` widżetu). **Głośność telefonu pozostaje taka, jak ją ustawiłeś.**
- Wszystkie parametry (siłę, płynność ściszania i powrotu, przytrzymanie) zmienisz w **Ustawienia → Przyciszanie muzyki**.

**Gdyby coś jednak zatrzymało muzykę** (przycisk w słuchawkach Bluetooth, inna aplikacja, system), „strażnik” odtwarzania co 3 sekundy sprawdza stan odtwarzacza. Jeśli sesja trwa, wznawia muzykę, a gdy to nie pomaga, przeładowuje odtwarzacz od zapamiętanego miejsca. Muzyka zatrzymuje się tylko wtedy, gdy Ty wstrzymasz rutynę, podczas rozmowy telefonicznej i po zakończeniu rutyny.

**W trybie „aplikacja SoundCloud”:** Android nie pozwala jednej aplikacji ustawiać głośności drugiej. Rytm prosi więc system o przyciszenie innych aplikacji na czas komunikatu (AudioFocus „may duck”). Siłę przyciszenia ustala **Android** i zwykle jest wyraźnie mocniejsza niż 25%. Niektóre aplikacje zamiast przyciszyć się wtedy **wstrzymują**. Jeśli tak robi u Ciebie aplikacja SoundCloud, ustaw w Ustawieniach „Nie przyciszaj”. Jeśli zależy Ci na delikatnym przyciszaniu bez zatrzymywania, używaj odtwarzacza wbudowanego (domyślnego).

---

## 5. Uprawnienia: po co są potrzebne

| Uprawnienie | Po co | Czy pyta |
|---|---|---|
| Powiadomienia (`POST_NOTIFICATIONS`) | bieżące zadanie, odliczanie i przyciski na ekranie blokady | tak, Android 13+ |
| Usługa pierwszoplanowa – odtwarzanie (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`) | rutyna, głos i muzyka działają przy wygaszonym ekranie | nie |
| Blokada uśpienia (`WAKE_LOCK`) | procesor nie zasypia w trakcie rutyny, więc polecenia padają o czasie. Tylko podczas sesji | nie |
| Bez ograniczeń baterii (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) | niektórzy producenci zatrzymują aplikacje w tle | tak, okno systemowe |
| Dokładne alarmy (`USE_EXACT_ALARM`, Android 12: `SCHEDULE_EXACT_ALARM`) | start rutyny o godzinie pobudki | nie (Android 13+), ewentualnie ekran ustawień |
| Powiadomienia pełnoekranowe (`USE_FULL_SCREEN_INTENT`) | ekran rutyny z budzika pokazuje się nad blokadą | Android 14+: czasem trzeba włączyć w ustawieniach (aplikacja pokazuje przycisk) |
| Uruchomienie po starcie (`RECEIVE_BOOT_COMPLETED`) | odtworzenie budzików po restarcie telefonu | nie |
| Internet, stan sieci | odtwarzacz SoundCloud i wznowienie po odzyskaniu sieci | nie |
| Wibracje | delikatna wibracja przy zmianie zadania (można wyłączyć) | nie |

Rytm **nie** prosi o dostęp do mikrofonu, kontaktów, lokalizacji ani stanu telefonu. Połączenia wykrywa po trybie audio, bez dodatkowego uprawnienia.

---

## 6. Działanie w tle: czego się spodziewać

- Rutyna działa jako **usługa pierwszoplanowa** z powiadomieniem, blokadą uśpienia CPU i sesją multimediów (sterowanie ze słuchawek i ekranu blokady). Tak działają odtwarzacze muzyki na Androidzie 8–15.
- **Przy wygaszonym ekranie i w słuchawkach** głos i muzyka działają dalej. Odtwarzacz SoundCloud cały czas siedzi w oknie aplikacji (niewidoczny, gdy ekran sesji jest zamknięty), więc przeglądarka nie usypia go jak karty w tle. Podczas rutyny „Wstecz” na ekranie głównym tylko chowa aplikację. Najpewniej działa, gdy rutynę uruchomisz z aplikacji i potem wygasisz ekran. Ekran sesji można też pokazać nad blokadą (ustawienie).
- **Słuchawki:** głos idzie tam, gdzie muzyka. Nawet gdy w ustawieniach wybrano „głośność budzika”, przy podłączonych słuchawkach (przewodowych, USB, Bluetooth) Rytm używa multimediów, bo dźwięk budzika Android puszcza też z głośnika. Przycisk play/pauza na słuchawkach wstrzymuje i wznawia całą rutynę.
- **Połączenie telefoniczne:** komunikaty i muzyka są wstrzymywane na czas rozmowy, potem rutyna zachowuje się zgodnie z Twoim wyborem (tabela wyżej).
- **Inna aplikacja zacznie grać:** Rytm nie zatrzymuje swojej muzyki (nie walczy o fokus audio). Wstrzymaj ją ręcznie, jeśli chcesz słuchać czegoś innego. Harmonogram trwa.
- **Zamknięcie procesu przez system:** stan sesji jest zapisywany na bieżąco. Android zwykle sam uruchamia usługę ponownie. Jeśli tego nie zrobi, otwarcie aplikacji wznawia rutynę zgodnie z wybranym zachowaniem.
- **Restart telefonu w trakcie rutyny:** Android 15 nie pozwala aplikacjom muzycznym startować samoczynnie po restarcie. Rytm pokaże powiadomienie „Dotknij, aby wznowić”. Budziki są ustawiane ponownie automatycznie.
- **Agresywne oszczędzanie baterii** (część telefonów Xiaomi, Huawei, Oppo, Samsung): mimo zgody systemowej producent może zatrzymywać aplikacje w tle. Jeśli rutyna urywa się przy wygaszonym ekranie, dodaj Rytm do wyjątków w ustawieniach baterii producenta (instrukcje dla poszczególnych marek: dontkillmyapp.com).
- **Rutyna z budzika** startuje o ustawionej godzinie, także przy zablokowanym telefonie. Opcjonalnie komunikaty mogą wtedy iść na **głośności budzika**, żeby było je słychać nawet przy ściszonych multimediach. Od 1.1.0 jest to domyślnie wyłączone, a przy słuchawkach i tak nieużywane. Muzyka SoundCloud gra zawsze na głośności multimediów.

---

## 7. Co zostało przetestowane, a co nie

### Przetestowane automatycznie (123 testy, wszystkie przechodzą)
Uruchomienie: `./gradlew testDebugUnitTest`.

| Obszar | Testy |
|---|---|
| Poprawność harmonogramu | dokładne czasy przykładu z założeń (00:10–00:25 … 02:10–05:10), brak nakładania zadań, edycja (przesuwanie, kopiowanie, kolejność, przerwy, wykrywanie nakładania) |
| Przechodzenie między zadaniami i brak opóźnień | zmiana zadania dokładnie na granicy (np. 24,999 s → zadanie 0, 25,000 s → zadanie 1). Stan niezależny od częstotliwości wywołań (losowe, rzadkie „tyknięcia”) |
| Synchronizacja komunikatów | polecenia wypowiadane dokładnie o 10, 25, 40, 100, 130 s i koniec o 310 s; kompensacja opóźnienia mowy (dźwięk zaczyna się ≤ 150 ms od planu) |
| Długie komunikaty / brak nakładania | komunikaty po 12 s w zadaniach po 5–15 s nigdy się nie nakładają. Długi komunikat jest przerywany przez kolejne polecenie zamiast opóźniać harmonogram. Wykrywanie za długich tekstów w edytorze |
| Wygaszony ekran | symulacja zamrożenia procesu na 90 s: tylko „Teraz: <bieżące zadanie>”, kolejne polecenia nadal punktualnie |
| Odzyskiwanie stanu | po zabiciu procesu (3 zachowania), po restarcie telefonu (zegar ścienny), zapis i odczyt migawki, ponowne uruchomienie usługi przez system (test usługi w Robolectric) |
| Utrata internetu i przerwanie muzyki | muzyka „czeka na internet” lub „przerwana”, a harmonogram i głos działają dokładnie według planu |
| Przyciszanie | 75% w trakcie mowy, nigdy ciszej, płynne kroki, dokładny powrót do 100%, brak powrotu między bliskimi komunikatami, przyciszenie zaczyna się przed komunikatem |
| Tryb ścisły | brak czekania na potwierdzenie; użytkownik, który nie zdążył, i tak dostaje kolejne polecenie po 15 s |
| Tryb elastyczny | „Zrobione” wcześniej → od razu kolejne zadanie; brak reakcji → najwyżej limit czekania; rutyna nie może się zawiesić |
| Połączenia | „kontynuuj” (cisza w trakcie, potem „Teraz: …”), „wstrzymaj”, „wznów od bieżącego zadania” |
| Planowanie wg pobudki/wyjścia | 30 min do wyjścia → plan ≤ 30 min, zadania obowiązkowe bez zmian, sytuacja niemożliwa zgłaszana |
| Polszczyzna | „Masz trzydzieści sekund”, „Masz jedną minutę”, „Zostały dwie minuty”, „Została jedna minuta” itd. |
| Muzyka nie gaśnie (1.1.0) | „strażnik” odtwarzania: wznowienie po niespodziewanej pauzie, tolerancja krótkiego buforowania, przeładowanie po kilku nieudanych próbach, brak walki bez internetu, zapętlenie liczone jako postęp |
| Komunikaty z nagrań (1.1.0) | każde polecenie wypowiadane w sesji (także „Teraz: …” po pauzie, ostrzeżenia i koniec) jest w zestawie nagrań przygotowywanych przed startem |
| Prywatne playlisty (1.1.0) | kody `s-…` w linkach list i utworów, `?secret_token=`, kod „Osadź”, adresy API, adres osadzenia odczytany z oEmbed, brak fałszywych trafień dla treści publicznych |
| Przykładowe rutyny (1.1.0) | wszystkie przechodzą walidację bez za długich komunikatów, realistyczne czasy (np. zęby 2–3 min, śniadanie 10–20 min, prysznic 5–10 min), zapas przed wyjściem, dopasowanie 45 → 30 min usuwa tylko prysznic i skraca śniadanie |
| Aktualizacja z 1.0.0 (1.1.0) | migracja bazy v1 → v2 na prawdziwym schemacie v1 (rutyny, budzik, muzyka i zadania zostają), podmiana tylko niezmienionych przykładów, edytowane i własne rutyny nietknięte |
| Android (Robolectric) | baza Room (zapis, edycja, kopiowanie, kaskadowe usuwanie), DataStore, budzik w AlarmManager, **usługa pierwszoplanowa**: typ mediaPlayback, polecenie dokładnie po 10 s, sterowanie widżetem SoundCloud (`load`, `setVolume(75.0)`, `pause`), powiadomienie „Wstrzymano”, sprzątanie po zakończeniu, odtworzenie po zabiciu procesu |

### Przetestowane ręcznie / półautomatycznie
- **1.1.0:** odczyt stanu odtwarzacza (`probe`: pauza/gra, pozycja) i wznowienie po wymuszonej pauzie sprawdzone na prawdziwym widżecie. Playlista w formacie adresu osadzenia (`api.soundcloud.com/playlists/…`) gra. Mobilna strona SoundCloud (User-Agent Androida) działa: wyszukiwanie list, strony playlist. Skrypt odczytu linku prywatnego nie daje fałszywych trafień dla list publicznych i poprawnie zwraca link z kodem dla listy prywatnej (symulacja metadanych strony; nie mam prawdziwej prywatnej listy do testu).
- **Prawdziwy odtwarzacz SoundCloud** z naszego pliku `app/src/main/assets/soundcloud_player.html` w przeglądarce Chromium, z tym samym adresem bazowym co w aplikacji. Sprawdzone: wczytanie bez klucza API, tytuł, wykonawca i okładka utworu, odtwarzanie (rosnąca pozycja), `setVolume(75)` potwierdzone przez `getVolume() = 75`, pauza i wznowienie, zapętlanie po końcu utworu. Skrypty: `tools/soundcloud-widget-check/`.
- **Kompilacja**: `assembleDebug`, `assembleRelease`, `lint` bez błędów, czysta kompilacja przez `./gradlew`. APK zweryfikowane narzędziem `apksigner`.

### Nieprzetestowane na fizycznym telefonie (brak urządzenia lub emulatora w środowisku budowania)
- Odtwarzanie widżetu SoundCloud w **Android WebView przy wygaszonym ekranie i w słuchawkach Bluetooth** na prawdziwym telefonie. W 1.1.0 dodałem kilka zabezpieczeń: WebView zawsze w oknie aplikacji i zgłaszający się jako widoczny, brak własnego fokusu audio, komunikaty z nagrań, strażnik odtwarzania, wysoki priorytet procesu WebView i usługa pierwszoplanowa. Mimo to nie zostało to zmierzone na urządzeniu. Jeśli muzyka mimo to milknie, daj znać (model telefonu), a w razie potrzeby użyj trybu „Odtwarzaj w aplikacji SoundCloud”.
- Prywatna playlista z prawdziwego konta SoundCloud (sprawdzone na danych symulowanych i na formacie linków).
- Odtwarzanie komunikatów z nagrań przez MediaPlayer na urządzeniu. W testach usługi sprawdzona jest ścieżka zapasowa (mowa bezpośrednia), z której Rytm korzysta, gdy nagranie nie jest gotowe.
- Rzeczywiste brzmienie polskich głosów różnych producentów oraz zachowanie oszczędzania baterii u konkretnych producentów.
- Wygląd interfejsu na różnych ekranach. Kod Compose się kompiluje i przechodzi lint, ale nie był oglądany na urządzeniu.

---

## 8. Budowanie ze źródeł (dla programisty)

Wymagania: **Android Studio** (Ladybug 2024.2 lub nowsze) albo JDK 17+ i Android SDK 35.

```bash
./gradlew assembleRelease      # → app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest    # 123 testy
./gradlew lintRelease
```

W Android Studio: **File → Open** → wybierz katalog projektu → **Run ▶**. Nie trzeba ustawiać żadnych kluczy ani plików konfiguracyjnych.

Wersje: Gradle 8.14.3, Android Gradle Plugin 8.11.1, Kotlin 2.2.0, Compose BOM 2025.06.01, Room 2.7.2, minSdk 26, target/compileSdk 35.

**Podpis:** APK jest podpisany kluczem `keystore/rytm-sideload.jks`. Hasło jest jawne w `app/build.gradle.kts`. Ten klucz **nie jest tajny**: służy tylko temu, żeby kolejne wersje zbudowane z repozytorium instalowały się jako aktualizacja bez utraty rutyn. Do publikacji w Google Play użyj własnego, prywatnego klucza.

### Architektura

```
app/src/main/java/pl/rytm/app/
├── domain/               czysty Kotlin, bez Androida, w pełni testowalny
│   ├── model/            Routine, RoutineTask, tryby, ustawienia komunikatów
│   ├── schedule/         oś czasu (model „zadanie + przerwa przed”), walidacja konfliktów,
│   │                     planer dopasowania do godziny wyjścia
│   ├── session/          SessionEngine (stan = f(migawka, zegar monotoniczny)),
│   │                     AnnouncementScheduler (komunikaty, brak nakładania),
│   │                     SessionRunner (pętla czasu rzeczywistego, mowa, przyciszanie, przerwania)
│   ├── audio/            DuckingController – obwiednia głośności (płynnie, z przytrzymaniem)
│   ├── speech/ text/     szacowanie długości mowy, polskie liczebniki i odmiana
├── data/                 Room (rutyny), DataStore (ustawienia), zapis migawki sesji
├── service/              RoutineSessionService (usługa pierwszoplanowa mediaPlayback),
│                         powiadomienia, MediaSession, wykrywanie połączeń, słuchawki
├── music/                SoundCloudWidgetPlayer (oficjalny widżet w WebView, „parking” w oknie aktywności),
│                         PlaybackGuard (strażnik ciągłości muzyki), linki (także prywatne), oEmbed
├── speech/               AndroidSpeech (TextToSpeech), ClipSpeechOutput (komunikaty z nagrań, bez fokusu audio)
├── alarm/                budzik rutyny (AlarmManager.setAlarmClock), odbiorniki systemowe
└── ui/                   Jetpack Compose + Material 3 (MVVM): ekran główny, edytor,
                          wybór muzyki, aktywna sesja, ustawienia, pierwsze uruchomienie
```

Najważniejsza zasada: **interfejs nie odmierza czasu.** Harmonogram prowadzi usługa. Stan zadania jest zawsze wyliczany z czasu startu sesji (zegar monotoniczny) i z planu przygotowanego przed startem. Dzięki temu wygaszony ekran, opóźnione wywołania czy powrót do aplikacji nie rozregulują rutyny.

---

## 9. Prywatność

- Rutyny i ustawienia są tylko na tym telefonie (Room + DataStore). Aplikacja nie ma kont, własnych serwerów ani analityki.
- Połączenia sieciowe: oficjalny odtwarzacz i strona SoundCloud oraz publiczny endpoint oEmbed (tytuł i okładka).
- Hasła do SoundCloud wpisujesz wyłącznie na stronie SoundCloud. Rytm ich nie odczytuje ani nie zapisuje. „Ustawienia → Wyloguj z SoundCloud w Rytmie” usuwa dane logowania przeglądarki wbudowanej.
