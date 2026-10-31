/* =========================================================================
   HackLingo – treść kursu
   Wszystkie laboratoria są SYMULOWANE w przeglądarce. Niczego nie atakujemy
   w prawdziwym świecie. Celem jest zrozumienie podatności, aby umieć je
   wykrywać i NAPRAWIAĆ (bezpieczeństwo ofensywne w służbie obrony).
   ========================================================================= */

const COURSE = [
  /* ------------------------------------------------------------------ */
  {
    id: "basics",
    title: "Podstawy sieci",
    short: "Jak działa web + etyka",
    icon: "🌐",
    color: "#58cc02",
    lessons: [
      {
        id: "b1",
        title: "Jak działa strona WWW",
        xp: 10,
        steps: [
          {
            type: "info",
            title: "Żądanie i odpowiedź",
            html:
              "Przeglądarka wysyła do serwera <b>żądanie HTTP</b> (np. <span class='inline-code'>GET /produkt?id=5</span>), a serwer odsyła <b>odpowiedź</b> — zwykle HTML, dane JSON albo plik.<br><br>Prawie każda podatność webowa bierze się stąd, że serwer <b>ufa danym</b>, które wysyła użytkownik. A użytkownik może wysłać <u>cokolwiek</u>."
          },
          {
            type: "quiz",
            q: "Co wysyła przeglądarka do serwera, żeby otworzyć stronę?",
            options: ["Żądanie HTTP", "Plik cookie serwera", "Klucz prywatny", "Certyfikat SSL serwera"],
            answer: 0,
            explain: "Przeglądarka wysyła żądanie HTTP (np. GET/POST). Serwer zwraca odpowiedź."
          },
          {
            type: "quiz",
            q: "Dlaczego dane od użytkownika są niebezpieczne?",
            options: [
              "Bo użytkownik może wysłać dowolną treść, także złośliwą",
              "Bo są zawsze zaszyfrowane",
              "Bo przeglądarka je blokuje",
              "Bo serwer ich nie widzi"
            ],
            answer: 0,
            explain: "Napastnik kontroluje każdy bajt, który wysyła. Dlatego dane wejściowe trzeba walidować i traktować jak nieufne."
          }
        ]
      },
      {
        id: "b2",
        title: "Narzędzia dewelopera",
        xp: 10,
        steps: [
          {
            type: "info",
            title: "Twój warsztat",
            html:
              "Klawisz <span class='kbd'>F12</span> otwiera <b>DevTools</b>. Najważniejsze zakładki dla bezpieczeństwa:<br><br>• <b>Network</b> – podgląd żądań i odpowiedzi<br>• <b>Console</b> – uruchamianie JavaScriptu<br>• <b>Application</b> – ciasteczka, localStorage, tokeny<br>• <b>Elements</b> – żywy kod HTML strony"
          },
          {
            type: "quiz",
            q: "Gdzie podejrzysz ciasteczka i tokeny sesji zapisane w przeglądarce?",
            options: ["W zakładce Application / Storage", "W zakładce Elements", "W pliku /etc/passwd", "W nagłówku <head>"],
            answer: 0,
            explain: "Zakładka Application (Storage) pokazuje cookies, localStorage i sessionStorage."
          }
        ]
      },
      {
        id: "b3",
        title: "Etyka i zasada CIA",
        xp: 15,
        steps: [
          {
            type: "info",
            title: "⚖️ Zasady gry",
            html:
              "Testujesz tylko systemy, na które masz <b>pisemną zgodę</b> (lub takie, które są do tego przeznaczone, jak to laboratorium). Włamanie bez zgody to przestępstwo.<br><br>Celem etycznego hakera jest <b>obrona</b>: znaleźć dziurę, zanim zrobi to ktoś zły — i ją załatać."
          },
          {
            type: "info",
            title: "Triada CIA",
            html:
              "Bezpieczeństwo opiera się na trzech filarach:<br><br>• <b>C</b>onfidentiality – poufność (nikt niepowołany nie czyta danych)<br>• <b>I</b>ntegrity – integralność (dane nie są podmieniane)<br>• <b>A</b>vailability – dostępność (usługa działa)"
          },
          {
            type: "quiz",
            q: "Kiedy wolno testować bezpieczeństwo cudzej strony?",
            options: [
              "Tylko za wyraźną zgodą właściciela lub na przeznaczonym do tego poligonie",
              "Zawsze, jeśli znajdę dziurę",
              "Jeśli strona nie ma HTTPS",
              "W weekendy"
            ],
            answer: 0,
            explain: "Zawsze potrzebna jest zgoda. Bez niej to łamanie prawa, nawet w 'dobrej wierze'."
          },
          {
            type: "quiz",
            q: "Które 'C' oznacza, że tajne dane pozostają tajne?",
            options: ["Confidentiality (poufność)", "Control", "Compression", "Caching"],
            answer: 0,
            explain: "Confidentiality = poufność. To pierwszy filar triady CIA."
          }
        ]
      }
    ]
  },

  /* ------------------------------------------------------------------ */
  {
    id: "sqli",
    title: "SQL Injection",
    short: "Oszukaj zapytanie do bazy",
    icon: "🗄️",
    color: "#1cb0f6",
    lessons: [
      {
        id: "s1",
        title: "Czym jest SQLi?",
        xp: 10,
        steps: [
          {
            type: "info",
            title: "Serwer rozmawia z bazą",
            html:
              "Logowanie często wygląda tak w kodzie serwera:<br><br><span class='inline-code'>SELECT * FROM users WHERE login='<b>TU_LOGIN</b>' AND haslo='<b>TU_HASLO</b>'</span><br><br>Jeśli serwer po prostu <b>wkleja</b> to, co wpiszesz, pomiędzy apostrofy — możesz 'wyjść' z tekstu i dopisać własny fragment zapytania. To jest <b>SQL Injection</b>."
          },
          {
            type: "quiz",
            q: "Na czym polega SQL Injection?",
            options: [
              "Na wstrzyknięciu własnego fragmentu do zapytania SQL przez pole formularza",
              "Na zgadywaniu hasła administratora",
              "Na przeciążeniu serwera ruchem",
              "Na podmianie certyfikatu HTTPS"
            ],
            answer: 0,
            explain: "Dane wejściowe stają się częścią komendy SQL — atakujący zmienia sens zapytania."
          }
        ]
      },
      {
        id: "s2",
        title: "Laboratorium: obejście logowania",
        xp: 25,
        steps: [
          {
            type: "challenge",
            kind: "sqli-login",
            title: "Zaloguj się bez hasła",
            brief:
              "Przed Tobą panel logowania podatny na SQLi. <b>Nie znasz hasła</b> administratora. Twój cel: dostać się do środka, manipulując zapytaniem.",
            hints: [
              "Serwer wkleja Twój login między apostrofy: <span class='inline-code'>... login='TU' ...</span>. Co się stanie, jeśli sam wpiszesz apostrof?",
              "Po wyjściu z apostrofu możesz dopisać warunek, który jest ZAWSZE prawdziwy, np. <span class='inline-code'>OR '1'='1</span>.",
              "Resztę zapytania (sprawdzanie hasła) możesz 'zakomentować' dwoma myślnikami: <span class='inline-code'>--</span>.",
              "Spróbuj w polu LOGIN wpisać: <span class='inline-code'>admin' --</span> albo <span class='inline-code'>' OR '1'='1</span>."
            ]
          },
          {
            type: "quiz",
            q: "Jak NAJLEPIEJ obronić się przed SQL Injection?",
            options: [
              "Zapytania parametryzowane (prepared statements) — dane nigdy nie są kodem",
              "Ukryć formularz logowania",
              "Zablokować klawisz apostrofu w przeglądarce",
              "Używać dłuższych haseł"
            ],
            answer: 0,
            explain: "Prepared statements oddzielają kod SQL od danych. Walidacja wejścia i najmniejsze uprawnienia bazy to dodatkowe warstwy."
          }
        ]
      },
      {
        id: "s3",
        title: "Laboratorium: wyciek danych (UNION)",
        xp: 30,
        steps: [
          {
            type: "info",
            title: "Nie tylko logowanie",
            html:
              "SQLi w polu wyszukiwania pozwala <b>dokleić</b> własne wyniki operatorem <span class='inline-code'>UNION SELECT</span> i wyciągnąć dane z innych tabel — np. loginy i hasła."
          },
          {
            type: "challenge",
            kind: "sqli-union",
            title: "Wydobądź hasła z bazy",
            brief:
              "Wyszukiwarka produktów wkleja Twój tekst do zapytania. Użyj <span class='inline-code'>UNION SELECT</span>, aby zamiast produktów wyświetlić login i hasło z tabeli <span class='inline-code'>users</span>.",
            hints: [
              "Najpierw zamknij tekst apostrofem, żeby wyjść z oryginalnego warunku.",
              "Doklej własny zestaw kolumn: <span class='inline-code'>UNION SELECT login, haslo FROM users</span>.",
              "Na końcu zakomentuj resztę: <span class='inline-code'>--</span>. Spróbuj: <span class='inline-code'>' UNION SELECT login,haslo FROM users --</span>"
            ]
          },
          {
            type: "quiz",
            q: "Dlaczego hasła w bazie powinny być 'zahaszowane' (np. bcrypt)?",
            options: [
              "Żeby po wycieku nie dało się ich łatwo odczytać",
              "Żeby zajmowały mniej miejsca",
              "Żeby logowanie było szybsze",
              "Żeby działały bez HTTPS"
            ],
            answer: 0,
            explain: "Hash + sól sprawiają, że nawet po wycieku bazy odzyskanie haseł jest bardzo trudne."
          }
        ]
      }
    ]
  },

  /* ------------------------------------------------------------------ */
  {
    id: "xss",
    title: "Cross-Site Scripting",
    short: "Wstrzyknij skrypt do strony",
    icon: "💬",
    color: "#ce82ff",
    lessons: [
      {
        id: "x1",
        title: "Czym jest XSS?",
        xp: 10,
        steps: [
          {
            type: "info",
            title: "Strona wyświetla Twój tekst",
            html:
              "Jeśli strona pokazuje innym to, co wpiszesz (komentarz, nick, opis) <b>bez zabezpieczenia</b>, możesz wpisać kod <b>HTML/JavaScript</b>, który wykona się w przeglądarce ofiary. To <b>XSS</b> — kradzież sesji, podmiana treści, keyloggery."
          },
          {
            type: "quiz",
            q: "Co atakujący wstrzykuje w ataku XSS?",
            options: ["Kod JavaScript/HTML, który wykona przeglądarka ofiary", "Zapytanie SQL", "Komendę systemu operacyjnego", "Nowy certyfikat TLS"],
            answer: 0,
            explain: "XSS to wykonanie obcego skryptu w przeglądarce innego użytkownika."
          }
        ]
      },
      {
        id: "x2",
        title: "Laboratorium: księga gości",
        xp: 25,
        steps: [
          {
            type: "challenge",
            kind: "xss",
            title: "Uruchom alert w przeglądarce",
            brief:
              "Ta księga gości wyświetla komentarze <b>bez filtrowania</b>. Twój cel: sprawić, żeby wykonał się JavaScript (klasyczny dowód: <span class='inline-code'>alert()</span>).",
            hints: [
              "Spróbuj wpisać zwykły znacznik HTML — np. <span class='inline-code'>&lt;b&gt;test&lt;/b&gt;</span>. Czy tekst zrobił się pogrubiony? Jeśli tak — strona nie filtruje HTML.",
              "Skoro HTML działa, spróbuj znacznika, który uruchamia skrypt.",
              "Klasyka: <span class='inline-code'>&lt;script&gt;alert(1)&lt;/script&gt;</span> albo <span class='inline-code'>&lt;img src=x onerror=alert(1)&gt;</span>."
            ]
          },
          {
            type: "quiz",
            q: "Jak serwer powinien zabezpieczyć wyświetlanie komentarzy?",
            options: [
              "Kodować znaki specjalne przy wyświetlaniu (output encoding) + CSP",
              "Zakazać komentarzy dłuższych niż 10 znaków",
              "Wyłączyć JavaScript dla wszystkich",
              "Zapisywać komentarze wielkimi literami"
            ],
            answer: 0,
            explain: "Escapowanie (& < > \" → encje) sprawia, że tekst zostaje tekstem. Nagłówek Content-Security-Policy to druga warstwa obrony."
          }
        ]
      }
    ]
  },

  /* ------------------------------------------------------------------ */
  {
    id: "auth",
    title: "Słabe uwierzytelnianie",
    short: "Hasła, brute-force, sesje",
    icon: "🔑",
    color: "#ff9600",
    lessons: [
      {
        id: "a1",
        title: "Czym jest słabe hasło?",
        xp: 10,
        steps: [
          {
            type: "info",
            title: "Najsłabsze ogniwo",
            html:
              "Miliony kont używa haseł typu <span class='inline-code'>123456</span>, <span class='inline-code'>password</span>, <span class='inline-code'>qwerty</span>. Jeśli serwer <b>nie ogranicza liczby prób</b>, napastnik może zgadywać hasła automatycznie (atak słownikowy / brute-force)."
          },
          {
            type: "quiz",
            q: "Co najskuteczniej utrudnia zgadywanie haseł?",
            options: [
              "Limit prób + 2FA (uwierzytelnianie dwuskładnikowe)",
              "Ukrycie pola hasła",
              "Krótsze hasła",
              "Wyłączenie HTTPS"
            ],
            answer: 0,
            explain: "Rate limiting/blokady, długie unikalne hasła i 2FA razem czynią brute-force niepraktycznym."
          }
        ]
      },
      {
        id: "a2",
        title: "Laboratorium: atak słownikowy",
        xp: 25,
        steps: [
          {
            type: "challenge",
            kind: "brute",
            title: "Zgadnij hasło konta admin",
            brief:
              "Ten panel <b>nie ma limitu prób</b>. Konto <span class='inline-code'>admin</span> używa bardzo popularnego hasła. Wypróbuj listę najczęstszych haseł.",
            hints: [
              "Nie musisz zgadywać na ślepo. Użyj przycisku „Atak słownikowy”, który po kolei próbuje najpopularniejszych haseł.",
              "Brak blokady po wielu błędnych próbach to właśnie podatność — każda próba jest darmowa.",
              "Hasło konta admin jest na liście TOP haseł. Uruchom słownik i obserwuj, które trafi."
            ]
          },
          {
            type: "quiz",
            q: "Serwer po 5 błędnych próbach blokuje konto na 15 minut. Jak nazywa się ta obrona?",
            options: ["Rate limiting / lockout", "Sharding", "Lazy loading", "Garbage collection"],
            answer: 0,
            explain: "Ograniczenie tempa prób (rate limiting) i blokady kont powstrzymują ataki zgadujące."
          }
        ]
      }
    ]
  },

  /* ------------------------------------------------------------------ */
  {
    id: "idor",
    title: "IDOR / Kontrola dostępu",
    short: "Podmień ID i zobacz cudze dane",
    icon: "🪪",
    color: "#1cb0f6",
    lessons: [
      {
        id: "i1",
        title: "Czym jest IDOR?",
        xp: 10,
        steps: [
          {
            type: "info",
            title: "Numer w adresie",
            html:
              "Adres <span class='inline-code'>/profil?user_id=1337</span> pokazuje Twój profil. Co, jeśli zmienisz numer na <span class='inline-code'>1</span>? Jeśli serwer <b>nie sprawdza</b>, czy to Twoje konto — zobaczysz cudze dane. To <b>IDOR</b> (Insecure Direct Object Reference)."
          },
          {
            type: "quiz",
            q: "Co jest przyczyną IDOR?",
            options: [
              "Brak sprawdzenia uprawnień do danego obiektu po stronie serwera",
              "Za długie hasło",
              "Zbyt szybki serwer",
              "Brak pliku robots.txt"
            ],
            answer: 0,
            explain: "Serwer pokazuje obiekt po samym ID, nie sprawdzając, czy użytkownik ma do niego prawo."
          }
        ]
      },
      {
        id: "i2",
        title: "Laboratorium: cudzy profil",
        xp: 25,
        steps: [
          {
            type: "challenge",
            kind: "idor",
            title: "Odczytaj dane innego użytkownika",
            brief:
              "Jesteś zalogowany jako użytkownik <b>1337</b>. API zwraca profil na podstawie parametru <span class='inline-code'>user_id</span>. Spróbuj odczytać konto administratora.",
            hints: [
              "Zauważ, że w adresie jest <span class='inline-code'>user_id=1337</span> — to Twój numer.",
              "Serwer nie sprawdza, czy numer należy do Ciebie. Co, jeśli wpiszesz inny?",
              "Administratorzy zwykle mają niskie ID. Spróbuj wpisać <span class='inline-code'>1</span>."
            ]
          },
          {
            type: "quiz",
            q: "Jak naprawić IDOR?",
            options: [
              "Sprawdzać po stronie serwera, czy zalogowany użytkownik ma prawo do danego obiektu",
              "Używać większych liczb jako ID",
              "Ukryć numer w adresie",
              "Dodać captcha do strony"
            ],
            answer: 0,
            explain: "Liczy się autoryzacja po stronie serwera (np. 'czy obiekt należy do tego usera'). Losowe UUID to tylko utrudnienie, nie obrona."
          }
        ]
      }
    ]
  },

  /* ------------------------------------------------------------------ */
  {
    id: "cmd",
    title: "Command Injection",
    short: "Doklej komendę do systemu",
    icon: "💻",
    color: "#ff4b4b",
    lessons: [
      {
        id: "c1",
        title: "Laboratorium: narzędzie ping",
        xp: 30,
        steps: [
          {
            type: "info",
            title: "Serwer uruchamia komendy",
            html:
              "Ten panel administracyjny 'pinguje' podany adres, uruchamiając w systemie: <span class='inline-code'>ping -c1 TU_WPIS</span>. Jeśli Twój wpis trafia prosto do powłoki (shell), możesz <b>dokleić własną komendę</b>."
          },
          {
            type: "challenge",
            kind: "cmd",
            title: "Wykonaj komendę systemową",
            brief:
              "Wpisz adres do pingowania, ale spróbuj dokleić do niego własną komendę systemu (np. <span class='inline-code'>id</span> albo <span class='inline-code'>whoami</span>).",
            hints: [
              "Twój tekst trafia na koniec komendy <span class='inline-code'>ping -c1 ...</span>.",
              "W powłoce można połączyć komendy znakami <span class='inline-code'>;</span> , <span class='inline-code'>&&</span> lub <span class='inline-code'>|</span>.",
              "Spróbuj: <span class='inline-code'>127.0.0.1; id</span> albo <span class='inline-code'>8.8.8.8 && whoami</span>."
            ]
          },
          {
            type: "quiz",
            q: "Jak zapobiec Command Injection?",
            options: [
              "Nie budować komend ze stringów — używać bezpiecznych API i walidować wejście (allowlist)",
              "Uruchamiać serwer jako root",
              "Dodać więcej pamięci RAM",
              "Zmienić port serwera"
            ],
            answer: 0,
            explain: "Unikaj powłoki: przekazuj argumenty jako listę do bezpiecznego API, waliduj wejście listą dozwolonych znaków i działaj z minimalnymi uprawnieniami."
          }
        ]
      }
    ]
  },

  /* ------------------------------------------------------------------ */
  {
    id: "path",
    title: "Path Traversal",
    short: "Wyjdź poza katalog",
    icon: "📁",
    color: "#58cc02",
    lessons: [
      {
        id: "p1",
        title: "Laboratorium: podgląd plików",
        xp: 30,
        steps: [
          {
            type: "info",
            title: "Pobieranie plików",
            html:
              "Serwer udostępnia pliki z jednego folderu: <span class='inline-code'>/var/www/files/TU_NAZWA</span>. Sekwencja <span class='inline-code'>../</span> oznacza 'katalog wyżej'. Jeśli serwer nie sprawdza nazwy, możesz 'wyjść' z folderu i czytać dowolny plik."
          },
          {
            type: "challenge",
            kind: "path",
            title: "Odczytaj /etc/passwd",
            brief:
              "Podgląd plików wkleja nazwę do ścieżki. Spróbuj wyjść poza folder i odczytać systemowy plik <span class='inline-code'>/etc/passwd</span>.",
            hints: [
              "Każde <span class='inline-code'>../</span> cofa Cię o jeden katalog w górę.",
              "Z folderu <span class='inline-code'>/var/www/files/</span> do korzenia <span class='inline-code'>/</span> trzeba cofnąć się kilka razy.",
              "Spróbuj: <span class='inline-code'>../../../../etc/passwd</span>."
            ]
          },
          {
            type: "quiz",
            q: "Jak bronić się przed Path Traversal?",
            options: [
              "Normalizować ścieżkę i sprawdzać, czy wynik nadal jest w dozwolonym folderze (allowlist)",
              "Zablokować znak kropki w całym internecie",
              "Zmienić nazwę folderu na losową",
              "Serwować pliki tylko nocą"
            ],
            answer: 0,
            explain: "Zamień na ścieżkę kanoniczną i odrzuć wszystko, co wychodzi poza bazowy katalog. Najlepiej mapuj dozwolone pliki po identyfikatorach."
          }
        ]
      }
    ]
  },

  /* ------------------------------------------------------------------ */
  {
    id: "boss",
    title: "Egzamin: Czerwona drużyna",
    short: "Sprawdź całą wiedzę",
    icon: "🏴",
    color: "#ce82ff",
    boss: true,
    lessons: [
      {
        id: "boss1",
        title: "Finałowe wyzwanie",
        xp: 60,
        steps: [
          {
            type: "info",
            title: "🏴 Finał",
            html:
              "Czas połączyć wszystko. Kilka pytań i jedno wyzwanie — pokaż, że rozumiesz zarówno <b>atak</b>, jak i <b>obronę</b>."
          },
          {
            type: "quiz",
            q: "Formularz odbija Twój tekst w HTML bez kodowania. Jaka to podatność?",
            options: ["XSS", "SQL Injection", "IDOR", "Path Traversal"],
            answer: 0,
            explain: "Odbity tekst w HTML bez escapowania = Cross-Site Scripting."
          },
          {
            type: "quiz",
            q: "Logowanie wpuszcza Cię po wpisaniu <span class='inline-code'>admin' --</span>. Co zawiodło?",
            options: ["Brak zapytań parametryzowanych (SQLi)", "Zbyt silne hasło", "Brak captcha", "Za duży serwer"],
            answer: 0,
            explain: "Dane trafiły do zapytania SQL jako kod — klasyczne SQL Injection."
          },
          {
            type: "quiz",
            q: "Zmiana <span class='inline-code'>?id=1337</span> na <span class='inline-code'>?id=1</span> pokazuje cudzy profil. Co naprawić?",
            options: ["Autoryzację dostępu do obiektu po stronie serwera", "Kolor przycisku", "Rozmiar czcionki", "Nazwę domeny"],
            answer: 0,
            explain: "To IDOR — brakuje sprawdzenia, czy obiekt należy do zalogowanego użytkownika."
          },
          {
            type: "challenge",
            kind: "cmd",
            title: "Ostatni cel: przejmij powłokę",
            brief:
              "Panel diagnostyczny znowu 'pinguje' host. Udowodnij wykonanie kodu, doklejając własną komendę.",
            hints: [
              "To ten sam wzorzec, co w module Command Injection.",
              "Połącz komendy znakiem <span class='inline-code'>;</span> lub <span class='inline-code'>&&</span>.",
              "Np. <span class='inline-code'>127.0.0.1; id</span>."
            ]
          }
        ]
      }
    ]
  }
];

/* ---------------------- Słownik pojęć ---------------------- */
const GLOSSARY = [
  { term: "SQL Injection (SQLi)", tags: ["baza", "wstrzyknięcie"], def: "Wstrzyknięcie fragmentu SQL przez dane wejściowe, zmieniające sens zapytania do bazy. Obrona: zapytania parametryzowane." },
  { term: "XSS", tags: ["przeglądarka", "javascript"], def: "Cross-Site Scripting — wykonanie obcego skryptu w przeglądarce ofiary. Obrona: kodowanie wyjścia (escaping) + CSP." },
  { term: "IDOR", tags: ["autoryzacja"], def: "Insecure Direct Object Reference — dostęp do cudzego obiektu przez podmianę identyfikatora. Obrona: kontrola dostępu po stronie serwera." },
  { term: "Command Injection", tags: ["system", "shell"], def: "Doklejenie komendy systemowej do polecenia budowanego ze stringów. Obrona: bezpieczne API zamiast powłoki + allowlista." },
  { term: "Path Traversal", tags: ["pliki"], def: "Użycie ../ do wyjścia poza dozwolony katalog i odczytu dowolnych plików. Obrona: normalizacja ścieżki + allowlista." },
  { term: "Brute-force / słownik", tags: ["hasła"], def: "Automatyczne zgadywanie haseł. Obrona: rate limiting, blokady, 2FA, silne hasła." },
  { term: "Prepared statement", tags: ["obrona", "baza"], def: "Zapytanie parametryzowane — dane są przekazywane osobno od kodu SQL, więc nigdy nie stają się kodem." },
  { term: "CSP", tags: ["obrona", "przeglądarka"], def: "Content-Security-Policy — nagłówek ograniczający, skąd ładują się skrypty. Druga warstwa obrony przed XSS." },
  { term: "Triada CIA", tags: ["podstawy"], def: "Poufność (Confidentiality), Integralność (Integrity), Dostępność (Availability) — trzy filary bezpieczeństwa." },
  { term: "Payload", tags: ["atak"], def: "Przygotowany ciąg znaków/kod, który wywołuje podatność (np. ' OR '1'='1)." },
  { term: "Rate limiting", tags: ["obrona"], def: "Ograniczenie liczby żądań/prób w czasie — utrudnia brute-force i nadużycia." },
  { term: "Hash + sól", tags: ["obrona", "hasła"], def: "Hasła przechowuje się jako jednokierunkowy skrót z losową solą (bcrypt/argon2), nie jako czysty tekst." }
];

/* ---------------------- Odznaki ---------------------- */
const BADGES = [
  { id: "first", ico: "🐣", name: "Pierwsza lekcja", test: (p) => Object.keys(p.done).length >= 1 },
  { id: "sqli", ico: "🗄️", name: "Mistrz SQLi", test: (p) => p.done["s2"] },
  { id: "xss", ico: "💥", name: "Łowca XSS", test: (p) => p.done["x2"] },
  { id: "idor", ico: "🪪", name: "Podmieniacz ID", test: (p) => p.done["i2"] },
  { id: "shell", ico: "💻", name: "Shell master", test: (p) => p.done["c1"] },
  { id: "streak3", ico: "🔥", name: "Seria 3 dni", test: (p) => p.streak >= 3 },
  { id: "boss", ico: "🏆", name: "Czerwona drużyna", test: (p) => p.done["boss1"] },
  { id: "all", ico: "🎓", name: "Ukończono kurs", test: (p) => COURSE.every((u) => u.lessons.every((l) => p.done[l.id])) }
];
