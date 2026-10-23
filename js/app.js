/* Odkrywca – logika aplikacji */
(function () {
  'use strict';

  const G = window.Geo;
  const S = window.Store;
  const $ = sel => document.querySelector(sel);
  const $$ = sel => Array.from(document.querySelectorAll(sel));
  const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  S.load();
  const settings = S.state.settings;

  /* ======================= MAPA ======================= */

  const startView = S.state.lastView || { lat: 52.07, lng: 19.48, zoom: 6 };
  const map = L.map('map', {
    zoomControl: false,
    attributionControl: true,
    worldCopyJump: true,
    minZoom: 2,
    maxZoom: 19,
    zoomSnap: 0.25,
    preferCanvas: true
  }).setView([startView.lat, startView.lng], startView.zoom);
  map.attributionControl.setPrefix(false);
  L.control.zoom({ position: 'bottomright' }).addTo(map);

  let baseLayer = null;
  function setBaseMap(id) {
    if (!G.BASE_MAPS[id]) id = 'streets';
    const def = G.BASE_MAPS[id];
    if (baseLayer) map.removeLayer(baseLayer);
    baseLayer = L.tileLayer(def.url, {
      attribution: def.attribution, subdomains: def.subdomains || 'abc',
      maxZoom: 19, maxNativeZoom: def.maxZoom, detectRetina: false, crossOrigin: true
    }).addTo(map);
    settings.baseMap = id;
    document.body.classList.toggle('map-dark', !!def.dark);
    S.save();
    renderLayerPop();
  }

  const fog = new window.FogLayer({ theme: G.FOG_THEMES[settings.fogTheme] || G.FOG_THEMES.mist, opacity: settings.fogOpacity });
  setBaseMap(settings.baseMap);
  fog.addTo(map);
  applyFogThemeUI();

  const trackRenderer = L.canvas({ padding: 0.3 });
  const trackLayer = L.layerGroup().addTo(map);
  const liveTrack = L.polyline([], { color: '#ff7a59', weight: 4, opacity: 0.9, renderer: trackRenderer, lineCap: 'round' });
  const pinLayer = L.layerGroup();
  if (settings.showPins) pinLayer.addTo(map);

  function renderTracks() {
    trackLayer.clearLayers();
    if (!settings.showTrack) return;
    const lines = [];
    Object.keys(S.state.tracks).forEach(d => S.state.tracks[d].forEach(seg => { if (seg.length > 1) lines.push(seg); }));
    if (lines.length) {
      L.polyline(lines, { color: '#ff7a59', weight: 2.5, opacity: 0.55, renderer: trackRenderer, dashArray: '1 6', lineCap: 'round', interactive: false }).addTo(trackLayer);
    }
    liveTrack.addTo(trackLayer);
  }

  /* Znacznik mojej pozycji */
  const meIcon = L.divIcon({ className: 'me-marker', html: '<span class="me-halo"></span><span class="me-dot"></span><span class="me-heading"></span>', iconSize: [24, 24] });
  const meMarker = L.marker([0, 0], { icon: meIcon, interactive: false, keyboard: false, zIndexOffset: 1000 });
  const meAccuracy = L.circle([0, 0], { radius: 10, color: '#4c6ef5', weight: 1, opacity: 0.4, fillOpacity: 0.08, interactive: false });
  let myPos = null;

  function showMe(lat, lon, acc, heading) {
    myPos = { lat, lon, acc };
    meMarker.setLatLng([lat, lon]);
    meAccuracy.setLatLng([lat, lon]).setRadius(acc || 10);
    if (!map.hasLayer(meMarker)) { meMarker.addTo(map); meAccuracy.addTo(map); }
    const el = meMarker.getElement();
    if (el) {
      const h = el.querySelector('.me-heading');
      if (heading != null && isFinite(heading)) { h.style.display = 'block'; h.style.transform = `rotate(${heading}deg)`; }
      else h.style.display = 'none';
    }
  }

  map.on('moveend', () => {
    const c = map.getCenter();
    S.state.lastView = { lat: c.lat, lng: c.lng, zoom: map.getZoom() };
    S.save();
  });

  map.on('dragstart', () => { if (tracking.active && settings.followMe) setFollow(false); });

  /* ======================= POWIADOMIENIA ======================= */

  function toast(html, opts) {
    opts = opts || {};
    const el = document.createElement('div');
    el.className = 'toast ' + (opts.type || '');
    el.innerHTML = html;
    const box = $('#toasts');
    box.appendChild(el);
    // najwyżej 4 powiadomienia naraz – najstarsze znikają
    while (box.children.length > 4) box.firstElementChild.remove();
    requestAnimationFrame(() => el.classList.add('show'));
    const close = () => { el.classList.remove('show'); setTimeout(() => el.remove(), 400); };
    el.addEventListener('click', close);
    setTimeout(close, opts.timeout || 3800);
    return el;
  }

  function celebrate(icon, title, sub) {
    toast(`<div class="toast-icon">${icon}</div><div><b>${esc(title)}</b><small>${esc(sub)}</small></div>`, { type: 'award', timeout: 5200 });
    burst();
  }

  function burst() {
    const wrap = document.createElement('div');
    wrap.className = 'burst';
    const colors = ['#ff7a59', '#ffd43b', '#4c6ef5', '#20c997', '#f06595', '#845ef7'];
    for (let i = 0; i < 28; i++) {
      const p = document.createElement('i');
      const a = (i / 28) * Math.PI * 2, d = 80 + Math.random() * 90;
      p.style.setProperty('--x', Math.cos(a) * d + 'px');
      p.style.setProperty('--y', Math.sin(a) * d + 'px');
      p.style.background = colors[i % colors.length];
      p.style.animationDelay = Math.random() * 80 + 'ms';
      wrap.appendChild(p);
    }
    document.body.appendChild(wrap);
    setTimeout(() => wrap.remove(), 1400);
  }

  S.on((type) => {
    if (type === 'saveError') toast('⚠️ Brak miejsca w pamięci przeglądarki – wyeksportuj kopię danych.', { type: 'warn', timeout: 7000 });
  });

  /* ======================= HUD / POZIOMY ======================= */

  let lastLevel = S.levelInfo().level;
  let hudTimer = null;
  function updateHUD() {
    const li = S.levelInfo();
    $('#chipArea').textContent = G.formatArea(S.area);
    $('#levelNum').textContent = li.level;
    $('#levelTitle').textContent = `${li.title} · poz. ${li.level}`;
    const circ = 2 * Math.PI * 15;
    const ring = $('#levelRing');
    ring.style.strokeDasharray = circ;
    ring.style.strokeDashoffset = circ * (1 - Math.max(0, Math.min(1, li.progress)));
    if (li.level > lastLevel) {
      celebrate('⬆️', `Poziom ${li.level}!`, `Twój tytuł: ${li.title}`);
    }
    lastLevel = li.level;
    if (panelOpen) renderActivePane();
  }
  function scheduleHUD() {
    if (hudTimer) return;
    hudTimer = setTimeout(() => { hudTimer = null; updateHUD(); checkAchievements(); }, 600);
  }

  /* ======================= ODZNAKI ======================= */

  function achievementStats() {
    const li = S.levelInfo();
    let bestDayArea = 0;
    for (const d in S.dayArea) bestDayArea = Math.max(bestDayArea, S.dayArea[d]);
    return {
      points: S.pointCount,
      area: S.area,
      distance: S.state.stats.distance,
      maxFromHome: S.state.stats.maxFromHome,
      cities: Object.keys(S.state.places.cities).length,
      countries: Object.keys(S.state.places.countries).length,
      bestStreak: S.bestStreak(),
      bestDayArea,
      nightOwl: S.state.stats.nightOwl,
      earlyBird: S.state.stats.earlyBird,
      pins: S.state.pins.length,
      level: li.level
    };
  }

  function checkAchievements(silent) {
    const st = achievementStats();
    const got = S.state.achievements;
    let changed = false;
    window.ACHIEVEMENTS.forEach(a => {
      if (!got[a.id] && a.test(st)) {
        got[a.id] = Date.now();
        changed = true;
        if (!silent) celebrate(a.icon, 'Nowa odznaka: ' + a.name, a.desc);
      }
    });
    if (changed) { S.save(); if (!silent) updateHUD(); }
  }

  /* ======================= ROZPOZNAWANIE MIEJSC ======================= */

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
    const day = p[2];
    if (cc && !places.countries[cc]) {
      places.countries[cc] = { name: a.country || cc, first: day };
      celebrate(G.flagEmoji(cc), 'Nowy kraj: ' + (a.country || cc), 'Witaj w nowym miejscu!');
    }
    const name = a.city || a.town || a.village || a.hamlet || a.municipality || a.suburb || a.county;
    if (name) {
      const key = cc + '|' + name;
      if (!places.cities[key]) {
        places.cities[key] = { name, cc, region: a.state || a.county || '', first: day, lat: p[0], lon: p[1] };
        toast(`<div class="toast-icon">${G.flagEmoji(cc)}</div><div><b>Odkryto: ${esc(name)}</b><small>${esc(a.state || a.country || '')}</small></div>`, { type: 'place' });
      }
    }
    scheduleHUD();
  }

  /* ======================= ŚLEDZENIE GPS ======================= */

  const tracking = { active: false, source: null, watchId: null, last: null, session: 0, simTimer: null, wakeLock: null };

  function setFollow(v) {
    settings.followMe = v;
    $('#btnLocate').classList.toggle('active', v && tracking.active);
    S.save();
  }

  function startTracking(source) {
    if (tracking.active) stopTracking();
    tracking.active = true;
    tracking.source = source || 'gps';
    tracking.last = null;
    tracking.session = 0;
    tracking.newSeg = true;
    liveTrack.setLatLngs([]);
    document.body.classList.add('tracking');
    $('#btnTrack').title = 'Zatrzymaj odkrywanie';
    $('#trackStatusText').textContent = tracking.source === 'sim' ? 'Symulacja spaceru' : 'Szukam sygnału GPS…';
    $('#trackSession').textContent = '0 m';
    setFollow(true);

    if (tracking.source === 'sim') startSimulation();
    else {
      if (!('geolocation' in navigator)) {
        toast('Twoja przeglądarka nie obsługuje lokalizacji 😢', { type: 'warn' });
        stopTracking();
        return;
      }
      tracking.watchId = navigator.geolocation.watchPosition(onPosition, onGeoError, {
        enableHighAccuracy: true, maximumAge: 2000, timeout: 30000
      });
    }
    requestWakeLock();
  }

  function stopTracking() {
    if (!tracking.active) return;
    if (tracking.watchId != null) navigator.geolocation.clearWatch(tracking.watchId);
    clearInterval(tracking.simTimer);
    tracking.watchId = null;
    tracking.active = false;
    document.body.classList.remove('tracking');
    $('#btnTrack').title = 'Rozpocznij odkrywanie';
    $('#btnLocate').classList.remove('active');
    releaseWakeLock();
    if (tracking.session > 0) toast(`🧭 Wyprawa zakończona: ${G.formatDistance(tracking.session)}`);
    S.saveNow();
    renderTracks();
  }

  function onGeoError(err) {
    const msg = {
      1: 'Brak zgody na lokalizację. Zezwól na nią w ustawieniach przeglądarki.',
      2: 'Nie można ustalić pozycji. Spróbuj wyjść na zewnątrz.',
      3: 'Przekroczono czas oczekiwania na GPS…'
    }[err.code] || 'Błąd lokalizacji';
    if (err.code === 1) { toast('📵 ' + msg, { type: 'warn', timeout: 6000 }); stopTracking(); }
    else $('#trackStatusText').textContent = msg;
  }

  function onPosition(pos) {
    const c = pos.coords;
    handleFix(c.latitude, c.longitude, c.accuracy, pos.timestamp || Date.now(), c.heading);
  }

  function handleFix(lat, lon, acc, time, heading) {
    showMe(lat, lon, acc, heading);
    if (settings.followMe) {
      if (map.getZoom() < 14) map.setView([lat, lon], 16);
      else map.panTo([lat, lon], { animate: true, duration: 0.6 });
    }
    if (acc > settings.maxAccuracy) {
      $('#trackStatusText').textContent = `Słaby sygnał GPS (±${Math.round(acc)} m)`;
      return;
    }
    $('#trackStatusText').textContent = tracking.source === 'sim' ? 'Symulacja spaceru' : 'Odkrywanie aktywne';

    const day = G.dayNumber(time);
    const last = tracking.last;
    let added = 0;
    if (last) {
      const d = G.haversine(last.lat, last.lon, lat, lon);
      if (d < Math.max(4, acc * 0.25)) return;
      const dt = Math.max(1, (time - last.time) / 1000);
      const speed = d / dt;
      if (speed < 85 && d < 3000) {
        S.addDistance(d);
        tracking.session += d;
        added = S.revealLine(last.lat, last.lon, lat, lon, day);
        S.addTrackPoint(lat, lon, tracking.newSeg);
      } else {
        added = S.revealDisc(lat, lon, settings.radius, day);
        S.addTrackPoint(lat, lon, true);
      }
    } else {
      added = S.revealDisc(lat, lon, settings.radius, day);
      S.addTrackPoint(lat, lon, true);
    }
    tracking.newSeg = false;
    tracking.last = { lat, lon, time };
    liveTrack.addLatLng([lat, lon]);

    const h = new Date(time).getHours();
    if (h < 4) S.state.stats.nightOwl = true;
    if (h >= 5 && h < 7) S.state.stats.earlyBird = true;

    $('#trackSession').textContent = G.formatDistance(tracking.session);
    if (added) {
      fog.redraw();
      sparkle(lat, lon);
    }
    S.save();
    scheduleHUD();
  }

  let lastSparkle = 0;
  function sparkle(lat, lon) {
    const now = Date.now();
    if (now - lastSparkle < 900) return;
    lastSparkle = now;
    const p = map.latLngToContainerPoint([lat, lon]);
    const el = document.createElement('div');
    el.className = 'sparkle';
    el.style.left = p.x + 'px';
    el.style.top = p.y + 'px';
    el.textContent = '+✨';
    $('#map').appendChild(el);
    setTimeout(() => el.remove(), 1100);
  }

  /* Symulacja spaceru – pozwala zobaczyć działanie bez wychodzenia z domu */
  function startSimulation() {
    const c = myPos ? L.latLng(myPos.lat, myPos.lon) : map.getCenter();
    let lat = c.lat, lon = c.lng, heading = Math.random() * 360, t = Date.now();
    if (map.getZoom() < 15) map.setView(c, 16);
    tracking.simTimer = setInterval(() => {
      heading += (Math.random() - 0.5) * 40;
      const step = 9;
      lat += Math.cos(heading * G.DEG) * step / G.M_PER_DEG_LAT;
      lon += Math.sin(heading * G.DEG) * step / (G.M_PER_DEG_LAT * Math.cos(lat * G.DEG));
      t += 6000;
      handleFix(lat, lon, 5, t, heading);
    }, 160);
  }

  async function requestWakeLock() {
    if (!settings.keepAwake || !('wakeLock' in navigator)) return;
    try {
      tracking.wakeLock = await navigator.wakeLock.request('screen');
    } catch (e) { /* bez znaczenia */ }
  }
  function releaseWakeLock() {
    if (tracking.wakeLock) { tracking.wakeLock.release().catch(() => {}); tracking.wakeLock = null; }
  }
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible' && tracking.active) requestWakeLock();
    if (document.visibilityState === 'hidden') S.saveNow();
  });
  window.addEventListener('pagehide', () => S.saveNow());

  $('#btnTrack').addEventListener('click', () => {
    if (tracking.active) stopTracking();
    else startTracking('gps');
  });

  $('#btnLocate').addEventListener('click', () => {
    if (tracking.active) {
      setFollow(!settings.followMe);
      if (settings.followMe && myPos) map.flyTo([myPos.lat, myPos.lon], Math.max(map.getZoom(), 16), { duration: 0.8 });
      return;
    }
    if (!navigator.geolocation) return toast('Lokalizacja niedostępna', { type: 'warn' });
    $('#btnLocate').classList.add('busy');
    navigator.geolocation.getCurrentPosition(pos => {
      $('#btnLocate').classList.remove('busy');
      const c = pos.coords;
      showMe(c.latitude, c.longitude, c.accuracy, null);
      map.flyTo([c.latitude, c.longitude], 16, { duration: 1.2 });
    }, err => {
      $('#btnLocate').classList.remove('busy');
      onGeoError(err);
    }, { enableHighAccuracy: true, timeout: 15000, maximumAge: 10000 });
  });

  /* ======================= PĘDZEL I GUMKA ======================= */

  const brush = { active: false, mode: 'reveal', drawing: false, last: null, day: null, cursor: null };

  function brushRadiusM() {
    const c = map.getCenter();
    const mpp = 40075016.686 * Math.cos(c.lat * G.DEG) / Math.pow(2, map.getZoom() + 8);
    return Math.max(settings.radius, 16 * mpp);
  }

  function setBrush(on) {
    brush.active = on;
    document.body.classList.toggle('brushing', on);
    if (on) {
      closePanel();
      map.dragging.disable();
      map.boxZoom.disable();
      toast('🖌️ Malujesz po mapie miejsca, w których byłeś. Kółkiem myszy lub dwoma palcami przybliżysz mapę.', { timeout: 4500 });
    } else {
      map.dragging.enable();
      map.boxZoom.enable();
      S.saveNow();
    }
  }

  function brushApply(latlng) {
    const r = brushRadiusM();
    if (brush.mode === 'reveal') {
      const from = brush.last || latlng;
      const d = G.haversine(from.lat, from.lng, latlng.lat, latlng.lng);
      const n = Math.max(1, Math.ceil(d / (r * 0.5)));
      for (let s = 1; s <= n; s++) {
        const t = s / n;
        S.revealDisc(from.lat + (latlng.lat - from.lat) * t, from.lng + (latlng.lng - from.lng) * t, r, brush.day);
      }
      if (!brush.last) S.revealDisc(latlng.lat, latlng.lng, r, brush.day);
    } else {
      S.erase(latlng.lat, latlng.lng, r);
    }
    brush.last = latlng;
    fog.redraw();
  }

  const mapEl = $('#map');
  mapEl.addEventListener('pointerdown', e => {
    if (!brush.active || !e.isPrimary || e.button > 0) return;
    if (e.target.closest('.leaflet-control')) return;
    brush.drawing = true;
    brush.last = null;
    const v = $('#brushDate').value;
    brush.day = v ? G.dayNumber(new Date(v + 'T12:00:00')) : G.dayNumber();
    mapEl.setPointerCapture && mapEl.setPointerCapture(e.pointerId);
    brushApply(map.mouseEventToLatLng(e));
  });
  mapEl.addEventListener('pointermove', e => {
    if (!brush.active) return;
    updateBrushCursor(e);
    if (brush.drawing && e.isPrimary) brushApply(map.mouseEventToLatLng(e));
  });
  const endStroke = () => {
    if (!brush.drawing) return;
    brush.drawing = false;
    if (brush.mode === 'erase') S.recomputeCoverage();
    fog.redraw();
    S.save();
    scheduleHUD();
  };
  mapEl.addEventListener('pointerup', endStroke);
  mapEl.addEventListener('pointercancel', endStroke);

  function updateBrushCursor(e) {
    if (!brush.cursor) {
      brush.cursor = document.createElement('div');
      brush.cursor.className = 'brush-cursor';
      mapEl.appendChild(brush.cursor);
    }
    const rect = mapEl.getBoundingClientRect();
    const c = map.getCenter();
    const mpp = 40075016.686 * Math.cos(c.lat * G.DEG) / Math.pow(2, map.getZoom() + 8);
    const rpx = brushRadiusM() / mpp;
    Object.assign(brush.cursor.style, {
      left: (e.clientX - rect.left) + 'px', top: (e.clientY - rect.top) + 'px',
      width: rpx * 2 + 'px', height: rpx * 2 + 'px'
    });
    brush.cursor.classList.toggle('erase', brush.mode === 'erase');
  }

  $('#btnBrush').addEventListener('click', () => setBrush(!brush.active));
  $('#brushDone').addEventListener('click', () => setBrush(false));
  $$('#brushBar [data-brush]').forEach(b => b.addEventListener('click', () => {
    brush.mode = b.dataset.brush;
    $$('#brushBar [data-brush]').forEach(x => x.classList.toggle('active', x === b));
  }));
  // pole daty w pasku pędzla – malowanie wspomnień z przeszłości
  (function () {
    const wrap = document.createElement('label');
    wrap.className = 'brush-date';
    wrap.innerHTML = '📅 <input type="date" id="brushDate">';
    $('#brushBar').insertBefore(wrap, $('#brushBar .brush-hint'));
    const today = G.dayToDate(G.dayNumber());
    const iso = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    $('#brushDate').value = iso(today);
    $('#brushDate').max = iso(today);
  })();

  /* ======================= WSPOMNIENIA (ZNACZNIKI) ======================= */

  const PIN_EMOJI = ['📍', '❤️', '⭐', '🏠', '☕', '🍕', '🏞️', '⛰️', '🏖️', '🏛️', '📸', '🎉', '🌅', '🚩', '🍦', '🎒'];

  function pinIcon(p) {
    return L.divIcon({ className: 'pin-marker', html: `<div class="pin-bubble">${esc(p.emoji)}</div>`, iconSize: [40, 48], iconAnchor: [20, 46], popupAnchor: [0, -42] });
  }

  function renderPins() {
    pinLayer.clearLayers();
    S.state.pins.forEach(p => {
      const m = L.marker([p.lat, p.lon], { icon: pinIcon(p), riseOnHover: true });
      m.bindPopup(`<div class="pin-pop"><div class="pin-pop-emoji">${esc(p.emoji)}</div><b>${esc(p.title || 'Wspomnienie')}</b>
        ${p.note ? `<p>${esc(p.note).replace(/\n/g, '<br>')}</p>` : ''}<small>${G.formatDay(p.day)}</small>
        <div class="pin-pop-actions"><button data-edit="${p.id}">Edytuj</button><button data-del="${p.id}" class="danger">Usuń</button></div></div>`);
      pinLayer.addLayer(m);
    });
  }

  map.on('popupopen', e => {
    const el = e.popup.getElement();
    el.querySelectorAll('[data-edit]').forEach(b => b.onclick = () => { map.closePopup(); editPin(S.state.pins.find(p => p.id === b.dataset.edit)); });
    el.querySelectorAll('[data-del]').forEach(b => b.onclick = () => { map.closePopup(); deletePin(b.dataset.del); });
  });

  function deletePin(id) {
    if (!confirm('Usunąć to wspomnienie?')) return;
    S.state.pins = S.state.pins.filter(p => p.id !== id);
    S.save();
    renderPins();
    if (panelOpen) renderActivePane();
  }

  function editPin(existing, latlng) {
    const p = existing || { id: 'p' + Date.now().toString(36), emoji: '📍', title: '', note: '', lat: latlng.lat, lon: latlng.lng, day: G.dayNumber() };
    openModal(`
      <h2>${existing ? 'Edytuj wspomnienie' : 'Nowe wspomnienie'}</h2>
      <div class="emoji-pick">${PIN_EMOJI.map(e => `<button class="${e === p.emoji ? 'active' : ''}" data-e="${e}">${e}</button>`).join('')}</div>
      <label class="field"><span>Nazwa</span><input id="pinTitle" maxlength="80" placeholder="np. Najlepsze lody w mieście" value="${esc(p.title)}"></label>
      <label class="field"><span>Notatka</span><textarea id="pinNote" rows="3" maxlength="1000" placeholder="Co tu się wydarzyło?">${esc(p.note)}</textarea></label>
      <div class="modal-actions">
        <button class="btn ghost" data-close>Anuluj</button>
        <button class="btn primary" id="pinSave">Zapisz</button>
      </div>`);
    let emoji = p.emoji;
    $$('.emoji-pick button').forEach(b => b.onclick = () => {
      emoji = b.dataset.e;
      $$('.emoji-pick button').forEach(x => x.classList.toggle('active', x === b));
    });
    setTimeout(() => $('#pinTitle').focus(), 50);
    $('#pinSave').onclick = () => {
      p.emoji = emoji;
      p.title = $('#pinTitle').value.trim();
      p.note = $('#pinNote').value.trim();
      if (!existing) {
        S.state.pins.push(p);
        if (!settings.showPins) { settings.showPins = true; pinLayer.addTo(map); }
        S.revealDisc(p.lat, p.lon, settings.radius, p.day);
        fog.redraw();
      }
      S.save();
      renderPins();
      closeModal();
      scheduleHUD();
      toast(`${esc(p.emoji)} Zapisano wspomnienie`);
    };
  }

  /* Tryb wskazywania miejsca znacznika */
  const pinPlacing = { active: false };
  function startPinPlacing() {
    closePanel();
    if (brush.active) setBrush(false);
    pinPlacing.active = true;
    document.body.classList.add('placing');
    if (myPos && tracking.active) map.panTo([myPos.lat, myPos.lon]);
  }
  function endPinPlacing() {
    pinPlacing.active = false;
    document.body.classList.remove('placing');
  }
  (function buildPlacingBar() {
    const bar = document.createElement('div');
    bar.className = 'placing-bar glass';
    bar.innerHTML = `<span>Ustaw celownik na miejscu wspomnienia</span>
      <div class="row"><button class="btn ghost" id="placeCancel">Anuluj</button>
      <button class="btn ghost" id="placeMe">📡 Moja pozycja</button>
      <button class="btn primary" id="placeOk">Tutaj</button></div>`;
    document.body.appendChild(bar);
    const cross = document.createElement('div');
    cross.className = 'crosshair';
    cross.innerHTML = '<div class="pin-bubble">📍</div>';
    document.body.appendChild(cross);
    $('#placeCancel').onclick = endPinPlacing;
    $('#placeOk').onclick = () => { endPinPlacing(); editPin(null, map.getCenter()); };
    $('#placeMe').onclick = () => {
      if (myPos) { endPinPlacing(); editPin(null, L.latLng(myPos.lat, myPos.lon)); return; }
      navigator.geolocation && navigator.geolocation.getCurrentPosition(pos => {
        endPinPlacing();
        showMe(pos.coords.latitude, pos.coords.longitude, pos.coords.accuracy);
        editPin(null, L.latLng(pos.coords.latitude, pos.coords.longitude));
      }, onGeoError, { enableHighAccuracy: true, timeout: 15000 });
    };
  })();
  $('#btnPin').addEventListener('click', startPinPlacing);
  map.on('contextmenu', e => { if (!brush.active) editPin(null, e.latlng); });

  /* ======================= MODAL ======================= */

  function openModal(html, cls) {
    $('#modalContent').innerHTML = html;
    $('#modal .modal').className = 'modal ' + (cls || '');
    $('#modal').classList.add('open');
    $$('#modalContent [data-close]').forEach(b => b.onclick = closeModal);
  }
  function closeModal() { $('#modal').classList.remove('open'); }
  $('#modal').addEventListener('click', e => { if (e.target.id === 'modal') closeModal(); });
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape') { closeModal(); closePanel(); $('#layersPop').classList.remove('open'); if (brush.active) setBrush(false); endPinPlacing(); }
  });

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
  $('#statChip').addEventListener('click', () => panelOpen && activeTab === 'stats' ? closePanel() : openPanel('stats'));
  $('#btnJournal').addEventListener('click', () => panelOpen && activeTab === 'places' ? closePanel() : openPanel('places'));
  $('#btnMenu').addEventListener('click', () => panelOpen && activeTab === 'settings' ? closePanel() : openPanel('settings'));

  // przeciąganie panelu w dół na telefonie
  (function () {
    const grip = $('.panel-grip'), panel = $('#panel');
    let y0 = null;
    grip.addEventListener('pointerdown', e => { y0 = e.clientY; grip.setPointerCapture(e.pointerId); panel.style.transition = 'none'; });
    grip.addEventListener('pointermove', e => { if (y0 != null) panel.style.transform = `translateY(${Math.max(0, e.clientY - y0)}px)`; });
    const end = e => {
      if (y0 == null) return;
      panel.style.transition = ''; panel.style.transform = '';
      if (e.clientY - y0 > 90) closePanel();
      y0 = null;
    };
    grip.addEventListener('pointerup', end);
    grip.addEventListener('pointercancel', end);
  })();

  function renderActivePane() {
    ({ stats: renderStats, achievements: renderAchievements, places: renderPlaces, pins: renderPinList, settings: renderSettings })[activeTab]();
  }

  function renderStats() {
    const li = S.levelInfo();
    const st = S.state.stats;
    const today = G.dayNumber();
    const countries = Object.keys(S.state.places.countries).length;
    const cities = Object.keys(S.state.places.cities).length;
    const pct = S.area / 1e6 / G.EARTH_AREA_KM2 * 100;
    const fields = S.area / 7140;
    const first = S.firstDay();

    // wykres ostatnich 30 dni
    let maxA = 0;
    const days = [];
    for (let d = today - 29; d <= today; d++) { const a = S.dayArea[d] || 0; maxA = Math.max(maxA, a); days.push([d, a]); }
    const bars = days.map(([d, a]) => `<div class="bar ${d === today ? 'today' : ''}" title="${G.formatDay(d, { day: 'numeric', month: 'short' })}: ${G.formatArea(a)}">
      <i style="height:${maxA ? Math.max(a ? 6 : 0, a / maxA * 100) : 0}%"></i></div>`).join('');

    $('#paneStats').innerHTML = `
      <div class="level-card">
        <div class="level-badge"><span>${li.level}</span></div>
        <div class="level-info">
          <small>Twój tytuł</small>
          <h3>${li.title}</h3>
          <div class="xp-bar"><i style="width:${Math.round(li.progress * 100)}%"></i></div>
          <small>${li.xp.toLocaleString('pl-PL')} XP · jeszcze ${(li.to - li.xp).toLocaleString('pl-PL')} XP do poziomu ${li.level + 1}</small>
        </div>
      </div>

      <div class="stat-grid">
        ${tile('🗺️', G.formatArea(S.area), 'odkryty obszar')}
        ${tile('🌐', G.formatPercent(pct), 'powierzchni Ziemi')}
        ${tile('👣', G.formatDistance(st.distance), 'przebyty dystans')}
        ${tile('✨', G.formatArea(S.dayArea[today] || 0), 'odkryte dziś')}
        ${tile('🔥', S.streak() + ' ' + G.plural(S.streak(), 'dzień', 'dni', 'dni'), 'seria (rekord: ' + S.bestStreak() + ')')}
        ${tile('📅', S.activeDaySet().size, 'dni odkrywania')}
        ${tile('🏳️', countries, G.plural(countries, 'kraj', 'kraje', 'krajów'))}
        ${tile('🏘️', cities, G.plural(cities, 'miejscowość', 'miejscowości', 'miejscowości'))}
        ${tile('🧭', G.formatDistance(st.maxFromHome), 'najdalej od startu')}
        ${tile('📍', S.state.pins.length, 'wspomnień')}
      </div>

      <div class="fun-fact">⚽ Twój odkryty świat to około <b>${Math.round(fields).toLocaleString('pl-PL')}</b> ${G.plural(Math.round(fields), 'boisko', 'boiska', 'boisk')} piłkarskich.
        ${S.area > 0 ? `Cała Ziemia jest jeszcze <b>${Math.round(G.EARTH_AREA_KM2 * 1e6 / S.area).toLocaleString('pl-PL')}</b> razy większa – przygód Ci nie zabraknie 😉` : ''}</div>

      <h4 class="sec-title">Ostatnie 30 dni</h4>
      <div class="chart">${bars}</div>
      <div class="chart-axis"><span>${G.formatDay(today - 29, { day: 'numeric', month: 'short' })}</span><span>dziś</span></div>

      <div class="btn-row">
        <button class="btn" id="statFit">🔭 Pokaż moje odkrycia</button>
        <button class="btn" id="statTime" ${first == null ? 'disabled' : ''}>⏳ Podróż w czasie</button>
      </div>
      ${first != null ? `<p class="muted center">Odkrywasz świat od ${G.formatDay(first)}</p>` : ''}
    `;
    $('#statFit').onclick = fitDiscoveries;
    $('#statTime').onclick = openTimeTravel;
  }

  function tile(icon, value, label) {
    return `<div class="tile"><span class="tile-ico">${icon}</span><b>${value}</b><small>${label}</small></div>`;
  }

  function fitDiscoveries() {
    let b = null;
    S.buckets.forEach(k => k.pts.forEach(p => { b = b ? b.extend([p[0], p[1]]) : L.latLngBounds([p[0], p[1]], [p[0], p[1]]); }));
    if (!b) return toast('Nie masz jeszcze odkrytych miejsc. Ruszaj w drogę! 🚶');
    closePanel();
    map.flyToBounds(b.pad(0.15), { maxZoom: 16, duration: 1.2 });
  }

  function renderAchievements() {
    const st = achievementStats();
    const got = S.state.achievements;
    const n = Object.keys(got).length, total = window.ACHIEVEMENTS.length;
    $('#paneAchievements').innerHTML = `
      <div class="ach-head"><b>${n}</b> / ${total} odznak zdobytych<div class="xp-bar"><i style="width:${n / total * 100}%"></i></div></div>
      <div class="ach-grid">${window.ACHIEVEMENTS.map(a => {
        const done = !!got[a.id];
        const prog = !done && a.progress ? Math.min(1, a.progress(st)) : null;
        return `<div class="ach ${done ? 'done' : ''}">
          <div class="ach-ico">${a.icon}</div>
          <div class="ach-txt"><b>${a.name}</b><small>${a.desc}</small>
            ${done ? `<em>Zdobyto ${new Date(got[a.id]).toLocaleDateString('pl-PL')}</em>` : ''}
            ${prog != null ? `<div class="mini-bar"><i style="width:${prog * 100}%"></i></div>` : ''}
          </div></div>`;
      }).join('')}</div>`;
  }

  function renderPlaces() {
    const pl = S.state.places;
    const countries = Object.entries(pl.countries).sort((a, b) => a[1].first - b[1].first);
    const cities = Object.values(pl.cities).sort((a, b) => b.first - a.first);
    const byCountry = {};
    cities.forEach(c => (byCountry[c.cc] = byCountry[c.cc] || []).push(c));
    $('#panePlaces').innerHTML = `
      ${!settings.geocode ? '<div class="notice">Rozpoznawanie miejsc jest wyłączone w ustawieniach.</div>' : ''}
      ${countries.length === 0 ? `<div class="empty"><div class="empty-ico">🌍</div><b>Twój dziennik jest jeszcze pusty</b><p>Gdy odkryjesz nowe miejsca, automatycznie pojawią się tu odwiedzone kraje i miejscowości.</p></div>` : ''}
      ${countries.map(([cc, c]) => `
        <details class="country" open>
          <summary><span class="flag">${G.flagEmoji(cc)}</span><b>${esc(c.name)}</b><span class="count">${(byCountry[cc] || []).length}</span></summary>
          <ul class="city-list">${(byCountry[cc] || []).map(ci => `
            <li data-lat="${ci.lat}" data-lon="${ci.lon}"><span>${esc(ci.name)}</span><small>${esc(ci.region)} · ${G.formatDay(ci.first, { day: 'numeric', month: 'short', year: 'numeric' })}</small></li>`).join('')}
          </ul>
        </details>`).join('')}`;
    $$('#panePlaces li[data-lat]').forEach(li => li.onclick = () => {
      closePanel();
      map.flyTo([+li.dataset.lat, +li.dataset.lon], 13, { duration: 1.2 });
    });
  }

  function renderPinList() {
    const pins = S.state.pins.slice().sort((a, b) => b.day - a.day);
    $('#panePins').innerHTML = `
      <button class="btn primary wide" id="pinAdd">＋ Dodaj wspomnienie</button>
      ${pins.length === 0 ? `<div class="empty"><div class="empty-ico">📍</div><b>Brak wspomnień</b><p>Zaznaczaj ulubione miejsca, kawiarnie, widoki. Kliknij prawym przyciskiem (lub przytrzymaj) mapę, aby szybko dodać.</p></div>` : ''}
      <ul class="pin-list">${pins.map(p => `
        <li data-id="${p.id}"><span class="pin-emoji">${esc(p.emoji)}</span>
          <div><b>${esc(p.title || 'Wspomnienie')}</b><small>${G.formatDay(p.day)}${p.note ? ' · ' + esc(p.note.slice(0, 60)) : ''}</small></div>
          <button class="btn-icon" data-del="${p.id}" title="Usuń">🗑️</button></li>`).join('')}
      </ul>`;
    $('#pinAdd').onclick = startPinPlacing;
    $$('#panePins li[data-id]').forEach(li => li.onclick = e => {
      if (e.target.closest('[data-del]')) return deletePin(li.dataset.id);
      const p = S.state.pins.find(x => x.id === li.dataset.id);
      closePanel();
      if (!settings.showPins) { settings.showPins = true; pinLayer.addTo(map); }
      map.flyTo([p.lat, p.lon], 17, { duration: 1.2 });
    });
  }

  function renderSettings() {
    const s = settings;
    $('#paneSettings').innerHTML = `
      <h4 class="sec-title">Odkrywanie</h4>
      <label class="range-field"><span>Promień odsłaniania <b id="vRadius">${s.radius} m</b></span>
        <input type="range" id="sRadius" min="20" max="250" step="5" value="${s.radius}"></label>
      <label class="range-field"><span>Ignoruj odczyty GPS gorsze niż <b id="vAcc">${s.maxAccuracy} m</b></span>
        <input type="range" id="sAcc" min="15" max="300" step="5" value="${s.maxAccuracy}"></label>
      ${toggle('sFollow', 'Podążaj za mną podczas odkrywania', s.followMe)}
      ${toggle('sAwake', 'Nie wygaszaj ekranu podczas odkrywania', s.keepAwake)}
      ${toggle('sGeocode', 'Rozpoznawaj odwiedzone miasta i kraje', s.geocode, 'Używa OpenStreetMap Nominatim (wymaga internetu)')}

      <h4 class="sec-title">Wygląd</h4>
      <label class="range-field"><span>Gęstość mgły <b id="vOpacity">${Math.round(s.fogOpacity * 100)}%</b></span>
        <input type="range" id="sOpacity" min="0.4" max="1" step="0.02" value="${s.fogOpacity}"></label>
      <div class="swatches">${fogSwatches()}</div>
      ${toggle('sTrack', 'Pokazuj przebyte trasy', s.showTrack)}
      ${toggle('sPins', 'Pokazuj wspomnienia na mapie', s.showPins)}

      <h4 class="sec-title">Dane</h4>
      <p class="muted">Wszystko zapisuje się tylko na tym urządzeniu. Rób kopie zapasowe, aby nie stracić postępów.</p>
      <div class="btn-col">
        <button class="btn" id="dExport">💾 Eksportuj kopię zapasową</button>
        <button class="btn" id="dImport">📂 Importuj (kopia, GPX, KML, GeoJSON, Google)</button>
        <button class="btn" id="dGpx">🛰️ Eksportuj trasy do GPX</button>
        <button class="btn" id="dSim">${tracking.source === 'sim' && tracking.active ? '⏹ Zatrzymaj symulację' : '🎮 Symuluj spacer (demo)'}</button>
        <button class="btn danger" id="dReset">🗑️ Wyczyść wszystkie odkrycia</button>
      </div>
      <p class="muted small">Punkty odkrycia: ${S.pointCount.toLocaleString('pl-PL')} · Mapy: © OpenStreetMap, CARTO, Esri, OpenTopoMap</p>
      <p class="muted small center">Odkrywca v1.0 · Zrobione z ❤️ dla ciekawskich świata</p>`;

    const bindRange = (id, out, fmt, fn) => {
      const el = $('#' + id);
      el.oninput = () => { $('#' + out).textContent = fmt(+el.value); fn(+el.value, false); };
      el.onchange = () => fn(+el.value, true);
    };
    bindRange('sRadius', 'vRadius', v => v + ' m', (v, done) => {
      s.radius = v;
      fog.redraw();
      if (done) { S.recomputeCoverage(); S.save(); updateHUD(); }
    });
    bindRange('sAcc', 'vAcc', v => v + ' m', v => { s.maxAccuracy = v; S.save(); });
    bindRange('sOpacity', 'vOpacity', v => Math.round(v * 100) + '%', v => { s.fogOpacity = v; fog.setOpacity(v); S.save(); });
    $('#sFollow').onchange = e => setFollow(e.target.checked);
    $('#sAwake').onchange = e => { s.keepAwake = e.target.checked; S.save(); if (!s.keepAwake) releaseWakeLock(); else if (tracking.active) requestWakeLock(); };
    $('#sGeocode').onchange = e => { s.geocode = e.target.checked; S.save(); };
    $('#sTrack').onchange = e => { s.showTrack = e.target.checked; S.save(); renderTracks(); };
    $('#sPins').onchange = e => { s.showPins = e.target.checked; S.save(); s.showPins ? pinLayer.addTo(map) : map.removeLayer(pinLayer); };
    bindSwatches($('#paneSettings .swatches'));
    $('#dExport').onclick = exportBackup;
    $('#dImport').onclick = () => $('#fileInput').click();
    $('#dGpx').onclick = exportGPX;
    $('#dSim').onclick = () => {
      if (tracking.active && tracking.source === 'sim') stopTracking();
      else { closePanel(); startTracking('sim'); }
      renderSettings();
    };
    $('#dReset').onclick = () => {
      if (!confirm('Na pewno usunąć wszystkie odkrycia, trasy, miejsca i wspomnienia? Tej operacji nie da się cofnąć.')) return;
      stopTracking();
      S.reset();
      afterBulkChange();
      toast('Mapa znów spowita jest mgłą 🌫️');
    };
  }

  function toggle(id, label, checked, hint) {
    return `<label class="toggle"><span>${label}${hint ? `<small>${hint}</small>` : ''}</span>
      <input type="checkbox" id="${id}" ${checked ? 'checked' : ''}><i></i></label>`;
  }

  /* ======================= STYL MAPY I MGŁY ======================= */

  function fogSwatches() {
    return Object.entries(G.FOG_THEMES).map(([id, t]) =>
      `<button class="swatch ${id === settings.fogTheme ? 'active' : ''}" data-fog="${id}" title="${t.name}">
        <i style="background:linear-gradient(135deg,${t.top},${t.bottom})"></i><span>${t.name}</span></button>`).join('');
  }
  function bindSwatches(root) {
    root.querySelectorAll('[data-fog]').forEach(b => b.onclick = () => setFogTheme(b.dataset.fog));
  }
  function setFogTheme(id) {
    settings.fogTheme = id;
    fog.setTheme(G.FOG_THEMES[id]);
    applyFogThemeUI();
    S.save();
    renderLayerPop();
    if (panelOpen && activeTab === 'settings') renderSettings();
  }
  function applyFogThemeUI() {
    const t = G.FOG_THEMES[settings.fogTheme] || G.FOG_THEMES.mist;
    document.body.classList.toggle('fog-dark', !!t.dark);
    document.documentElement.style.setProperty('--fog-top', t.top);
    document.documentElement.style.setProperty('--fog-bottom', t.bottom);
    const meta = document.querySelector('meta[name=theme-color]');
    if (meta) meta.content = t.top;
  }

  function renderLayerPop() {
    const grid = $('#baseGrid');
    if (!grid) return;
    grid.innerHTML = Object.entries(G.BASE_MAPS).map(([id, b]) =>
      `<button class="layer-card ${id === settings.baseMap ? 'active' : ''}" data-base="${id}"><i style="background:${b.preview}"></i><span>${b.name}</span></button>`).join('');
    grid.querySelectorAll('[data-base]').forEach(b => b.onclick = () => setBaseMap(b.dataset.base));
    $('#fogGrid').innerHTML = fogSwatches();
    bindSwatches($('#fogGrid'));
  }
  renderLayerPop();

  $('#btnLayers').addEventListener('click', e => {
    e.stopPropagation();
    $('#layersPop').classList.toggle('open');
  });
  document.addEventListener('click', e => {
    // composedPath działa także, gdy kliknięty element został już przerysowany
    const inside = id => e.composedPath().some(el => el.id === id);
    if (!inside('layersPop') && !inside('btnLayers')) $('#layersPop').classList.remove('open');
    if (!inside('searchBox') && !inside('btnSearch')) $('#searchBox').classList.remove('open');
  });

  $('#btnFogToggle').addEventListener('click', () => {
    fog.setVisible(!fog.visible);
    $('#btnFogToggle').classList.toggle('active', !fog.visible);
    toast(fog.visible ? '🌫️ Mgła przywrócona' : '👀 Podgląd bez mgły');
  });

  /* ======================= PODRÓŻ W CZASIE ======================= */

  let timePlayTimer = null;
  function openTimeTravel() {
    const first = S.firstDay();
    if (first == null) return;
    closePanel();
    const today = G.dayNumber();
    const r = $('#timeRange');
    r.min = first; r.max = today; r.value = today;
    document.body.classList.add('time-travel');
    updateTime();
  }
  function updateTime() {
    const v = +$('#timeRange').value;
    const today = G.dayNumber();
    $('#timeLabel').textContent = v >= today ? 'dziś' : G.formatDay(v);
    fog.setMaxDay(v >= today ? null : v);
  }
  function closeTimeTravel() {
    clearInterval(timePlayTimer);
    timePlayTimer = null;
    $('#timePlay').textContent = '▶';
    document.body.classList.remove('time-travel');
    fog.setMaxDay(null);
  }
  $('#timeRange').addEventListener('input', updateTime);
  $('#timeClose').addEventListener('click', closeTimeTravel);
  $('#timePlay').addEventListener('click', () => {
    if (timePlayTimer) {
      clearInterval(timePlayTimer); timePlayTimer = null; $('#timePlay').textContent = '▶'; return;
    }
    const r = $('#timeRange');
    const min = +r.min, max = +r.max;
    if (+r.value >= max) r.value = min;
    const step = Math.max(1, Math.round((max - min) / 120));
    $('#timePlay').textContent = '⏸';
    timePlayTimer = setInterval(() => {
      r.value = Math.min(max, +r.value + step);
      updateTime();
      if (+r.value >= max) { clearInterval(timePlayTimer); timePlayTimer = null; $('#timePlay').textContent = '▶'; }
    }, 60);
  });

  /* ======================= WYSZUKIWANIE ======================= */

  let searchTimer = null, searchAbort = null;
  $('#btnSearch').addEventListener('click', e => {
    e.stopPropagation();
    $('#searchBox').classList.toggle('open');
    if ($('#searchBox').classList.contains('open')) setTimeout(() => $('#searchInput').focus(), 100);
  });
  $('#searchInput').addEventListener('input', () => {
    clearTimeout(searchTimer);
    const q = $('#searchInput').value.trim();
    if (q.length < 3) { $('#searchResults').innerHTML = ''; return; }
    searchTimer = setTimeout(() => runSearch(q), 450);
  });
  $('#searchInput').addEventListener('keydown', e => {
    if (e.key === 'Enter') { const f = $('#searchResults li'); if (f) f.click(); }
  });
  function runSearch(q) {
    if (searchAbort) searchAbort.abort();
    searchAbort = new AbortController();
    fetch(`https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&accept-language=pl&q=${encodeURIComponent(q)}`, { signal: searchAbort.signal })
      .then(r => r.json())
      .then(list => {
        $('#searchResults').innerHTML = list.length ? list.map((r, i) =>
          `<li data-i="${i}"><b>${esc(r.name || r.display_name.split(',')[0])}</b><small>${esc(r.display_name)}</small></li>`).join('')
          : '<li class="none">Nic nie znaleziono</li>';
        $$('#searchResults li[data-i]').forEach(li => li.onclick = () => {
          const r = list[+li.dataset.i];
          $('#searchBox').classList.remove('open');
          if (r.boundingbox) {
            const bb = r.boundingbox.map(Number);
            map.flyToBounds([[bb[0], bb[2]], [bb[1], bb[3]]], { maxZoom: 16, duration: 1.4 });
          } else map.flyTo([+r.lat, +r.lon], 14);
        });
      })
      .catch(() => {});
  }

  /* ======================= IMPORT / EKSPORT ======================= */

  function download(name, content, type) {
    const blob = new Blob([content], { type });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = name;
    document.body.appendChild(a);
    a.click();
    setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
  }

  function stamp() { return new Date().toISOString().slice(0, 10); }

  function exportBackup() {
    S.saveNow();
    download(`odkrywca-kopia-${stamp()}.json`, JSON.stringify({ app: 'odkrywca', exported: Date.now(), state: S.state }), 'application/json');
    toast('💾 Kopia zapasowa zapisana');
  }

  function exportGPX() {
    const days = Object.keys(S.state.tracks).sort((a, b) => a - b);
    if (!days.length) return toast('Brak zapisanych tras do eksportu');
    let gpx = '<?xml version="1.0" encoding="UTF-8"?>\n<gpx version="1.1" creator="Odkrywca" xmlns="http://www.topografix.com/GPX/1/1">\n';
    days.forEach(d => {
      gpx += `<trk><name>${G.formatDay(+d)}</name>\n`;
      S.state.tracks[d].forEach(seg => {
        if (!seg.length) return;
        gpx += '<trkseg>' + seg.map(p => `<trkpt lat="${p[0]}" lon="${p[1]}"/>`).join('') + '</trkseg>\n';
      });
      gpx += '</trk>\n';
    });
    gpx += '</gpx>';
    download(`odkrywca-trasy-${stamp()}.gpx`, gpx, 'application/gpx+xml');
  }

  $('#fileInput').addEventListener('change', async e => {
    const files = Array.from(e.target.files || []);
    e.target.value = '';
    for (const f of files) {
      try { await importFile(f); }
      catch (err) { console.error(err); toast(`⚠️ Nie udało się wczytać „${esc(f.name)}”: ${esc(err.message || err)}`, { type: 'warn', timeout: 6000 }); }
    }
  });

  async function importFile(file) {
    const text = await file.text();
    const name = file.name.toLowerCase();
    let segments = null;
    if (name.endsWith('.gpx')) segments = G.parseGPX(text);
    else if (name.endsWith('.kml')) segments = G.parseKML(text);
    else {
      const obj = JSON.parse(text);
      if (obj.app === 'odkrywca' && obj.state) {
        if (!confirm('Wczytać kopię zapasową? Zastąpi ona obecne dane na tym urządzeniu.')) return;
        stopTracking();
        S.replaceState(obj.state);
        afterBulkChange();
        toast('✅ Przywrócono kopię zapasową');
        return;
      }
      if (obj.type && /Feature|Collection|LineString|Point|Polygon/.test(obj.type)) segments = G.parseGeoJSON(obj);
      else segments = G.parseGoogleHistory(obj);
    }
    if (!segments || !segments.length) throw new Error('nie znaleziono w nim żadnych punktów');
    let added = 0, total = 0, dist = 0;
    const today = G.dayNumber();
    segments.forEach(seg => {
      let prev = null;
      seg.forEach(p => {
        total++;
        const day = p.time && isFinite(p.time) ? Math.min(today, G.dayNumber(p.time)) : today;
        if (prev) {
          const d = G.haversine(prev.lat, prev.lon, p.lat, p.lon);
          const dt = prev.time && p.time ? (p.time - prev.time) / 1000 : null;
          if (d < 3000 && (dt == null || d / Math.max(dt, 1) < 85)) { added += S.revealLine(prev.lat, prev.lon, p.lat, p.lon, day); dist += d; }
          else added += S.revealDisc(p.lat, p.lon, settings.radius, day);
        } else added += S.revealDisc(p.lat, p.lon, settings.radius, day);
        prev = p;
      });
      // trasa do podglądu
      const tday = seg[0].time && isFinite(seg[0].time) ? G.dayNumber(seg[0].time) : today;
      const simplified = seg.filter((p, i) => i % Math.max(1, Math.floor(seg.length / 2000)) === 0).map(p => [+p.lat.toFixed(6), +p.lon.toFixed(6)]);
      if (simplified.length > 1) (S.state.tracks[tday] = S.state.tracks[tday] || []).push(simplified);
    });
    S.state.stats.distance += dist;
    S.saveNow();
    afterBulkChange();
    toast(`📥 Zaimportowano „${esc(file.name)}”: ${total.toLocaleString('pl-PL')} punktów, ${added.toLocaleString('pl-PL')} nowych miejsc`, { timeout: 5000 });
    fitDiscoveries();
  }

  function afterBulkChange() {
    fog.redraw();
    renderTracks();
    renderPins();
    lastLevel = S.levelInfo().level;
    updateHUD();
    checkAchievements();
    if (panelOpen) renderActivePane();
  }

  // przeciągnij i upuść plik na mapę
  document.addEventListener('dragover', e => { e.preventDefault(); document.body.classList.add('dropping'); });
  document.addEventListener('dragleave', e => { if (!e.relatedTarget) document.body.classList.remove('dropping'); });
  document.addEventListener('drop', async e => {
    e.preventDefault();
    document.body.classList.remove('dropping');
    for (const f of Array.from(e.dataTransfer.files || [])) {
      try { await importFile(f); } catch (err) { toast(`⚠️ ${esc(err.message || err)}`, { type: 'warn' }); }
    }
  });

  /* ======================= POWITANIE ======================= */

  function showWelcome() {
    openModal(`
      <div class="welcome">
        <div class="welcome-art">
          <svg viewBox="0 0 64 64"><circle cx="32" cy="32" r="28" fill="none" stroke="currentColor" stroke-width="3" opacity=".3"/>
          <path d="M32 10 L39 32 L32 54 L25 32 Z" fill="currentColor"/><path d="M32 10 L39 32 L25 32 Z" fill="#ff7a59"/></svg>
        </div>
        <h1>Witaj, Odkrywco!</h1>
        <p class="lead">Cały świat spowija mgła. Rozwiewa się tylko tam, gdzie naprawdę byłeś.</p>
        <ul class="features">
          <li><span>🚶</span><div><b>Włącz odkrywanie</b> i ruszaj na spacer – mapa odsłania się wokół Ciebie.</div></li>
          <li><span>🖌️</span><div><b>Pędzlem</b> odsłonisz miejsca z przeszłości, a <b>import GPX</b> wczyta Twoje dawne trasy.</div></li>
          <li><span>🏆</span><div>Zdobywaj <b>poziomy i odznaki</b>, zbieraj kraje i miasta w dzienniku.</div></li>
          <li><span>🔒</span><div>Twoje dane zostają <b>tylko na tym urządzeniu</b>.</div></li>
        </ul>
        <div class="modal-actions column">
          <button class="btn primary big" id="wStart">🧭 Zacznij odkrywać</button>
          <button class="btn ghost" id="wDemo">🎮 Zobacz demo (symulowany spacer)</button>
          <button class="btn ghost" data-close>Najpierw się rozejrzę</button>
        </div>
      </div>`, 'wide');
    const done = () => { S.state.onboarded = true; S.saveNow(); };
    $('#wStart').onclick = () => { done(); closeModal(); startTracking('gps'); };
    $('#wDemo').onclick = () => { done(); closeModal(); startTracking('sim'); };
    $$('#modalContent [data-close]').forEach(b => b.addEventListener('click', done));
  }

  /* ======================= START ======================= */

  renderTracks();
  renderPins();
  updateHUD();
  checkAchievements(true);
  if (!S.state.onboarded) setTimeout(showWelcome, 400);

  if ('serviceWorker' in navigator && location.protocol.startsWith('http')) {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }

  // dostęp do debugowania z konsoli
  window.odkrywca = { map, fog, store: S, startTracking, stopTracking, handleFix };
})();
