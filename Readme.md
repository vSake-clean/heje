# 🧭 Odkrywca – odkrywaj prawdziwy świat

Mapa świata ukryta pod delikatną mgłą, która rozwiewa się **tylko tam, gdzie naprawdę byłeś**.
Włącz odkrywanie, wyjdź na spacer, jedź rowerem albo w podróż – i patrz, jak na mapie
odsłaniają się ulice, parki i miasta, które poznałeś.

## 📱 Instalacja na Androidzie

1. Pobierz **[`apk/Odkrywca.apk`](apk/Odkrywca.apk)** na telefon
   (najnowszą wersję znajdziesz też w zakładce *Actions* → ostatni przebieg → artefakt `Odkrywca-apk`).
2. Otwórz plik i zezwól na instalację z nieznanych źródeł, gdy telefon o to zapyta.
3. Uruchom Odkrywcę, dotknij ▶ i zezwól na **lokalizację** oraz **powiadomienia**.
4. Gdy aplikacja zaproponuje „Pozwól działać w tle”, zgódź się – na telefonach Xiaomi, Huawei,
   Samsung itp. bez tego system potrafi usypiać aplikacje.

### Działanie w tle

Po włączeniu odkrywania startuje natywna **usługa pierwszoplanowa** Androida (z powiadomieniem
„🧭 Odkrywanie aktywne · przebyto X km”). Zapisuje pozycję GPS **także przy wyłączonym ekranie,
zminimalizowanej lub zamkniętej aplikacji**. Punkty trafiają do bufora w pamięci telefonu,
a po otwarciu aplikacji mapa od razu odsłania całą przebytą trasę.
Odkrywanie zatrzymasz przyciskiem ⏹ w aplikacji albo „Zatrzymaj” w powiadomieniu.

Wymaga Androida 7.0 lub nowszego.

### Budowanie APK samodzielnie

Wymagane: Node.js 22, JDK 21, Android SDK (platforma 36).

```bash
npm ci
npm run apk        # → android/app/build/outputs/apk/release/app-release.apk
```

APK buduje się też automatycznie w GitHub Actions przy każdym pushu
(`.github/workflows/android.yml`), a wypchnięcie tagu `v*` tworzy wydanie z plikiem APK.
Wszystkie wersje podpisywane są tym samym kluczem (`android/app/odkrywca.keystore`),
więc nowe APK instaluje się jako aktualizacja – bez utraty odkryć.

## 🌐 Wersja w przeglądarce

```bash
npm run serve      # http://localhost:8080
```

## Funkcje

**Mapa i mgła**
- 🌫️ Mgła wojny z miękkimi krawędziami i proceduralną teksturą chmur, przesuwającą się razem z mapą
- 🎨 7 motywów mgły (Poranna mgła, Lawenda, Pergamin, Obłoki, Mięta, Noc, Atrament) i regulowana gęstość
- 🗺️ 6 stylów mapy: Ulice, Klasyczna (OSM), Jasna, Nocna, Satelita, Teren
- 👀 Podgląd mapy bez mgły jednym przyciskiem
- 🔍 Wyszukiwarka miejsc

**Odkrywanie**
- 🚶 Śledzenie GPS na żywo z filtrem dokładności, wykrywaniem skoków (np. lot samolotem) i interpolacją trasy
- 📏 Regulowany promień odsłaniania (20–250 m)
- 🖌️ **Pędzel** – odsłoń miejsca, w których byłeś dawniej (z wyborem daty), oraz **gumka**
- 📥 Import tras: **GPX, KML, GeoJSON, historia lokalizacji Google (Takeout)** – także przeciągnij-i-upuść
- 🎮 Symulowany spacer (demo), aby zobaczyć działanie bez wychodzenia z domu
- 🛰️ Na Androidzie: nagrywanie w tle przy wyłączonym ekranie (usługa pierwszoplanowa)

**Postępy**
- 📊 Statystyki: odkryta powierzchnia (km² i % Ziemi), dystans, seria dni, odkrycia dziś, najdalszy punkt, wykres 30 dni
- ⬆️ Poziomy i tytuły (od Turysty do Legendy) za punkty doświadczenia
- 🏆 28 odznak (powierzchnia, dystans, kraje, miasta, serie dni, nocny marek, ranny ptaszek…)
- 🌍 Dziennik miejsc – automatycznie rozpoznawane odwiedzone kraje i miejscowości (OpenStreetMap Nominatim)
- 📍 Wspomnienia – własne znaczniki z emoji i notatką (prawy klik / przytrzymanie mapy)
- ⏳ Podróż w czasie – suwak i animacja pokazujące, jak odkrywałeś świat dzień po dniu

**Dane**
- 🔒 Wszystko zapisuje się lokalnie na urządzeniu (localStorage)
- 💾 Eksport / import kopii zapasowej (JSON) i eksport tras do GPX
- 📶 Działa offline (service worker buforuje aplikację i ostatnio oglądane kafelki mapy)

## Struktura

```
www/                         – aplikacja (HTML/CSS/JS), wspólna dla Androida i przeglądarki
  index.html, css/style.css
  js/geo.js                  – geometria, formatowanie, style map i mgły, parsery GPX/KML/GeoJSON/Google
  js/store.js                – siatka odkrytych punktów, liczenie powierzchni, statystyki, zapis
  js/fog.js                  – warstwa mgły Leaflet rysowana na canvasie
  js/achievements.js         – definicje odznak
  js/app.js                  – logika: GPS, pędzel, panele, import/eksport, podróż w czasie, most do Androida
  vendor/leaflet/            – biblioteka map (lokalnie, działa offline)
android/                     – projekt Android (Capacitor)
  app/src/main/java/pl/odkrywca/app/
    TrackingService.java     – usługa w tle nagrywająca GPS
    PointStore.java          – bufor punktów zapisanych w tle
    OdkrywcaPlugin.java      – most JS ↔ Android (start/stop, odbiór punktów, uprawnienia, bateria)
apk/Odkrywca.apk             – gotowa aplikacja do instalacji
```

## Uwagi

- Wszystkie dane zostają na telefonie. Rób kopie zapasowe (Ustawienia → Eksportuj kopię), zwłaszcza przed odinstalowaniem.
- Mapy wymagają internetu (ostatnio oglądane kafelki są buforowane). Mapy: © OpenStreetMap, Esri, OpenTopoMap.
  Rozpoznawanie miejsc: Nominatim (OpenStreetMap).
