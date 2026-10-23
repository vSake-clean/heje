# Odkrywca

Mapa świata zakryta mgłą. Odsłania się tylko w miejscach, w których fizycznie byłeś z telefonem.

Aplikacja celowo daje mało możliwości:

- zapis lokalizacji działa cały czas i nie ma wyłącznika,
- nie da się ręcznie dopisać ani wymazać odwiedzonych miejsc (nie ma pędzla, gumki ani importu tras),
- promień odsłaniania jest stały: 50 m od każdego odczytu GPS dokładniejszego niż 60 m,
- odczyty z aplikacji do podrabiania lokalizacji są odrzucane.

## Instalacja na Androidzie

1. Pobierz [`apk/Odkrywca.apk`](apk/Odkrywca.apk) na telefon. Najnowsza wersja jest też w zakładce Actions jako artefakt `Odkrywca-apk`.
2. Otwórz plik i zezwól na instalację z nieznanych źródeł.
3. Po uruchomieniu nadaj dostęp do lokalizacji, a w kolejnym kroku wybierz **Zezwalaj zawsze**.
4. Wyłącz dla aplikacji optymalizację baterii, gdy aplikacja o to poprosi.

Wymagany Android 7.0 lub nowszy.

### Jak działa zapis w tle

Zapis prowadzi natywna usługa pierwszoplanowa Androida, widoczna jako stałe powiadomienie
„Zapis trasy włączony”. Działa przy wyłączonym ekranie i po zamknięciu aplikacji.
Uruchamia się sama po włączeniu telefonu i po aktualizacji aplikacji
(wymaga zgody na lokalizację „zawsze”). Jeśli system ją zatrzyma, aplikacja wznawia ją przy następnym otwarciu.

Punkty trafiają do bufora w pamięci telefonu. Po otwarciu aplikacji mapa odsłania całą trasę.

## Co jest w aplikacji

- mapa z mgłą i trzy rodzaje podkładu: plan, teren, satelita,
- odkryta powierzchnia i dystans (łącznie i dziś), seria dni, wykres z 30 dni,
- historia: suwak pokazujący stan mapy w wybranym dniu,
- odwiedzone kraje i miejscowości rozpoznawane automatycznie (OpenStreetMap Nominatim, można wyłączyć),
- odznaki z jawnymi progami (powierzchnia, dystans, miejscowości, kraje, serie dni),
- wyszukiwarka miejsc (tylko do przesuwania mapy).

Dane są wyłącznie na urządzeniu.

## Budowanie

Wymagane: Node.js 22, JDK 21, Android SDK (platforma 36).

```bash
npm ci
npm run apk        # android/app/build/outputs/apk/release/app-release.apk
npm run serve      # wersja w przeglądarce: http://localhost:8080
```

APK buduje się też w GitHub Actions przy każdym pushu (`.github/workflows/android.yml`),
a tag `v*` tworzy wydanie z plikiem APK. Wszystkie wersje są podpisane tym samym kluczem
(`android/app/odkrywca.keystore`), więc nowe APK instaluje się jako aktualizacja bez utraty danych.

## Struktura

```
www/                          aplikacja (HTML, CSS, JS), wspólna dla Androida i przeglądarki
  js/geo.js                   geometria, formatowanie, mapy podkładowe
  js/store.js                 siatka odkrytych punktów, powierzchnia, statystyki, zapis
  js/fog.js                   warstwa mgły rysowana na canvasie
  js/achievements.js          odznaki
  js/app.js                   logika aplikacji i most do Androida
  vendor/leaflet/             biblioteka map
android/app/src/main/java/pl/odkrywca/app/
  TrackingService.java        usługa zapisująca GPS w tle
  BootReceiver.java           wznowienie zapisu po restarcie telefonu i aktualizacji
  PointStore.java             bufor punktów zapisanych w tle
  OdkrywcaPlugin.java         most JS i Android: uprawnienia, odbiór punktów, bateria
apk/Odkrywca.apk              gotowa aplikacja
```

Mapy: OpenStreetMap, Esri, OpenTopoMap.
