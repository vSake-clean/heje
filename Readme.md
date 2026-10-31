# 🛡️ HackLingo — kurs bezpieczeństwa webowego (aplikacja na Androida)

Aplikacja mobilna (Android **APK/AAB**) do nauki **bezpieczeństwa aplikacji
webowych** w stylu Duolingo: ścieżka poziomów, XP, **passa (seria dni)**, serca,
odznaki — ale zamiast języków uczysz się, **jak działają podatności i jak je
naprawiać**.

Wszystkie laboratoria są w **100% symulowane** wewnątrz aplikacji. Nic nie jest
wysyłane do sieci, nic nie atakuje prawdziwych systemów, aplikacja **działa w
pełni offline** (nie wymaga nawet uprawnienia INTERNET).

## 📦 Zawartość (dużo poziomów)

- **56 poziomów** w **14 modułach**, **82 pytania** i **8 interaktywnych laboratoriów**:
  - 🌐 Podstawy sieci + etyka (CIA)
  - 🔎 Rozpoznanie (recon)
  - 🗄️ SQL Injection (logowanie, UNION, blind, obrona)
  - 💬 Cross-Site Scripting (typy, CSP, obrona)
  - 🔑 Uwierzytelnianie (brute-force, sesje, JWT, 2FA)
  - 🪪 IDOR / kontrola dostępu
  - 💻 Command Injection
  - 📁 Path Traversal / LFI
  - 🎭 CSRF · 🕳️ SSRF · 📤 Upload plików
  - 🔐 Kryptografia i nagłówki bezpieczeństwa
  - 🧰 Metodyka (OWASP Top 10, proxy, responsible disclosure)
  - 🏴 Dwa egzaminy końcowe
- **Przycisk „💡 Podpowiedź”** — progresywne wskazówki, które delikatnie
  naprowadzają, *w którym kierunku iść*, a dopiero na końcu podają rozwiązanie.
- **Sekcja „obrona”** przy każdym laboratorium — jak podatność naprawić.
- **Grywalizacja**: XP, poziomy, passa 🔥, serca ❤️, tryb Treningu, odznaki 🏆.
- **Słownik** pojęć, profil, zapis postępu lokalnie (localStorage).

## 📱 Instalacja APK na telefonie

1. Skopiuj plik `HackLingo-1.0.0.apk` na telefon (lub pobierz).
2. Otwórz go — Android poprosi o zgodę na „instalację z nieznanych źródeł”
   (to normalne przy instalacji poza sklepem). Zezwól dla tej aplikacji.
3. Zainstaluj i uruchom „HackLingo”.

> Minimalna wersja Androida: **8.0 (API 26)**. Aplikacja jest podpisana.

## 🏗️ Budowanie ze źródeł

Wymagania: JDK 17+, Android SDK (platform android-34, build-tools 34.0.0).

```bash
cd android
./sync-web.sh                 # skopiuj treść web do assets/ (po zmianach w kursie)
./gradlew assembleDebug       # APK debug  -> app/build/outputs/apk/debug/
./gradlew assembleRelease     # APK release (podpisany, jeśli jest keystore.properties)
./gradlew bundleRelease       # AAB do Google Play
```

### Podpisywanie release
Build release podpisuje się automatycznie, jeśli w katalogu `android/` znajduje
się plik `keystore.properties` (niecommitowany) wskazujący Twój keystore:

```properties
storeFile=moj-klucz.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

Klucz tworzysz raz:
```bash
keytool -genkeypair -v -keystore moj-klucz.keystore -alias hacklingo \
  -keyalg RSA -keysize 2048 -validity 10000
```
Bez tego pliku build release powstanie niepodpisany (do podpisania później).

## 🏬 Publikacja w sklepach

- **Google Play** — wgraj `HackLingo-1.0.0.aab` w Google Play Console (zalecany
  jest mechanizm Play App Signing; wtedy Twój klucz służy jako upload key).
  Trzeba podać m.in. politykę prywatności i ikony/zrzuty — aplikacja nie zbiera
  danych i działa offline, co upraszcza formularz.
- **Apple App Store (iOS)** — wymaga komputera **Mac z Xcode** oraz konta Apple
  Developer; nie da się tego zbudować w tym (linuksowym) środowisku. Tę samą
  aplikację webową można opakować pod iOS (np. Capacitor / WKWebView) na macu.

## 🗂️ Struktura

```
index.html, css/, js/        # aplikacja webowa (źródło treści kursu)
  js/data.js                 # 56 poziomów, słownik, odznaki
  js/labs.js                 # symulowane laboratoria podatności
  js/app.js                  # silnik: widoki, postęp, grywalizacja
android/                     # natywny projekt Androida (WebView, offline)
  app/src/main/assets/www/   # kopia aplikacji webowej pakowana do APK
  sync-web.sh                # synchronizacja web -> assets
```

## ⚖️ Nota etyczna

HackLingo powstał w celach **edukacyjnych i obronnych**. Zrozumienie ataków jest
podstawą skutecznej ochrony. Zdobytą wiedzę wykorzystuj **wyłącznie legalnie**:
na tym poligonie, na własnych systemach lub tam, gdzie masz **wyraźną zgodę**
właściciela. Nieautoryzowany dostęp do cudzych systemów jest przestępstwem.
