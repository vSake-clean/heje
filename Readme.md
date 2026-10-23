# 🧭 Odkrywca – odkrywaj prawdziwy świat

Mapa świata ukryta pod delikatną mgłą, która rozwiewa się **tylko tam, gdzie naprawdę byłeś**.
Włącz odkrywanie, wyjdź na spacer, jedź rowerem albo w podróż – i patrz, jak na mapie
odsłaniają się ulice, parki i miasta, które poznałeś.

## Jak uruchomić

To aplikacja webowa (PWA) bez żadnej kompilacji.

```bash
# w katalogu projektu:
npx http-server -p 8080 .      # albo: python3 -m http.server 8080
```

Otwórz `http://localhost:8080`. Lokalizacja GPS działa tylko przez `https://` lub `localhost`,
więc aby używać jej na telefonie, opublikuj katalog np. na GitHub Pages / Netlify
i otwórz go w przeglądarce telefonu → „Dodaj do ekranu głównego”.
Aplikacja działa wtedy jak natywna (pełny ekran, ikona, działanie offline).

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
- 💡 Ekran nie gaśnie podczas odkrywania (Wake Lock API)

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
index.html            – układ interfejsu
css/style.css         – wygląd (glassmorphism, tryb jasny/ciemny, mobile-first)
js/geo.js             – geometria, formatowanie, style map i mgły, parsery GPX/KML/GeoJSON/Google
js/store.js           – model danych: siatka odkrytych punktów, liczenie powierzchni, statystyki, zapis
js/fog.js             – warstwa mgły Leaflet rysowana na canvasie
js/achievements.js    – definicje odznak
js/app.js             – logika aplikacji: GPS, pędzel, panele, import/eksport, podróż w czasie
sw.js                 – service worker (offline)
```

## Uwagi

- Przeglądarki zwykle wstrzymują GPS, gdy aplikacja działa w tle lub ekran jest wyłączony –
  dlatego podczas odkrywania ekran pozostaje włączony. Trasy nagrane w innej aplikacji
  (Strava, Komoot, Google Maps) możesz później zaimportować jako GPX.
- Mapy: © OpenStreetMap, Esri, OpenTopoMap. Rozpoznawanie miejsc: Nominatim (OpenStreetMap).
