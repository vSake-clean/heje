# 🛡️ HackLingo — kurs bezpieczeństwa webowego w stylu Duolingo

Interaktywna aplikacja (telefon + komputer) do nauki **bezpieczeństwa aplikacji
webowych**. Działa jak Duolingo: ścieżka lekcji, XP, serie dni, serca, odznaki —
ale zamiast języków uczysz się, **jak działają podatności i jak je naprawiać**.

Każde laboratorium jest w **100% symulowane w przeglądarce**. Aplikacja niczego
nie atakuje w prawdziwym świecie, nie wysyła żadnych danych i działa offline.

## ✨ Co w środku

- **Ścieżka nauki** z modułami:
  - 🌐 Podstawy sieci + etyka (triada CIA)
  - 🗄️ SQL Injection (obejście logowania, wyciek danych UNION)
  - 💬 Cross-Site Scripting (XSS)
  - 🔑 Słabe uwierzytelnianie (atak słownikowy / brute-force)
  - 🪪 IDOR / kontrola dostępu
  - 💻 Command Injection
  - 📁 Path Traversal
  - 🏴 Egzamin końcowy („Czerwona drużyna”)
- **Interaktywne laboratoria** — symulowany panel logowania, księga gości,
  narzędzie ping, podgląd plików itd. Próbujesz różnych payloadów i widzisz efekt.
- **Przycisk „💡 Podpowiedź”** — progresywne wskazówki, które delikatnie
  naprowadzają, **w którym kierunku iść**, a nie od razu dają rozwiązanie.
- **Obrona przy każdej lekcji** — po wykorzystaniu podatności dowiadujesz się,
  jak ją naprawić (prepared statements, output encoding, autoryzacja itd.).
- **Grywalizacja** — XP, poziomy, seria dni 🔥, serca ❤️, tryb Treningu, odznaki 🏆.
- **Słownik** pojęć (atak + obrona).
- **PWA** — można zainstalować na telefonie i używać bez internetu.

## ▶️ Jak uruchomić

To statyczna strona — nie wymaga instalacji ani serwera backendowego.

**Najprościej:** otwórz plik `index.html` w przeglądarce.

**Zalecane (żeby działał service worker / PWA)** — uruchom lokalny serwer:

```bash
# Python 3
python3 -m http.server 8000
# następnie otwórz:  http://localhost:8000
```

lub:

```bash
npx serve .
```

### Instalacja na telefonie
Otwórz adres w Chrome/Safari na telefonie → menu → **„Dodaj do ekranu
głównego”**. Aplikacja zadziała jak natywna, również offline.

## 🗂️ Struktura

```
index.html              # szkielet + zakładki
css/styles.css          # wygląd (jasny/ciemny, responsywny)
js/data.js              # treść kursu, słownik, odznaki
js/labs.js              # symulowane laboratoria podatności
js/app.js               # silnik: widoki, postęp, grywalizacja
manifest.webmanifest    # PWA
sw.js                   # tryb offline
icon.svg                # ikona
```

Postęp zapisywany jest lokalnie w `localStorage` (nic nie opuszcza urządzenia).

## ⚖️ Nota etyczna

HackLingo powstał w celach **edukacyjnych i obronnych**. Zrozumienie ataków jest
podstawą skutecznej ochrony. Zdobytą wiedzę wykorzystuj **wyłącznie legalnie**:
na tym poligonie, na własnych systemach lub tam, gdzie masz **wyraźną zgodę**
właściciela. Nieautoryzowany dostęp do cudzych systemów jest przestępstwem.
