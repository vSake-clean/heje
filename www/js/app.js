/* Odkrywca: logika aplikacji.
 * Jedynym źródłem odkryć jest GPS. Nie ma ręcznego odsłaniania, wymazywania,
 * importu ani wyłącznika zapisu. */
(function () {
  'use strict';

  const G = window.Geo;
  const S = window.Store;
  const $ = sel => document.querySelector(sel);
  const $$ = sel => Array.from(document.querySelectorAll(sel));
  const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  const MAX_ACCURACY = 60;   // gorsze odczyty GPS są pomijane
  const MAX_SPEED = 85;      // m/s; szybciej = przeskok (np. samolot), bez łączenia linią
  const MAX_GAP = 3000;      // m; dłuższych przerw nie łączymy linią

  S.load();
  const settings = S.state.settings;

  /* Android: zapis prowadzi natywna usługa działająca w tle */
  const Cap = window.Capacitor;
  const isNativeApp = !!(Cap && Cap.isNativePlatform && Cap.isNativePlatform());
  const native = isNativeApp && Cap.isPluginAvailable && Cap.isPluginAvailable('Odkrywca')
    ? {
      call: (method, opts) => Cap.nativePromise('Odkrywca', method, opts || {}),
      on: (event, cb) => Cap.addListener('Odkrywca', event, cb)
    }
    : null;

  /* ======================= MAPA ======================= */

  const startView = S.state.lastView || { lat: 52.07, lng: 19.48, zoom: 6 };
  const map = L.map('map', {
    zoomControl: false,
    worldCopyJump: true,
    minZoom: 2,
    maxZoom: 19,
    zoomSnap: 0.25,
    preferCanvas: true
  }).setView([startView.lat, startView.lng], startView.zoom);
  map.attributionControl.setPrefix(false);

  let baseLayer = null;
  function setBaseMap(id) {
    if (!G.BASE_MAPS[id]) id = 'streets';
    const def = G.BASE_MAPS[id];
    if (baseLayer) map.removeLayer(baseLayer);
    baseLayer = L.tileLayer(def.url, {
      attribution: def.attribution, subdomains: def.subdomains || 'abc',
      maxZoom: 19, maxNativeZoom: def.maxZoom, crossOrigin: true
    }).addTo(map);
    settings.baseMap = id;
    S.save();
    renderLayers();
  }

  const fog = new window.FogLayer({ theme: G.FOG, opacity: 0.92 });
  setBaseMap(settings.baseMap);
  fog.addTo(map);

  const trackRenderer = L.canvas({ padding: 0.3 });
  const trackLayer = L.layerGroup().addTo(map);
  const liveTrack = L.polyline([], { color: '#2f5d50', weight: 3, opacity: 0.9, renderer: trackRenderer, interactive: false });

  function renderTracks() {
    trackLayer.clearLayers();
    const lines = [];
    Object.keys(S.state.tracks).forEach(d => S.state.tracks[d].forEach(seg => { if (seg.length > 1) lines.push(seg); }));
    if (lines.length) {
      L.polyline(lines, { color: '#2f5d50', weight: 2, opacity: 0.45, renderer: trackRenderer, interactive: false }).addTo(trackLayer);
    }
    liveTrack.addTo(trackLayer);
  }

  const meIcon = L.divIcon({ className: 'me', html: '<span class="me-dir"></span><span class="me-dot"></span>', iconSize: [20, 20] });
  const meMarker = L.marker([0, 0], { icon: meIcon, interactive: false, keyboard: false, zIndexOffset: 1000 });
  const meAccuracy = L.circle([0, 0], { radius: 10, color: '#2b6cb0', weight: 1, opacity: 0.35, fillOpacity: 0.06, interactive: false });
  let myPos = null;
  let follow = true;

  function showMe(lat, lon, acc, heading) {
    myPos = { lat, lon };
    meMarker.setLatLng([lat, lon]);
    meAccuracy.setLatLng([lat, lon]).setRadius(acc || 10);
    if (!map.hasLayer(meMarker)) { meMarker.addTo(map); meAccuracy.addTo(map); }
    const el = meMarker.getElement();
    if (el) {
      const d = el.querySelector('.me-dir');
      const has = heading != null && isFinite(heading) && heading >= 0;
      d.style.display = has ? 'block' : 'none';
      if (has) d.style.transform = `rotate(${heading}deg)`;
    }
  }

  map.on('moveend', () => {
    const c = map.getCenter();
    S.state.lastView = { lat: c.lat, lng: c.lng, zoom: map.getZoom() };
    S.save();
  });
  map.on('dragstart', () => setFollow(false));

  function setFollow(v) {
    follow = v;
    $('#btnLocate').classList.toggle('on', v);
  }

  /* ======================= KOMUNIKATY ======================= */

  function toast(text, ms) {
    const el = document.createElement('div');
    el.className = 'toast';
    el.textContent = text;
    const box = $('#toasts');
    box.appendChild(el);
    while (box.children.length > 3) box.firstElementChild.remove();
    setTimeout(() => el.remove(), ms || 3500);
    return el;
  }

  S.on(type => {
    if (type === 'saveError') toast('Brak miejsca w pamięci telefonu. Zwolnij miejsce, żeby nie stracić odkryć.', 8000);
  });

  /* ======================= PODSUMOWANIE ======================= */

  let hudTimer = null;
  function updateSummary() {
    const today = G.dayNumber();
    $('#sumArea').textContent = G.formatArea(S.area);
    $('#sumToday').textContent = 'dziś ' + G.formatDistance(S.state.stats.activeDays[today] || 0);
    if (panelOpen) renderActivePane();
  }
  function scheduleSummary() {
    if (hudTimer) return;
    hudTimer = setTimeout(() => { hudTimer = null; updateSummary(); checkAchievements(); }, 600);
  }

  function setStatus(text, state) {
    $('#statusText').textContent = text;
    $('#status').dataset.state = state || 'ok';
  }

  /* ======================= ODZNAKI ======================= */

  function achievementStats() {
    return {
      area: S.area,
      distance: S.state.stats.distance,
      maxFromHome: S.state.stats.maxFromHome,
      cities: Object.keys(S.state.places.cities).length,
      countries: Object.keys(S.state.places.countries).length,
      bestStreak: S.bestStreak(),
      nightOwl: S.state.stats.nightOwl,
      earlyBird: S.state.stats.earlyBird
    };
  }

  function checkAchievements(silent) {
    const st = achievementStats();
    const got = S.state.achievements;
    let changed = false;
    window.ACHIEVEMENTS.forEach(a => {
      if (!got[a.id] && a.value(st) >= a.goal) {
        got[a.id] = Date.now();
        changed = true;
        if (!silent) toast('Odznaka: ' + a.name, 5000);
      }
    });
    if (changed) S.save();
  }

  /* ======================= MIEJSCOWOŚCI ======================= */

  const geocodeFailed = new Set();
  let geocodeBusy = false;
  function geocodePump() {
    if (!settings.geocode || geocodeBusy || !navigator.onLine) return;
    let target = null;
    for (const b of S.buckets.values()) {
      if (b.pts.length && !S.state.geocoded[b.key] && !geocodeFailed.has(b.key)) { target = b; break; }
    }
    if (!target) return;
    geocodeBusy = true;
    const p = target.pts[Math.floor(target.pts.length / 2)];
    const url = `https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=10&addressdetails=1&accept-language=pl&lat=${p[0].toFixed(5)}&lon=${p[1].toFixed(5)}`;
    fetch(url, { headers: { 'Accept': 'application/json' } })
      .then(r => r.ok ? r.json() : Promise.reject(r.status))
      .then(data => {
        S.state.geocoded[target.key] = 1;
        registerPlace(data, p);
        S.save();
      })
      .catch(() => geocodeFailed.add(target.key))
      .finally(() => { geocodeBusy = false; });
  }
  setInterval(geocodePump, 1200);

  function registerPlace(data, p) {
    const a = data && data.address;
    if (!a) return;
    const cc = (a.country_code || '').toUpperCase();
    const places = S.state.places;
    if (cc && !places.countries[cc]) {
      places.countries[cc] = { name: a.country || cc, first: p[2] };
      if (Object.keys(places.countries).length > 1) toast('Nowy kraj: ' + (a.country || cc), 5000);
    }
    const name = a.city || a.town || a.village || a.hamlet || a.municipality || a.suburb || a.county;
    if (name) {
      const key = cc + '|' + name;
      if (!places.cities[key]) {
        places.cities[key] = { name, cc, region: a.state || a.county || '', first: p[2], lat: p[0], lon: p[1] };
        toast('Nowa miejscowość: ' + name);
      }
    }
    scheduleSummary();
  }

  /* ======================= ODCZYTY GPS ======================= */

  let last = null;          // ostatni przyjęty odczyt
  let newSegment = true;

  function handleFix(lat, lon, acc, time, heading, quiet) {
    if (!quiet) {
      showMe(lat, lon, acc, heading);
      if (follow) {
        if (map.getZoom() < 14) map.setView([lat, lon], 16);
        else map.panTo([lat, lon], { animate: true, duration: 0.5 });
      }
    }
    if (!(acc <= MAX_ACCURACY)) return;

    const day = G.dayNumber(time);
    let added = 0;
    if (last) {
      const d = G.haversine(last.lat, last.lon, lat, lon);
      if (d < Math.max(4, acc * 0.25)) return;
      const dt = Math.max(1, (time - last.time) / 1000);
      if (d / dt < MAX_SPEED && d < MAX_GAP && time > last.time) {
        S.addDistance(d, day);
        added = S.revealLine(last.lat, last.lon, lat, lon, day);
        S.addTrackPoint(lat, lon, newSegment);
      } else {
        added = S.addPoint(lat, lon, day) ? 1 : 0;
        S.addTrackPoint(lat, lon, true);
      }
    } else {
      added = S.addPoint(lat, lon, day) ? 1 : 0;
      S.addTrackPoint(lat, lon, true);
    }
    newSegment = false;
    last = { lat, lon, time };
    liveTrack.addLatLng([lat, lon]);

    const h = new Date(time).getHours();
    if (h < 4) S.state.stats.nightOwl = true;
    if (h >= 5 && h < 7) S.state.stats.earlyBird = true;

    if (added) fog.redraw();
    S.save();
    scheduleSummary();
  }

  /* ---------- Android ---------- */

  let draining = false, drainAgain = false;
  function drainNative() {
    if (!native) return;
    if (draining) { drainAgain = true; return; }
    draining = true;
    native.call('drain').then(r => {
      const pts = r.points || [];
      pts.forEach((p, i) => handleFix(p[1], p[2], p[3], p[0], p[4], i < pts.length - 1));
      if (pts.length) S.saveNow();
    }).catch(() => {}).finally(() => {
      draining = false;
      if (drainAgain) { drainAgain = false; drainNative(); }
    });
  }

  function applyNativeStatus(st) {
    if (!st.location) {
      showGate('Bez dostępu do lokalizacji aplikacja nie może działać.');
      setStatus('Brak dostępu do lokalizacji', 'off');
      return;
    }
    hideGate();
    if (!st.gpsEnabled) setStatus('Lokalizacja w telefonie jest wyłączona', 'warn');
    else if (!st.tracking) setStatus('Uruchamiam zapis', 'warn');
    else setStatus('Zapis włączony', 'ok');
    nativeStatus = st;
  }
  let nativeStatus = null;

  function startNative() {
    return native.call('start').then(st => {
      applyNativeStatus(st);
      drainNative();
      if (st.location && !st.background && !S.state.askedBackground) askBackground();
      else if (st.location && !st.batteryUnrestricted && !S.state.askedBattery) askBattery();
    }).catch(() => {
      showGate('Bez dostępu do lokalizacji aplikacja nie może działać. Jeśli okno zgody się nie pojawia, nadaj uprawnienie w ustawieniach aplikacji.');
      setStatus('Brak dostępu do lokalizacji', 'off');
    });
  }

  function askBackground() {
    S.state.askedBackground = true;
    S.save();
    openModal(`
      <h2>Lokalizacja przez cały czas</h2>
      <p>Żeby zapis wracał sam po ponownym uruchomieniu telefonu, wybierz w następnym kroku opcję <b>Zezwalaj zawsze</b>.</p>
      <div class="modal-actions"><button class="btn primary" id="bgOk">Dalej</button></div>`);
    $('#bgOk').onclick = () => {
      closeModal();
      native.call('requestBackground').then(st => {
        applyNativeStatus(st);
        if (!st.batteryUnrestricted && !S.state.askedBattery) askBattery();
      }).catch(() => {});
    };
  }

  function askBattery() {
    S.state.askedBattery = true;
    S.save();
    openModal(`
      <h2>Oszczędzanie baterii</h2>
      <p>Część telefonów usypia aplikacje działające w tle. Wyłącz dla Odkrywcy optymalizację baterii, żeby żaden spacer nie przepadł.</p>
      <div class="modal-actions"><button class="btn primary" id="batOk">Dalej</button></div>`);
    $('#batOk').onclick = () => { closeModal(); native.call('openBatterySettings'); };
  }

  if (native) {
    let liveTimer = null;
    native.on('location', () => {
      if (document.visibilityState !== 'visible' || liveTimer) return;
      liveTimer = setTimeout(() => { liveTimer = null; drainNative(); }, 800);
    });
    native.on('status', e => {
      // system zatrzymał usługę: od razu ją przywracamy
      if (!e.tracking) setTimeout(startNative, 1000);
      else native.call('status').then(applyNativeStatus).catch(() => {});
    });
    Cap.addListener('App', 'backButton', () => {
      if ($('#modal').classList.contains('open')) return;
      if (panelOpen) closePanel();
      else if (document.body.classList.contains('time-travel')) closeTimeTravel();
      else if ($('#layersPop').classList.contains('open')) $('#layersPop').classList.remove('open');
      else if ($('#searchBox').classList.contains('open')) $('#searchBox').classList.remove('open');
      else Cap.nativePromise('App', 'minimizeApp', {}).catch(() => {});
    });
  }

  /* ---------- przeglądarka ---------- */

  let watchId = null;
  function startBrowser() {
    if (!('geolocation' in navigator)) {
      setStatus('Ta przeglądarka nie udostępnia lokalizacji', 'off');
      return;
    }
    if (watchId != null) navigator.geolocation.clearWatch(watchId);
    setStatus('Czekam na sygnał GPS', 'warn');
    watchId = navigator.geolocation.watchPosition(pos => {
      hideGate();
      setStatus('Zapis włączony, dopóki strona jest otwarta', 'ok');
      const c = pos.coords;
      handleFix(c.latitude, c.longitude, c.accuracy, pos.timestamp || Date.now(), c.heading);
    }, err => {
      if (err.code === 1) {
        showGate('Przeglądarka zablokowała dostęp do lokalizacji. Zezwól na niego w ustawieniach strony.');
        setStatus('Brak dostępu do lokalizacji', 'off');
      } else {
        setStatus('Brak sygnału GPS', 'warn');
      }
    }, { enableHighAccuracy: true, maximumAge: 2000, timeout: 30000 });
  }

  function startRecording() {
    if (!native) return startBrowser();
    // bez zgody najpierw pokazujemy wyjaśnienie; systemowe okno dopiero po dotknięciu przycisku
    native.call('status').then(st => {
      if (st.location) startNative();
      else applyNativeStatus(st);
    }).catch(() => startNative());
  }

  /* ---------- ekran zgody ---------- */

  function showGate(reason) {
    $('#gateReason').textContent = reason || '';
    $('#gate').hidden = false;
  }
  function hideGate() { $('#gate').hidden = true; }
  let gateTries = 0;
  $('#gateBtn').addEventListener('click', () => {
    if (!native) return startBrowser();
    // po odmowie Android nie pokazuje już okna zgody, więc za drugim razem otwieramy ustawienia
    if (++gateTries > 1) native.call('openAppSettings');
    else startNative();
  });

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
      if (native) native.call('status').then(st => { applyNativeStatus(st); if (!st.tracking && st.location) startNative(); }).catch(() => {});
      drainNative();
    } else {
      S.saveNow();
    }
  });
  window.addEventListener('pagehide', () => S.saveNow());

  $('#btnLocate').addEventListener('click', () => {
    setFollow(true);
    if (myPos) map.flyTo([myPos.lat, myPos.lon], Math.max(map.getZoom(), 16), { duration: 0.6 });
    else toast('Czekam na pierwszy odczyt GPS');
  });

  /* ======================= OKNO DIALOGOWE ======================= */

  function openModal(html) {
    $('#modalContent').innerHTML = html;
    $('#modal').classList.add('open');
  }
  function closeModal() { $('#modal').classList.remove('open'); }

  /* ======================= PANEL ======================= */

  let panelOpen = false, activeTab = 'stats';
  function openPanel(tab) {
    if (tab) activeTab = tab;
    panelOpen = true;
    $('#panel').classList.add('open');
    $('#panel').setAttribute('aria-hidden', 'false');
    $('#layersPop').classList.remove('open');
    $$('#tabs button').forEach(b => b.classList.toggle('active', b.dataset.tab === activeTab));
    $$('.panel-body section').forEach(s => s.classList.toggle('active', s.dataset.pane === activeTab));
    renderActivePane();
  }
  function closePanel() {
    panelOpen = false;
    $('#panel').classList.remove('open');
    $('#panel').setAttribute('aria-hidden', 'true');
  }
  $$('#tabs button').forEach(b => b.addEventListener('click', () => openPanel(b.dataset.tab)));
  $('#panelClose').addEventListener('click', closePanel);
  $('#btnJournal').addEventListener('click', () => panelOpen ? closePanel() : openPanel());
  $('#summary').addEventListener('click', () => openPanel('stats'));

  function renderActivePane() {
    ({ stats: renderStats, achievements: renderAchievements, places: renderPlaces, settings: renderSettings })[activeTab]();
  }

  function row(label, value) {
    return `<div class="row"><span>${label}</span><b>${value}</b></div>`;
  }

  function renderStats() {
    const st = S.state.stats;
    const today = G.dayNumber();
    const countries = Object.keys(S.state.places.countries).length;
    const cities = Object.keys(S.state.places.cities).length;
    const first = S.firstDay();

    let maxA = 0;
    const days = [];
    for (let d = today - 29; d <= today; d++) { const a = S.dayArea[d] || 0; maxA = Math.max(maxA, a); days.push([d, a]); }
    const bars = days.map(([d, a]) => `<i title="${G.formatDay(d, { day: 'numeric', month: 'short' })}: ${G.formatArea(a)}" style="height:${maxA ? Math.max(a ? 4 : 0, a / maxA * 100) : 0}%"></i>`).join('');

    $('#paneStats').innerHTML = `
      <div class="rows">
        ${row('Odkryta powierzchnia', G.formatArea(S.area))}
        ${row('Odkryte dziś', G.formatArea(S.dayArea[today] || 0))}
        ${row('Przebyty dystans', G.formatDistance(st.distance))}
        ${row('Dystans dziś', G.formatDistance(st.activeDays[today] || 0))}
        ${row('Dni w ruchu', S.activeDaySet().size)}
        ${row('Seria dni', `${S.streak()} (najdłuższa ${S.bestStreak()})`)}
        ${row('Kraje', countries)}
        ${row('Miejscowości', cities)}
        ${row('Najdalej od pierwszego zapisu', G.formatDistance(st.maxFromHome))}
      </div>
      <h3>Odkryta powierzchnia, ostatnie 30 dni</h3>
      <div class="chart">${bars}</div>
      <div class="chart-axis"><span>${G.formatDay(today - 29, { day: 'numeric', month: 'short' })}</span><span>dziś</span></div>
      ${first != null ? `<p class="muted">Pierwszy zapis: ${G.formatDay(first)}</p>` : ''}
      <div class="actions">
        <button class="btn" id="statFit">Pokaż całość</button>
        <button class="btn" id="statTime" ${first == null ? 'disabled' : ''}>Historia</button>
      </div>`;
    $('#statFit').onclick = fitDiscoveries;
    $('#statTime').onclick = openTimeTravel;
  }

  function fitDiscoveries() {
    let b = null;
    S.buckets.forEach(k => k.pts.forEach(p => { b = b ? b.extend([p[0], p[1]]) : L.latLngBounds([p[0], p[1]], [p[0], p[1]]); }));
    if (!b) return toast('Nic jeszcze nie odkryto');
    closePanel();
    setFollow(false);
    map.flyToBounds(b.pad(0.15), { maxZoom: 16, duration: 0.8 });
  }

  function renderAchievements() {
    const st = achievementStats();
    const got = S.state.achievements;
    const n = Object.keys(got).length;
    $('#paneAchievements').innerHTML = `
      <p class="muted">Zdobyte: ${n} z ${window.ACHIEVEMENTS.length}</p>
      <ul class="list">${window.ACHIEVEMENTS.map(a => {
        const done = !!got[a.id];
        const v = Math.min(a.value(st), a.goal);
        const progress = done
          ? 'zdobyto ' + new Date(got[a.id]).toLocaleDateString('pl-PL')
          : a.fmt ? `${a.fmt(v)} z ${a.fmt(a.goal)}` : 'jeszcze nie';
        return `<li class="${done ? 'done' : ''}">
          <span class="mark" aria-hidden="true"></span>
          <div><b>${a.name}</b><small>${a.desc}</small></div>
          <span class="val">${progress}</span>
          ${!done && a.fmt ? `<span class="meter"><i style="width:${v / a.goal * 100}%"></i></span>` : ''}
        </li>`;
      }).join('')}</ul>`;
  }

  function renderPlaces() {
    const pl = S.state.places;
    const countries = Object.entries(pl.countries).sort((a, b) => a[1].first - b[1].first);
    const byCountry = {};
    Object.values(pl.cities).sort((a, b) => b.first - a.first).forEach(c => (byCountry[c.cc] = byCountry[c.cc] || []).push(c));
    $('#panePlaces').innerHTML = !settings.geocode
      ? '<p class="muted">Rozpoznawanie miejscowości jest wyłączone w ustawieniach.</p>'
      : countries.length === 0
        ? '<p class="muted">Odwiedzone kraje i miejscowości pojawią się tutaj, gdy aplikacja je rozpozna. Wymaga to internetu.</p>'
        : countries.map(([cc, c]) => `
          <h3><span class="cc">${esc(cc)}</span>${esc(c.name)} <small>${(byCountry[cc] || []).length}</small></h3>
          <ul class="list places">${(byCountry[cc] || []).map(ci => `
            <li data-lat="${ci.lat}" data-lon="${ci.lon}"><div><b>${esc(ci.name)}</b><small>${esc(ci.region)}</small></div>
            <span class="val">${G.formatDay(ci.first, { day: 'numeric', month: 'short', year: 'numeric' })}</span></li>`).join('')}
          </ul>`).join('');
    $$('#panePlaces li[data-lat]').forEach(li => li.onclick = () => {
      closePanel();
      setFollow(false);
      map.flyTo([+li.dataset.lat, +li.dataset.lon], 13, { duration: 0.8 });
    });
  }

  function renderSettings() {
    const bg = native && nativeStatus;
    $('#paneSettings').innerHTML = `
      <h3>Mapa</h3>
      <div class="radio">${baseMapOptions()}</div>
      <h3>Miejscowości</h3>
      <label class="check"><input type="checkbox" id="sGeocode" ${settings.geocode ? 'checked' : ''}>
        <span>Rozpoznawaj odwiedzone miejscowości<small>Wysyła przybliżoną pozycję do OpenStreetMap Nominatim.</small></span></label>
      ${native ? `
      <h3>Działanie w tle</h3>
      <div class="rows">
        ${row('Lokalizacja przez cały czas', bg && bg.background ? 'tak' : 'nie')}
        ${row('Bez ograniczeń baterii', bg && bg.batteryUnrestricted ? 'tak' : 'nie')}
      </div>
      <div class="actions">
        <button class="btn" id="sBg">Uprawnienia</button>
        <button class="btn" id="sBat">Bateria</button>
      </div>` : ''}
      <h3>O aplikacji</h3>
      <p class="muted">Mapa odsłania się w promieniu ${S.RADIUS} m od każdego odczytu GPS dokładniejszego niż ${MAX_ACCURACY} m.
        Zapis działa cały czas i nie da się go wyłączyć. Odkryć nie można dopisać ani usunąć ręcznie.
        Dane są tylko na tym urządzeniu.</p>
      <p class="muted small">Mapy: OpenStreetMap, Esri, OpenTopoMap. Punkty: ${S.pointCount.toLocaleString('pl-PL')}.</p>`;
    bindBaseMapOptions($('#paneSettings .radio'));
    $('#sGeocode').onchange = e => { settings.geocode = e.target.checked; S.save(); };
    if (native) {
      $('#sBg').onclick = () => native.call('openAppSettings');
      $('#sBat').onclick = () => native.call('openBatterySettings');
    }
  }

  /* ======================= RODZAJ MAPY ======================= */

  function baseMapOptions() {
    return Object.entries(G.BASE_MAPS).map(([id, b]) =>
      `<button role="radio" aria-checked="${id === settings.baseMap}" data-base="${id}">${b.name}</button>`).join('');
  }
  function bindBaseMapOptions(root) {
    root.querySelectorAll('[data-base]').forEach(b => b.onclick = () => {
      setBaseMap(b.dataset.base);
      if (panelOpen && activeTab === 'settings') renderSettings();
    });
  }
  function renderLayers() {
    const pop = $('#layersPop');
    pop.innerHTML = baseMapOptions();
    bindBaseMapOptions(pop);
  }

  $('#btnLayers').addEventListener('click', e => {
    e.stopPropagation();
    $('#searchBox').classList.remove('open');
    $('#layersPop').classList.toggle('open');
  });
  document.addEventListener('click', e => {
    const inside = id => e.composedPath().some(el => el.id === id);
    if (!inside('layersPop') && !inside('btnLayers')) $('#layersPop').classList.remove('open');
    if (!inside('searchBox') && !inside('btnSearch')) $('#searchBox').classList.remove('open');
  });

  /* ======================= HISTORIA ======================= */

  let timePlayTimer = null;
  function openTimeTravel() {
    const first = S.firstDay();
    if (first == null) return;
    closePanel();
    const r = $('#timeRange');
    r.min = first; r.max = G.dayNumber(); r.value = r.max;
    document.body.classList.add('time-travel');
    updateTime();
  }
  function updateTime() {
    const v = +$('#timeRange').value;
    const today = G.dayNumber();
    $('#timeLabel').textContent = v >= today ? 'dziś' : G.formatDay(v);
    fog.setMaxDay(v >= today ? null : v);
  }
  function stopPlay() {
    clearInterval(timePlayTimer);
    timePlayTimer = null;
    $('#timePlay').textContent = 'Odtwórz';
  }
  function closeTimeTravel() {
    stopPlay();
    document.body.classList.remove('time-travel');
    fog.setMaxDay(null);
  }
  $('#timeRange').addEventListener('input', () => { stopPlay(); updateTime(); });
  $('#timeClose').addEventListener('click', closeTimeTravel);
  $('#timePlay').addEventListener('click', () => {
    if (timePlayTimer) return stopPlay();
    const r = $('#timeRange');
    const min = +r.min, max = +r.max;
    if (+r.value >= max) r.value = min;
    const step = Math.max(1, Math.round((max - min) / 100));
    $('#timePlay').textContent = 'Pauza';
    timePlayTimer = setInterval(() => {
      r.value = Math.min(max, +r.value + step);
      updateTime();
      if (+r.value >= max) stopPlay();
    }, 80);
  });

  /* ======================= WYSZUKIWANIE ======================= */

  let searchTimer = null, searchAbort = null;
  $('#btnSearch').addEventListener('click', e => {
    e.stopPropagation();
    $('#layersPop').classList.remove('open');
    $('#searchBox').classList.toggle('open');
    if ($('#searchBox').classList.contains('open')) setTimeout(() => $('#searchInput').focus(), 50);
  });
  $('#searchInput').addEventListener('input', () => {
    clearTimeout(searchTimer);
    const q = $('#searchInput').value.trim();
    if (q.length < 3) { $('#searchResults').innerHTML = ''; return; }
    searchTimer = setTimeout(() => runSearch(q), 450);
  });
  $('#searchInput').addEventListener('keydown', e => {
    if (e.key === 'Enter') { const f = $('#searchResults li[data-i]'); if (f) f.click(); }
  });
  function runSearch(q) {
    if (searchAbort) searchAbort.abort();
    searchAbort = new AbortController();
    fetch(`https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&accept-language=pl&q=${encodeURIComponent(q)}`, { signal: searchAbort.signal })
      .then(r => r.json())
      .then(list => {
        $('#searchResults').innerHTML = list.length ? list.map((r, i) =>
          `<li data-i="${i}"><b>${esc(r.name || r.display_name.split(',')[0])}</b><small>${esc(r.display_name)}</small></li>`).join('')
          : '<li>Nic nie znaleziono</li>';
        $$('#searchResults li[data-i]').forEach(li => li.onclick = () => {
          const r = list[+li.dataset.i];
          $('#searchBox').classList.remove('open');
          setFollow(false);
          if (r.boundingbox) {
            const bb = r.boundingbox.map(Number);
            map.flyToBounds([[bb[0], bb[2]], [bb[1], bb[3]]], { maxZoom: 16, duration: 0.8 });
          } else map.flyTo([+r.lat, +r.lon], 14);
        });
      })
      .catch(() => {});
  }

  /* ======================= START ======================= */

  if (isNativeApp) Cap.nativePromise('SystemBars', 'setStyle', { style: 'LIGHT' }).catch(() => {});
  renderTracks();
  updateSummary();
  checkAchievements(true);
  startRecording();

  if (!isNativeApp && 'serviceWorker' in navigator && location.protocol.startsWith('http')) {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }
})();
