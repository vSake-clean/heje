/* =========================================================================
   HackLingo – treść kursu (wersja rozbudowana: dużo poziomów)
   Wszystkie laboratoria są SYMULOWANE w przeglądarce/aplikacji. Niczego nie
   atakujemy w prawdziwym świecie. Cel: rozumieć podatności, aby je wykrywać
   i NAPRAWIAĆ (bezpieczeństwo ofensywne w służbie obrony).
   ========================================================================= */

/* --- skróty pomocnicze do budowania lekcji (mniej powtórzeń) --- */
function L_info(id, title, xp, infoTitle, html, quizzes) {
  const steps = [{ type: "info", title: infoTitle, html: html }];
  (quizzes || []).forEach((q) => steps.push(Object.assign({ type: "quiz" }, q)));
  return { id: id, title: title, xp: xp || 10, steps: steps };
}
function L_quiz(id, title, xp, quizzes) {
  return { id: id, title: title, xp: xp || 10, steps: quizzes.map((q) => Object.assign({ type: "quiz" }, q)) };
}

const COURSE = [
  /* ===================== 1. PODSTAWY ===================== */
  {
    id: "basics", title: "Podstawy sieci", short: "Jak działa web + etyka", icon: "🌐", color: "#58cc02",
    lessons: [
      L_info("b1", "Jak działa strona WWW", 10, "Żądanie i odpowiedź",
        "Przeglądarka wysyła do serwera <b>żądanie HTTP</b> (np. <span class='inline-code'>GET /produkt?id=5</span>), a serwer odsyła <b>odpowiedź</b> — HTML, dane JSON albo plik.<br><br>Prawie każda podatność webowa bierze się stąd, że serwer <b>ufa danym</b> od użytkownika. A użytkownik może wysłać <u>cokolwiek</u>.",
        [
          { q: "Co wysyła przeglądarka, żeby otworzyć stronę?", options: ["Żądanie HTTP", "Plik cookie serwera", "Klucz prywatny", "Certyfikat serwera"], answer: 0, explain: "Przeglądarka wysyła żądanie HTTP (GET/POST), serwer zwraca odpowiedź." },
          { q: "Dlaczego dane od użytkownika są niebezpieczne?", options: ["Bo użytkownik może wysłać dowolną, także złośliwą treść", "Bo są zawsze zaszyfrowane", "Bo przeglądarka je blokuje", "Bo serwer ich nie widzi"], answer: 0, explain: "Napastnik kontroluje każdy bajt — dane wejściowe to dane nieufne." }
        ]),
      L_info("b2", "Narzędzia dewelopera", 10, "Twój warsztat",
        "Klawisz <span class='kbd'>F12</span> otwiera <b>DevTools</b>:<br><br>• <b>Network</b> – żądania i odpowiedzi<br>• <b>Console</b> – uruchamianie JavaScriptu<br>• <b>Application</b> – ciasteczka, localStorage, tokeny<br>• <b>Elements</b> – żywy HTML strony",
        [{ q: "Gdzie podejrzysz ciasteczka i tokeny sesji?", options: ["Application / Storage", "Elements", "W /etc/passwd", "W nagłówku <head>"], answer: 0, explain: "Zakładka Application (Storage) pokazuje cookies i localStorage." }]),
      {
        id: "b3", title: "Etyka i zasada CIA", xp: 15, steps: [
          { type: "info", title: "⚖️ Zasady gry", html: "Testujesz tylko systemy, na które masz <b>pisemną zgodę</b> (albo poligony takie jak ta aplikacja). Włamanie bez zgody to przestępstwo. Cel etycznego hakera to <b>obrona</b>: znaleźć dziurę, zanim zrobi to ktoś zły — i ją załatać." },
          { type: "info", title: "Triada CIA", html: "• <b>C</b>onfidentiality – poufność<br>• <b>I</b>ntegrity – integralność<br>• <b>A</b>vailability – dostępność" },
          { type: "quiz", q: "Kiedy wolno testować cudzą stronę?", options: ["Tylko za zgodą właściciela lub na przeznaczonym poligonie", "Zawsze, jeśli znajdę dziurę", "Jeśli strona nie ma HTTPS", "W weekendy"], answer: 0, explain: "Zawsze potrzebna jest zgoda." },
          { type: "quiz", q: "Które 'C' oznacza, że tajne dane pozostają tajne?", options: ["Confidentiality", "Control", "Compression", "Caching"], answer: 0, explain: "Confidentiality = poufność." }
        ]
      },
      L_quiz("b4", "Metody HTTP", 10, [
        { q: "Która metoda służy zwykle do POBIERANIA danych?", options: ["GET", "DELETE", "PUT", "PATCH"], answer: 0, explain: "GET pobiera zasób. POST/PUT/PATCH zmieniają dane, DELETE usuwa." },
        { q: "Którą metodą wysyła się zwykle dane formularza logowania?", options: ["POST", "GET", "HEAD", "OPTIONS"], answer: 0, explain: "POST wysyła dane w ciele żądania (nie w adresie URL)." }
      ]),
      L_quiz("b5", "Kody odpowiedzi", 10, [
        { q: "Co oznacza kod <span class='inline-code'>200</span>?", options: ["OK — sukces", "Nie znaleziono", "Błąd serwera", "Brak dostępu"], answer: 0, explain: "2xx = sukces, 3xx = przekierowanie, 4xx = błąd klienta, 5xx = błąd serwera." },
        { q: "Kod <span class='inline-code'>403</span> to…", options: ["Zabronione (brak uprawnień)", "Sukces", "Przekierowanie", "Strona nie istnieje"], answer: 0, explain: "403 Forbidden — serwer rozumie żądanie, ale odmawia dostępu." },
        { q: "Kod <span class='inline-code'>500</span> wskazuje na…", options: ["Błąd po stronie serwera", "Poprawne logowanie", "Brak pliku", "Zbyt wolne łącze"], answer: 0, explain: "5xx = błąd serwera. Czasem zdradza szczegóły pomocne przy ataku." }
      ]),
      L_quiz("b6", "Ciasteczka i sesje", 10, [
        { q: "Do czego służy ciasteczko sesyjne?", options: ["Do rozpoznania zalogowanego użytkownika", "Do przyspieszenia grafiki", "Do szyfrowania dysku", "Do kompresji HTML"], answer: 0, explain: "Serwer po loginie wydaje token sesji w ciasteczku — kolejne żądania go dołączają." },
        { q: "Flaga <span class='inline-code'>HttpOnly</span> na ciasteczku…", options: ["Blokuje odczyt ciasteczka przez JavaScript", "Wymusza HTTP zamiast HTTPS", "Usuwa ciasteczko po godzinie", "Szyfruje jego wartość"], answer: 0, explain: "HttpOnly utrudnia kradzież sesji przez XSS — skrypt nie odczyta ciasteczka." }
      ])
    ]
  },

  /* ===================== 2. ROZPOZNANIE ===================== */
  {
    id: "recon", title: "Rozpoznanie", short: "Zbieranie informacji", icon: "🔎", color: "#ff9600",
    lessons: [
      L_info("rec1", "robots.txt i sitemap", 10, "Co strona sama zdradza",
        "Plik <span class='inline-code'>/robots.txt</span> mówi robotom, czego nie indeksować — często wskazuje ciekawe, 'ukryte' ścieżki. <span class='inline-code'>/sitemap.xml</span> wylicza podstrony.",
        [{ q: "Co można znaleźć w robots.txt?", options: ["Ścieżki, które autor chciał ukryć przed wyszukiwarką", "Hasła administratora", "Klucz TLS", "Kod źródłowy serwera"], answer: 0, explain: "Disallow często zdradza panele i katalogi — to wskazówka, nie zabezpieczenie." }]),
      L_info("rec2", "Ukryte ścieżki", 10, "Forced browsing",
        "Nawet jeśli link nie istnieje na stronie, zasób może być dostępny pod zgadniętym adresem (np. <span class='inline-code'>/admin</span>, <span class='inline-code'>/backup.zip</span>). To <b>forced browsing</b>.",
        [{ q: "Co to forced browsing?", options: ["Odwiedzanie zgadniętych adresów, do których nie ma linków", "Wymuszanie restartu serwera", "Blokada przeglądarki", "Szyfrowanie ruchu"], answer: 0, explain: "Zgadujesz adresy zasobów. Obrona: nie polegaj na 'ukryciu' (security by obscurity) — wymuszaj autoryzację." }]),
      L_info("rec3", "Fingerprinting", 10, "Rozpoznanie technologii",
        "Nagłówki odpowiedzi (<span class='inline-code'>Server</span>, <span class='inline-code'>X-Powered-By</span>) oraz komunikaty błędów zdradzają użyte technologie i ich wersje — a wersja = znane podatności.",
        [{ q: "Dlaczego warto ukrywać wersje oprogramowania w nagłówkach?", options: ["Bo wersja ujawnia znane podatności", "Bo przyspiesza stronę", "Bo wymaga tego HTTPS", "Bo zmniejsza koszt serwera"], answer: 0, explain: "Mniej informacji dla atakującego. To warstwa dodatkowa, nie zamiast łatania." }]),
      L_quiz("rec4", "Legalny OSINT", 10, [
        { q: "Rozpoznanie w etycznym teście powinno być…", options: ["W zakresie zgody (scope) i zgodne z prawem", "Bez żadnych ograniczeń", "Tajne przed klientem", "Nastawione na szkody"], answer: 0, explain: "Zawsze działasz w ustalonym zakresie (scope) i legalnie." },
        { q: "Co to 'scope' w teście bezpieczeństwa?", options: ["Uzgodniony zakres systemów, które wolno testować", "Nazwa narzędzia", "Rodzaj szyfru", "Typ serwera"], answer: 0, explain: "Scope określa, co i jak wolno testować — wyjście poza niego jest nielegalne." }
      ])
    ]
  },

  /* ===================== 3. SQL INJECTION ===================== */
  {
    id: "sqli", title: "SQL Injection", short: "Oszukaj zapytanie do bazy", icon: "🗄️", color: "#1cb0f6",
    lessons: [
      L_info("s1", "Czym jest SQLi?", 10, "Serwer rozmawia z bazą",
        "Logowanie w kodzie serwera często wygląda tak:<br><br><span class='inline-code'>SELECT * FROM users WHERE login='<b>TU</b>' AND haslo='<b>TU</b>'</span><br><br>Jeśli serwer <b>wkleja</b> Twój tekst między apostrofy, możesz z nich 'wyjść' i dopisać własny fragment. To <b>SQL Injection</b>.",
        [{ q: "Na czym polega SQLi?", options: ["Na wstrzyknięciu fragmentu do zapytania SQL przez pole formularza", "Na zgadywaniu hasła", "Na przeciążeniu serwera", "Na podmianie certyfikatu"], answer: 0, explain: "Dane wejściowe stają się częścią komendy SQL." }]),
      {
        id: "s2", title: "Lab: obejście logowania", xp: 25, steps: [
          { type: "challenge", kind: "sqli-login", title: "Zaloguj się bez hasła", brief: "Panel logowania podatny na SQLi. <b>Nie znasz hasła</b> administratora. Cel: dostać się do środka, manipulując zapytaniem.", hints: ["Serwer wkleja Twój login między apostrofy: <span class='inline-code'>login='TU'</span>. Co, jeśli sam wpiszesz apostrof?", "Po apostrofie dopisz warunek zawsze prawdziwy: <span class='inline-code'>OR '1'='1</span>.", "Resztę zapytania zakomentuj dwoma myślnikami: <span class='inline-code'>--</span>.", "Spróbuj w polu LOGIN: <span class='inline-code'>admin' --</span> albo <span class='inline-code'>' OR '1'='1</span>."] },
          { type: "quiz", q: "Najlepsza obrona przed SQLi?", options: ["Zapytania parametryzowane (prepared statements)", "Ukryć formularz", "Zablokować apostrof w przeglądarce", "Dłuższe hasła"], answer: 0, explain: "Prepared statements oddzielają kod SQL od danych." }
        ]
      },
      {
        id: "s3", title: "Lab: wyciek danych (UNION)", xp: 30, steps: [
          { type: "info", title: "Nie tylko logowanie", html: "SQLi w wyszukiwarce pozwala <b>dokleić</b> własne wyniki operatorem <span class='inline-code'>UNION SELECT</span> i wyciągnąć dane z innych tabel." },
          { type: "challenge", kind: "sqli-union", title: "Wydobądź hasła z bazy", brief: "Wyszukiwarka wkleja Twój tekst do zapytania. Użyj <span class='inline-code'>UNION SELECT</span>, aby wyświetlić login i hasło z tabeli <span class='inline-code'>users</span>.", hints: ["Zamknij tekst apostrofem, by wyjść z warunku.", "Doklej: <span class='inline-code'>UNION SELECT login, haslo FROM users</span>.", "Na końcu: <span class='inline-code'>--</span>. Całość: <span class='inline-code'>' UNION SELECT login,haslo FROM users --</span>"] },
          { type: "quiz", q: "Dlaczego hasła trzyma się jako hash (bcrypt)?", options: ["Żeby po wycieku nie dało się ich łatwo odczytać", "Żeby zajmowały mniej miejsca", "Żeby logowanie było szybsze", "Żeby działały bez HTTPS"], answer: 0, explain: "Hash + sól utrudniają odzyskanie haseł po wycieku." }
        ]
      },
      L_info("s4", "Blind SQLi", 15, "Gdy nie widać wyniku",
        "Czasem aplikacja nie pokazuje danych, ale zachowuje się różnie dla prawdy i fałszu (np. 'znaleziono' vs 'brak'). Można <b>po jednym bicie</b> wyciągać dane, zadając pytania tak/nie. To <b>blind SQL injection</b>.",
        [{ q: "Co wyróżnia blind SQLi?", options: ["Dane wyciąga się pośrednio, po różnicy w zachowaniu aplikacji", "Serwer pokazuje całą bazę", "Działa tylko offline", "Nie wymaga żadnego wejścia"], answer: 0, explain: "Obserwujesz reakcję (treść/czas) zamiast wprost widzieć dane." }]),
      L_quiz("s5", "Wykrywanie SQLi", 10, [
        { q: "Który znak najczęściej ujawnia SQLi przy testach?", options: ["Apostrof <span class='inline-code'>'</span>", "Spacja", "Kropka", "Przecinek"], answer: 0, explain: "Apostrof może zepsuć zapytanie i wywołać błąd SQL — pierwszy sygnał." },
        { q: "Nagły błąd bazy po wpisaniu <span class='inline-code'>'</span> sugeruje…", options: ["Możliwą podatność na SQLi", "Że strona jest bezpieczna", "Awarię sieci", "Brak HTTPS"], answer: 0, explain: "Błąd składni SQL = dane trafiają do zapytania bez zabezpieczenia." }
      ]),
      L_quiz("s6", "Obrona: SQLi", 15, [
        { q: "Które podejście NIE jest wystarczającą obroną przed SQLi?", options: ["Samo usuwanie apostrofów z wejścia (blacklist)", "Prepared statements", "ORM z parametrami", "Minimalne uprawnienia konta bazy"], answer: 0, explain: "Czarne listy łatwo obejść. Parametryzacja to właściwe rozwiązanie." },
        { q: "Minimalne uprawnienia konta bazy pomagają, bo…", options: ["Ograniczają szkody, nawet gdy dojdzie do wstrzyknięcia", "Przyspieszają zapytania", "Szyfrują ruch", "Blokują HTTP"], answer: 0, explain: "Defense in depth: mniej uprawnień = mniejszy wpływ udanego ataku." }
      ])
    ]
  },

  /* ===================== 4. XSS ===================== */
  {
    id: "xss", title: "Cross-Site Scripting", short: "Wstrzyknij skrypt do strony", icon: "💬", color: "#ce82ff",
    lessons: [
      L_info("x1", "Czym jest XSS?", 10, "Strona wyświetla Twój tekst",
        "Jeśli strona pokazuje innym to, co wpiszesz (komentarz, nick) <b>bez zabezpieczenia</b>, możesz wpisać kod <b>HTML/JS</b>, który wykona się u ofiary. To <b>XSS</b>.",
        [{ q: "Co wstrzykuje atakujący w XSS?", options: ["Kod JavaScript/HTML dla przeglądarki ofiary", "Zapytanie SQL", "Komendę systemu", "Certyfikat TLS"], answer: 0, explain: "XSS to wykonanie obcego skryptu w przeglądarce innego użytkownika." }]),
      {
        id: "x2", title: "Lab: księga gości", xp: 25, steps: [
          { type: "challenge", kind: "xss", title: "Uruchom alert w przeglądarce", brief: "Księga gości wyświetla komentarze <b>bez filtrowania</b>. Cel: sprawić, żeby wykonał się JavaScript (<span class='inline-code'>alert()</span>).", hints: ["Wpisz zwykły tag, np. <span class='inline-code'>&lt;b&gt;test&lt;/b&gt;</span>. Pogrubiło się? To znaczy, że HTML jest interpretowany.", "Skoro HTML działa, użyj tagu uruchamiającego skrypt.", "Klasyka: <span class='inline-code'>&lt;script&gt;alert(1)&lt;/script&gt;</span> lub <span class='inline-code'>&lt;img src=x onerror=alert(1)&gt;</span>."] },
          { type: "quiz", q: "Jak zabezpieczyć wyświetlanie komentarzy?", options: ["Kodować znaki specjalne (output encoding) + CSP", "Limit 10 znaków", "Wyłączyć JS dla wszystkich", "Wielkie litery"], answer: 0, explain: "Escaping zamienia < > & \" na encje. CSP to druga warstwa." }
        ]
      },
      L_quiz("x3", "Typy XSS", 10, [
        { q: "XSS, w którym payload zapisuje się w bazie i rażą wszystkich odwiedzających, to…", options: ["Stored (trwały) XSS", "Reflected XSS", "DOM XSS", "Blind SQLi"], answer: 0, explain: "Stored zapisuje się na serwerze i atakuje każdego, kto zobaczy treść." },
        { q: "XSS, w którym payload jest w adresie URL i odbija się w odpowiedzi, to…", options: ["Reflected (odbity) XSS", "Stored XSS", "CSRF", "IDOR"], answer: 0, explain: "Reflected — payload z żądania wraca w tej samej odpowiedzi." }
      ]),
      L_quiz("x4", "Skutki XSS", 10, [
        { q: "Co atakujący może zrobić przez XSS?", options: ["Ukraść sesję, podmienić treść, działać jako ofiara", "Sformatować dysk serwera", "Złamać szyfrowanie TLS", "Wyłączyć prąd"], answer: 0, explain: "Skrypt działa w kontekście ofiary — może wiele w jej imieniu." },
        { q: "Dlaczego HttpOnly ogranicza skutki XSS?", options: ["Bo JS nie odczyta ciasteczka sesji", "Bo wyłącza JavaScript", "Bo szyfruje HTML", "Bo blokuje obrazki"], answer: 0, explain: "HttpOnly chroni ciasteczko przed kradzieżą skryptem." }
      ]),
      L_info("x5", "Content-Security-Policy", 10, "Druga linia obrony",
        "Nagłówek <span class='inline-code'>Content-Security-Policy</span> mówi przeglądarce, skąd wolno ładować skrypty. Dobrze ustawiony CSP potrafi powstrzymać wykonanie wstrzykniętego skryptu, nawet gdy escaping zawiedzie.",
        [{ q: "Czym jest CSP?", options: ["Polityką ograniczającą źródła skryptów (obrona przed XSS)", "Typem bazy danych", "Algorytmem haszowania", "Protokołem transportu"], answer: 0, explain: "CSP to warstwa dodatkowa — nie zastępuje escapowania, wzmacnia je." }]),
      L_quiz("x6", "Obrona: XSS", 15, [
        { q: "Podstawowa obrona przed XSS to…", options: ["Kodowanie danych przy wyświetlaniu w odpowiednim kontekście", "Dłuższe hasła", "Wyłączenie ciasteczek", "Większy serwer"], answer: 0, explain: "Context-aware output encoding + CSP + walidacja wejścia." }
      ])
    ]
  },

  /* ===================== 5. UWIERZYTELNIANIE ===================== */
  {
    id: "auth", title: "Uwierzytelnianie", short: "Hasła, sesje, 2FA", icon: "🔑", color: "#ffc800",
    lessons: [
      L_info("a1", "Słabe hasła", 10, "Najsłabsze ogniwo",
        "Miliony kont używają haseł jak <span class='inline-code'>123456</span> czy <span class='inline-code'>password</span>. Bez <b>limitu prób</b> można je zgadywać automatycznie (atak słownikowy / brute-force).",
        [{ q: "Co najskuteczniej utrudnia zgadywanie haseł?", options: ["Limit prób + 2FA", "Ukrycie pola hasła", "Krótsze hasła", "Brak HTTPS"], answer: 0, explain: "Rate limiting, silne hasła i 2FA razem." }]),
      {
        id: "a2", title: "Lab: atak słownikowy", xp: 25, steps: [
          { type: "challenge", kind: "brute", title: "Zgadnij hasło konta admin", brief: "Panel <b>bez limitu prób</b>. Konto <span class='inline-code'>admin</span> ma bardzo popularne hasło. Wypróbuj listę.", hints: ["Użyj przycisku „Atak słownikowy” — próbuje po kolei najpopularniejsze hasła.", "Brak blokady po błędach to właśnie podatność.", "Hasło admina jest na liście TOP. Uruchom słownik."] },
          { type: "quiz", q: "Blokada konta po 5 błędach to…", options: ["Rate limiting / lockout", "Sharding", "Lazy loading", "Garbage collection"], answer: 0, explain: "Ograniczenie tempa prób powstrzymuje zgadywanie." }
        ]
      },
      L_quiz("a3", "Sesje i tokeny", 10, [
        { q: "Co powinien zrobić serwer przy wylogowaniu?", options: ["Unieważnić token sesji", "Usunąć konto", "Zmienić hasło", "Wyłączyć HTTPS"], answer: 0, explain: "Token musi przestać działać, inaczej sesja żyje dalej." },
        { q: "Token sesji powinien być…", options: ["Długi i losowy", "Krótki i przewidywalny", "Równy nazwie użytkownika", "Datą urodzenia"], answer: 0, explain: "Przewidywalny token = przejęcie cudzej sesji." }
      ]),
      L_info("a4", "JWT w pigułce", 12, "Token, który niesie dane",
        "JWT to token z trzech części: nagłówek.payload.podpis. Payload jest tylko <b>zakodowany</b> (base64), nie zaszyfrowany — każdy go odczyta. Bezpieczeństwo daje <b>podpis</b>; serwer musi go weryfikować.",
        [{ q: "Czy dane w payloadzie JWT są tajne?", options: ["Nie — są tylko zakodowane base64, każdy je odczyta", "Tak, są zaszyfrowane", "Tylko w nocy", "Tylko na HTTPS"], answer: 0, explain: "Nie wkładaj sekretów do payloadu JWT. Liczy się weryfikacja podpisu." }]),
      L_quiz("a5", "2FA / MFA", 10, [
        { q: "Drugi składnik (2FA) to zwykle…", options: ["Coś, co masz/jesteś (kod z aplikacji, klucz, odcisk)", "Drugie, dłuższe hasło", "Inny login", "Nowy e-mail"], answer: 0, explain: "MFA łączy różne kategorie: wiesz / masz / jesteś." },
        { q: "2FA pomaga, bo…", options: ["Samo skradzione hasło nie wystarcza do logowania", "Przyspiesza logowanie", "Szyfruje bazę", "Usuwa ciasteczka"], answer: 0, explain: "Dodatkowy składnik blokuje atak oparty tylko na haśle." }
      ]),
      L_quiz("a6", "Credential stuffing", 10, [
        { q: "Credential stuffing to…", options: ["Masowe logowanie wykradzionymi parami login:hasło z innych wycieków", "Tworzenie nowych kont", "Zmiana hasła admina", "Blokada serwera"], answer: 0, explain: "Ludzie powtarzają hasła — wyciek z jednego serwisu otwiera inne." },
        { q: "Najlepsza obrona użytkownika przed tym atakiem?", options: ["Unikalne hasła + menedżer haseł + 2FA", "Krótsze hasła", "Jedno hasło wszędzie", "Brak hasła"], answer: 0, explain: "Unikalne hasła odcinają efekt domina po wycieku." }
      ])
    ]
  },

  /* ===================== 6. IDOR ===================== */
  {
    id: "idor", title: "IDOR / Dostęp", short: "Podmień ID i zobacz cudze dane", icon: "🪪", color: "#1cb0f6",
    lessons: [
      L_info("i1", "Czym jest IDOR?", 10, "Numer w adresie",
        "Adres <span class='inline-code'>/profil?user_id=1337</span> pokazuje Twój profil. Zmień numer na <span class='inline-code'>1</span> — jeśli serwer <b>nie sprawdza</b>, czy to Twoje konto, zobaczysz cudze dane. To <b>IDOR</b>.",
        [{ q: "Co jest przyczyną IDOR?", options: ["Brak sprawdzenia uprawnień do obiektu po stronie serwera", "Za długie hasło", "Zbyt szybki serwer", "Brak robots.txt"], answer: 0, explain: "Serwer pokazuje obiekt po ID, nie sprawdzając uprawnień." }]),
      {
        id: "i2", title: "Lab: cudzy profil", xp: 25, steps: [
          { type: "challenge", kind: "idor", title: "Odczytaj dane innego użytkownika", brief: "Jesteś użytkownikiem <b>1337</b>. API zwraca profil po parametrze <span class='inline-code'>user_id</span>. Odczytaj konto administratora.", hints: ["W adresie jest <span class='inline-code'>user_id=1337</span> — to Twój numer.", "Serwer nie sprawdza, czy numer jest Twój. Co, jeśli wpiszesz inny?", "Administratorzy mają niskie ID. Spróbuj <span class='inline-code'>1</span>."] },
          { type: "quiz", q: "Jak naprawić IDOR?", options: ["Sprawdzać na serwerze, czy user ma prawo do obiektu", "Większe liczby jako ID", "Ukryć numer w adresie", "Dodać captcha"], answer: 0, explain: "Liczy się autoryzacja po stronie serwera." }
        ]
      },
      L_quiz("i3", "Eskalacja uprawnień", 10, [
        { q: "Pozioma eskalacja uprawnień to…", options: ["Dostęp do danych innego użytkownika o tych samych prawach", "Zostanie administratorem", "Restart serwera", "Zmiana języka"], answer: 0, explain: "Pozioma: inny user tego samego poziomu. Pionowa: wyższe uprawnienia." },
        { q: "Pionowa eskalacja to…", options: ["Zdobycie wyższych uprawnień (np. admina)", "Dostęp do konta sąsiada", "Wylogowanie", "Zmiana motywu"], answer: 0, explain: "Pionowa eskalacja = awans uprawnień." }
      ]),
      L_quiz("i4", "Obrona: kontrola dostępu", 15, [
        { q: "Zasada 'deny by default' oznacza…", options: ["Domyślnie brak dostępu; przyznaje się go świadomie", "Wszystko dozwolone", "Brak logowania", "Wyłączenie bazy"], answer: 0, explain: "Domyślna odmowa + jawne nadawanie uprawnień to bezpieczny wzorzec." },
        { q: "Gdzie MUSI być egzekwowana autoryzacja?", options: ["Na serwerze (nie tylko w interfejsie)", "Tylko w przeglądarce", "Tylko w CSS", "Nigdzie"], answer: 0, explain: "Interfejs można ominąć — serwer jest ostateczną bramką." }
      ])
    ]
  },

  /* ===================== 7. COMMAND INJECTION ===================== */
  {
    id: "cmd", title: "Command Injection", short: "Doklej komendę do systemu", icon: "💻", color: "#ff4b4b",
    lessons: [
      {
        id: "c1", title: "Lab: narzędzie ping", xp: 30, steps: [
          { type: "info", title: "Serwer uruchamia komendy", html: "Panel 'pinguje' adres, uruchamiając <span class='inline-code'>ping -c1 TU</span>. Jeśli Twój wpis trafia do powłoki, możesz <b>dokleić własną komendę</b>." },
          { type: "challenge", kind: "cmd", title: "Wykonaj komendę systemową", brief: "Wpisz adres do pingowania, ale doklej własną komendę (np. <span class='inline-code'>id</span> lub <span class='inline-code'>whoami</span>).", hints: ["Twój tekst trafia na koniec <span class='inline-code'>ping -c1 ...</span>.", "W powłoce komendy łączy się znakami <span class='inline-code'>;</span> , <span class='inline-code'>&&</span> , <span class='inline-code'>|</span>.", "Spróbuj: <span class='inline-code'>127.0.0.1; id</span> albo <span class='inline-code'>8.8.8.8 && whoami</span>."] },
          { type: "quiz", q: "Jak zapobiec Command Injection?", options: ["Nie budować komend ze stringów — bezpieczne API + allowlist", "Uruchamiać serwer jako root", "Więcej RAM", "Zmienić port"], answer: 0, explain: "Przekazuj argumenty jako listę do bezpiecznego API, waliduj wejście." }
        ]
      },
      L_info("c2", "Blind command injection", 15, "Gdy nie widać wyniku",
        "Czasem wynik komendy nie wraca do odpowiedzi. Można to wykryć pośrednio, np. komendą opóźniającą (<span class='inline-code'>sleep 5</span>) i mierząc czas odpowiedzi, albo wysyłając dane 'na zewnątrz' (out-of-band).",
        [{ q: "Jak wykryć 'ślepe' wstrzyknięcie komend?", options: ["Po zmianie czasu odpowiedzi (np. sleep)", "Po kolorze strony", "Po rozmiarze czcionki", "Po nazwie domeny"], answer: 0, explain: "Opóźnienie potwierdza wykonanie komendy, choć wyniku nie widać." }]),
      L_quiz("c3", "Obrona: command injection", 15, [
        { q: "Najbezpieczniejsze podejście do uruchamiania procesów?", options: ["API przyjmujące argumenty jako listę, bez powłoki", "Sklejanie komendy ze stringów", "Uruchamianie z uprawnieniami roota", "Wyłączenie logów"], answer: 0, explain: "Brak powłoki + lista argumentów = brak miejsca na wstrzyknięcie." },
        { q: "Allowlist wejścia oznacza…", options: ["Dopuszczanie tylko znanych, bezpiecznych wartości", "Blokowanie kilku złych znaków", "Akceptowanie wszystkiego", "Brak walidacji"], answer: 0, explain: "Biała lista jest bezpieczniejsza niż czarna." }
      ])
    ]
  },

  /* ===================== 8. PATH TRAVERSAL ===================== */
  {
    id: "path", title: "Path Traversal", short: "Wyjdź poza katalog", icon: "📁", color: "#58cc02",
    lessons: [
      {
        id: "p1", title: "Lab: podgląd plików", xp: 30, steps: [
          { type: "info", title: "Pobieranie plików", html: "Serwer udostępnia pliki z folderu <span class='inline-code'>/var/www/files/TU</span>. Sekwencja <span class='inline-code'>../</span> to 'katalog wyżej'. Bez kontroli nazwy można 'wyjść' z folderu." },
          { type: "challenge", kind: "path", title: "Odczytaj /etc/passwd", brief: "Podgląd plików wkleja nazwę do ścieżki. Wyjdź poza folder i odczytaj <span class='inline-code'>/etc/passwd</span>.", hints: ["Każde <span class='inline-code'>../</span> cofa o katalog w górę.", "Z <span class='inline-code'>/var/www/files/</span> do <span class='inline-code'>/</span> trzeba cofnąć się kilka razy.", "Spróbuj: <span class='inline-code'>../../../../etc/passwd</span>."] },
          { type: "quiz", q: "Jak bronić się przed Path Traversal?", options: ["Normalizować ścieżkę i sprawdzać, czy jest w dozwolonym folderze", "Zablokować kropkę w internecie", "Losowa nazwa folderu", "Serwować pliki nocą"], answer: 0, explain: "Kanoniczna ścieżka + allowlista. Najlepiej mapuj pliki po ID." }
        ]
      },
      L_info("p2", "LFI i RFI", 12, "Dołączanie plików",
        "Gdy aplikacja 'dołącza' plik po nazwie z wejścia, atakujący może wskazać lokalny plik (<b>LFI</b>) lub — w groźniejszym wariancie — zdalny (<b>RFI</b>), prowadząc nawet do wykonania kodu.",
        [{ q: "LFI to…", options: ["Local File Inclusion — dołączenie lokalnego pliku wg wejścia", "Logiczny błąd interfejsu", "Rodzaj zapory", "Format obrazu"], answer: 0, explain: "LFI dołącza pliki z serwera; RFI z sieci — oba bardzo groźne." }]),
      L_quiz("p3", "Obrona: path traversal", 15, [
        { q: "Najpewniejsza obrona przy udostępnianiu plików?", options: ["Mapować dozwolone pliki po stałych identyfikatorach", "Ufuć nazwie z wejścia", "Dodać opóźnienie", "Zmienić port"], answer: 0, explain: "Nie przekazuj surowej ścieżki od użytkownika do systemu plików." }
      ])
    ]
  },

  /* ===================== 9. CSRF ===================== */
  {
    id: "csrf", title: "CSRF", short: "Akcja w imieniu ofiary", icon: "🎭", color: "#ce82ff",
    lessons: [
      L_info("csrf1", "Czym jest CSRF?", 12, "Żądanie podrobione między witrynami",
        "Jesteś zalogowany w banku. Wchodzisz na złośliwą stronę, która po cichu wysyła żądanie 'przelej 1000 zł' do banku — a przeglądarka <b>automatycznie dołącza Twoje ciasteczko sesji</b>. To <b>CSRF</b>.",
        [{ q: "Co wykorzystuje CSRF?", options: ["Automatyczne dołączanie ciasteczek do żądań przez przeglądarkę", "Słabe hasło", "Brak HTTPS", "Błąd SQL"], answer: 0, explain: "Atakujący 'pożycza' Twoją uwierzytelnioną sesję do wykonania akcji." }]),
      L_quiz("csrf2", "Token CSRF", 10, [
        { q: "Token anty-CSRF chroni, bo…", options: ["Obca strona nie zna losowego tokenu wymaganego w żądaniu", "Szyfruje hasło", "Blokuje JavaScript", "Zmienia port"], answer: 0, explain: "Serwer wymaga nieprzewidywalnego tokenu, którego atakujący nie ma." }
      ]),
      L_quiz("csrf3", "SameSite cookies", 10, [
        { q: "Flaga <span class='inline-code'>SameSite</span> na ciasteczku…", options: ["Ogranicza wysyłanie ciasteczka przy żądaniach z innych witryn", "Szyfruje ciasteczko", "Wydłuża sesję", "Blokuje obrazki"], answer: 0, explain: "SameSite=Lax/Strict to mocna obrona przed CSRF." }
      ])
    ]
  },

  /* ===================== 10. SSRF ===================== */
  {
    id: "ssrf", title: "SSRF", short: "Serwer pobiera cudzy adres", icon: "🕳️", color: "#ff9600",
    lessons: [
      L_info("ssrf1", "Czym jest SSRF?", 12, "Zmuś serwer, by sam wszedł gdzieś",
        "Funkcja 'pobierz obraz z URL' pozwala podać adres. Jeśli brak kontroli, można zmusić <b>serwer</b> do sięgnięcia pod adresy wewnętrzne (np. <span class='inline-code'>http://localhost/admin</span>). To <b>SSRF</b>.",
        [{ q: "Co robi atakujący w SSRF?", options: ["Zmusza serwer do wykonania żądania pod wskazany (często wewnętrzny) adres", "Łamie hasło", "Psuje CSS", "Zmienia nazwę pliku"], answer: 0, explain: "Serwer staje się 'proxy' atakującego do sieci wewnętrznej." }]),
      L_quiz("ssrf2", "Metadane chmury", 10, [
        { q: "Dlaczego adres metadanych chmury (np. 169.254.169.254) jest groźnym celem SSRF?", options: ["Może zwrócić poświadczenia/tokeny serwera", "Jest kolorowy", "Jest szybki", "Jest publiczny"], answer: 0, explain: "Endpoint metadanych bywa źródłem kluczy — SSRF może je wykraść." }
      ]),
      L_quiz("ssrf3", "Obrona: SSRF", 15, [
        { q: "Dobra obrona przed SSRF to…", options: ["Allowlista dozwolonych hostów + blokada adresów wewnętrznych", "Dłuższe hasła", "Wyłączenie HTTPS", "Większy dysk"], answer: 0, explain: "Ogranicz, dokąd serwer może sięgać; blokuj zakresy prywatne." }
      ])
    ]
  },

  /* ===================== 11. UPLOAD PLIKÓW ===================== */
  {
    id: "upload", title: "Upload plików", short: "Niebezpieczne wgrywanie", icon: "📤", color: "#1cb0f6",
    lessons: [
      L_info("up1", "Groźne rozszerzenia", 12, "Nie każdy plik to obrazek",
        "Jeśli serwer pozwala wgrać plik i potem go <b>uruchamia</b> (np. <span class='inline-code'>.php</span>), wgranie 'obrazka' będącego skryptem może dać wykonanie kodu. Sprawdzanie tylko rozszerzenia nie wystarcza.",
        [{ q: "Dlaczego sama kontrola rozszerzenia to za mało?", options: ["Bo można je podrobić, a liczy się jak serwer traktuje plik", "Bo rozszerzenia nie istnieją", "Bo pliki są zawsze bezpieczne", "Bo przeglądarka to blokuje"], answer: 0, explain: "Waliduj typ, zmieniaj nazwy, przechowuj poza katalogiem wykonywalnym." }]),
      L_quiz("up2", "Content-Type i magic bytes", 10, [
        { q: "Czy nagłówkowi <span class='inline-code'>Content-Type</span> od klienta można ufać?", options: ["Nie — klient go ustawia i może skłamać", "Tak, zawsze", "Tylko na HTTPS", "Tylko dla obrazów"], answer: 0, explain: "Weryfikuj zawartość (np. sygnatury plików), nie wierz deklaracji klienta." }
      ]),
      L_quiz("up3", "Obrona: upload", 15, [
        { q: "Które NIE jest dobrą praktyką przy uploadzie?", options: ["Przechowywać pliki w katalogu, który serwer wykonuje jako kod", "Zmieniać nazwy plików", "Walidować typ zawartości", "Trzymać pliki poza webrootem"], answer: 0, explain: "Pliki użytkowników nie powinny być wykonywalne przez serwer." }
      ])
    ]
  },

  /* ===================== 12. KRYPTOGRAFIA I NAGŁÓWKI ===================== */
  {
    id: "crypto", title: "Krypto i nagłówki", short: "Hash, TLS, nagłówki", icon: "🔐", color: "#ffc800",
    lessons: [
      L_quiz("cr1", "Hash vs szyfrowanie", 10, [
        { q: "Czym różni się haszowanie od szyfrowania?", options: ["Hash jest jednokierunkowy; szyfrowanie można odwrócić kluczem", "Niczym", "Hash jest szybszy do odwrócenia", "Szyfrowanie jest jednokierunkowe"], answer: 0, explain: "Hasła hashujemy (bcrypt/argon2). Dane do odczytu — szyfrujemy." },
        { q: "Po co dodaje się 'sól' do haszowanych haseł?", options: ["By identyczne hasła miały różne hashe i utrudnić tablice tęczowe", "By hash był krótszy", "By działał offline", "By przyspieszyć logowanie"], answer: 0, explain: "Sól niweluje ataki tablicami tęczowymi." }
      ]),
      L_info("cr2", "HTTPS / TLS", 10, "Szyfrowany transport",
        "TLS szyfruje ruch między przeglądarką a serwerem, zapewniając poufność i integralność w drodze. Bez HTTPS dane (w tym hasła) lecą jawnie i można je podsłuchać.",
        [{ q: "Co daje HTTPS?", options: ["Szyfrowanie i integralność danych w transporcie", "Szybszy serwer", "Ochronę przed SQLi", "Darmowy hosting"], answer: 0, explain: "TLS chroni dane w drodze — ale nie zastępuje innych zabezpieczeń." }]),
      L_quiz("cr3", "Nagłówki bezpieczeństwa", 10, [
        { q: "Nagłówek <span class='inline-code'>X-Frame-Options</span> / frame-ancestors chroni przed…", options: ["Clickjackingiem (osadzeniem strony w ramce)", "SQLi", "Brute-force", "DNS"], answer: 0, explain: "Blokuje ładowanie strony w <iframe> na obcej witrynie." },
        { q: "Nagłówek <span class='inline-code'>HSTS</span> wymusza…", options: ["Korzystanie z HTTPS", "Większe hasła", "Wyłączenie ciasteczek", "Kompresję"], answer: 0, explain: "HSTS każe przeglądarce zawsze używać HTTPS dla danej domeny." }
      ]),
      L_quiz("cr4", "Flagi ciasteczek", 10, [
        { q: "Które flagi wzmacniają ciasteczko sesji?", options: ["HttpOnly + Secure + SameSite", "Public + Fast", "Big + Small", "Red + Blue"], answer: 0, explain: "HttpOnly (bez JS), Secure (tylko HTTPS), SameSite (anty-CSRF)." }
      ])
    ]
  },

  /* ===================== 13. METODYKA I NARZĘDZIA ===================== */
  {
    id: "method", title: "Metodyka", short: "OWASP, narzędzia, zgłaszanie", icon: "🧰", color: "#ff4b4b",
    lessons: [
      L_info("m1", "OWASP Top 10", 10, "Mapa najczęstszych ryzyk",
        "OWASP Top 10 to lista najpoważniejszych ryzyk aplikacji webowych (m.in. Broken Access Control, Injection, Cryptographic Failures). To dobra mapa tego, czego szukać i co wzmacniać.",
        [{ q: "Czym jest OWASP Top 10?", options: ["Listą najczęstszych klas ryzyk w aplikacjach webowych", "Językiem programowania", "Marką antywirusa", "Protokołem sieciowym"], answer: 0, explain: "To punkt odniesienia dla testów i obrony." }]),
      L_info("m2", "Proxy przechwytujące", 10, "Podgląd i edycja ruchu",
        "Narzędzia typu Burp Suite / OWASP ZAP działają jak proxy: pokazują i pozwalają zmieniać żądania między przeglądarką a serwerem — podstawowy warsztat testera (używany tylko w ramach zgody).",
        [{ q: "Do czego służy proxy przechwytujące?", options: ["Do podglądu i modyfikacji żądań HTTP w teście", "Do grania w gry", "Do edycji zdjęć", "Do pisania maili"], answer: 0, explain: "Pozwala zobaczyć i zmienić to, co leci do serwera." }]),
      L_quiz("m3", "Odpowiedzialne zgłaszanie", 15, [
        { q: "Co zrobić po znalezieniu podatności w cudzym systemie (za zgodą)?", options: ["Zgłosić ją właścicielowi i dać czas na naprawę (responsible disclosure)", "Opublikować exploit od razu", "Wykorzystać do własnych celów", "Sprzedać dane"], answer: 0, explain: "Zgłaszasz prywatnie, pomagasz naprawić — to etyka i często zasady bug bounty." },
        { q: "Program bug bounty to…", options: ["Legalny program nagradzający za zgłaszanie błędów bezpieczeństwa", "Konkurs graficzny", "Rodzaj wirusa", "Serwer gier"], answer: 0, explain: "Firmy płacą za zgłoszenia w ustalonym zakresie i zasadach." }
      ])
    ]
  },

  /* ===================== 14. EGZAMIN ===================== */
  {
    id: "boss", title: "Egzamin", short: "Sprawdź całą wiedzę", icon: "🏴", color: "#ce82ff", boss: true,
    lessons: [
      {
        id: "boss1", title: "Finał: Czerwona drużyna", xp: 60, steps: [
          { type: "info", title: "🏴 Finał", html: "Połącz atak i obronę. Kilka pytań i jedno wyzwanie." },
          { type: "quiz", q: "Formularz odbija tekst w HTML bez kodowania. To…", options: ["XSS", "SQLi", "IDOR", "Path Traversal"], answer: 0, explain: "Odbity, niekodowany tekst w HTML = XSS." },
          { type: "quiz", q: "Logowanie wpuszcza po <span class='inline-code'>admin' --</span>. Co zawiodło?", options: ["Brak zapytań parametryzowanych (SQLi)", "Za silne hasło", "Brak captcha", "Za duży serwer"], answer: 0, explain: "Dane trafiły do SQL jako kod." },
          { type: "quiz", q: "Zmiana <span class='inline-code'>?id=1337</span> na <span class='inline-code'>?id=1</span> pokazuje cudzy profil. Co naprawić?", options: ["Autoryzację po stronie serwera", "Kolor przycisku", "Czcionkę", "Domenę"], answer: 0, explain: "To IDOR — brak kontroli dostępu." },
          { type: "challenge", kind: "cmd", title: "Ostatni cel: przejmij powłokę", brief: "Panel znów 'pinguje' host. Udowodnij wykonanie kodu, doklejając komendę.", hints: ["To wzorzec z modułu Command Injection.", "Połącz komendy znakiem <span class='inline-code'>;</span> lub <span class='inline-code'>&&</span>.", "Np. <span class='inline-code'>127.0.0.1; id</span>."] }
        ]
      },
      L_quiz("boss2", "Egzamin: obrona", 50, [
        { q: "Dopasuj obronę: przed SQLi chroni…", options: ["Parametryzacja zapytań", "Dłuższe nazwy plików", "Wyłączenie CSS", "Zmiana portu"], answer: 0, explain: "Prepared statements." },
        { q: "Przed XSS chroni…", options: ["Kodowanie wyjścia + CSP", "Większy serwer", "Brak ciasteczek", "Krótsze hasło"], answer: 0, explain: "Output encoding + CSP." },
        { q: "Przed CSRF chroni…", options: ["Token anty-CSRF + SameSite", "Dłuższe hasło", "Wyłączenie HTTPS", "Więcej RAM"], answer: 0, explain: "Token + SameSite." },
        { q: "Przed brute-force chroni…", options: ["Rate limiting + 2FA", "Krótsze hasła", "Brak logów", "Jawne hasła"], answer: 0, explain: "Ograniczenie prób + drugi składnik." },
        { q: "Przed IDOR chroni…", options: ["Autoryzacja dostępu do obiektu na serwerze", "Ukrycie numeru ID", "Większe ID", "Captcha"], answer: 0, explain: "Serwer musi weryfikować prawo do obiektu." }
      ])
    ]
  }
];

/* ---------------------- Słownik pojęć ---------------------- */
const GLOSSARY = [
  { term: "SQL Injection (SQLi)", tags: ["baza", "wstrzyknięcie"], def: "Wstrzyknięcie fragmentu SQL przez dane wejściowe. Obrona: zapytania parametryzowane." },
  { term: "XSS", tags: ["przeglądarka", "javascript"], def: "Wykonanie obcego skryptu w przeglądarce ofiary. Obrona: kodowanie wyjścia + CSP." },
  { term: "IDOR", tags: ["autoryzacja"], def: "Dostęp do cudzego obiektu przez podmianę identyfikatora. Obrona: kontrola dostępu na serwerze." },
  { term: "Command Injection", tags: ["system", "shell"], def: "Doklejenie komendy systemowej. Obrona: bezpieczne API zamiast powłoki + allowlista." },
  { term: "Path Traversal", tags: ["pliki"], def: "Użycie ../ do wyjścia poza katalog. Obrona: normalizacja ścieżki + allowlista." },
  { term: "CSRF", tags: ["sesja"], def: "Żądanie wykonane w imieniu zalogowanej ofiary. Obrona: token CSRF + SameSite." },
  { term: "SSRF", tags: ["serwer"], def: "Zmuszenie serwera do żądania pod wskazany adres (często wewnętrzny). Obrona: allowlista hostów." },
  { term: "Brute-force / słownik", tags: ["hasła"], def: "Automatyczne zgadywanie haseł. Obrona: rate limiting, blokady, 2FA." },
  { term: "Credential stuffing", tags: ["hasła"], def: "Logowanie wykradzionymi parami login:hasło z innych wycieków. Obrona: unikalne hasła + 2FA." },
  { term: "Prepared statement", tags: ["obrona", "baza"], def: "Zapytanie parametryzowane — dane oddzielone od kodu SQL." },
  { term: "CSP", tags: ["obrona"], def: "Content-Security-Policy — ogranicza źródła skryptów. Warstwa obrony przed XSS." },
  { term: "JWT", tags: ["tokeny"], def: "Token nagłówek.payload.podpis. Payload jest tylko zakodowany base64 — nie wkładaj tam sekretów." },
  { term: "2FA / MFA", tags: ["obrona", "hasła"], def: "Dodatkowy składnik logowania (coś, co masz/jesteś) poza hasłem." },
  { term: "Triada CIA", tags: ["podstawy"], def: "Poufność, Integralność, Dostępność — trzy filary bezpieczeństwa." },
  { term: "Hash + sól", tags: ["obrona", "hasła"], def: "Hasła jako jednokierunkowy skrót z losową solą (bcrypt/argon2)." },
  { term: "HSTS", tags: ["nagłówki"], def: "Wymusza na przeglądarce używanie HTTPS dla domeny." },
  { term: "SameSite", tags: ["ciasteczka"], def: "Flaga ciasteczka ograniczająca wysyłkę przy żądaniach z innych witryn (anty-CSRF)." },
  { term: "OWASP Top 10", tags: ["metodyka"], def: "Lista najczęstszych klas ryzyk aplikacji webowych." },
  { term: "Responsible disclosure", tags: ["etyka"], def: "Prywatne zgłoszenie podatności właścicielowi i czas na naprawę." },
  { term: "Payload", tags: ["atak"], def: "Przygotowany ciąg wywołujący podatność (np. ' OR '1'='1)." }
];

/* ---------------------- Odznaki ---------------------- */
const BADGES = [
  { id: "first", ico: "🐣", name: "Pierwszy poziom", test: (p) => Object.keys(p.done).length >= 1 },
  { id: "ten", ico: "🎖️", name: "10 poziomów", test: (p) => Object.keys(p.done).length >= 10 },
  { id: "sqli", ico: "🗄️", name: "Mistrz SQLi", test: (p) => p.done["s2"] },
  { id: "xss", ico: "💥", name: "Łowca XSS", test: (p) => p.done["x2"] },
  { id: "idor", ico: "🪪", name: "Podmieniacz ID", test: (p) => p.done["i2"] },
  { id: "shell", ico: "💻", name: "Shell master", test: (p) => p.done["c1"] },
  { id: "streak3", ico: "🔥", name: "Passa 3 dni", test: (p) => p.streak >= 3 },
  { id: "streak7", ico: "⚡", name: "Passa 7 dni", test: (p) => p.streak >= 7 },
  { id: "half", ico: "🚀", name: "Połowa kursu", test: (p) => Object.keys(p.done).length >= 25 },
  { id: "boss", ico: "🏆", name: "Czerwona drużyna", test: (p) => p.done["boss1"] },
  { id: "all", ico: "🎓", name: "Ukończono kurs", test: (p) => COURSE.every((u) => u.lessons.every((l) => p.done[l.id])) }
];
