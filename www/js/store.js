/* Odkrywca: odkryte punkty, pokrycie terenu, statystyki, zapis */
(function (global) {
  'use strict';

  const KEY = 'odkrywca.v1';
  const GRID = 0.0002;        // siatka punktów odkrycia (~22 m)
  const CHUNK = 250;          // punkty grupowane w kafle 0.05° dla szybkiego rysowania
  const AREA_GRID = 0.0005;   // siatka do liczenia powierzchni (~55 m)
  const RADIUS = 50;          // promień odsłaniania w metrach; stały, użytkownik go nie zmienia
  const G = global.Geo;
  const { haversine, dayNumber, M_PER_DEG_LAT, DEG } = G;

  function freshState() {
    return {
      version: 2,
      created: Date.now(),
      points: {},
      places: { countries: {}, cities: {} },
      geocoded: {},
      tracks: {},          // dzień -> lista odcinków [[lat,lon],...]
      stats: { distance: 0, activeDays: {}, home: null, maxFromHome: 0, nightOwl: false, earlyBird: false },
      achievements: {},
      settings: { baseMap: 'streets', geocode: true },
      lastView: null
    };
  }

  const Store = {
    GRID, AREA_GRID, RADIUS,
    state: freshState(),
    buckets: new Map(),
    coverage: new Set(),
    area: 0,
    dayArea: {},
    pointCount: 0,
    dayCounts: {},
    _saveTimer: null,
    _listeners: [],

    load() {
      try {
        const raw = localStorage.getItem(KEY);
        if (raw) {
          const s = JSON.parse(raw);
          const base = freshState();
          // przenosimy tylko dane; dawne ustawienia (promień, pędzel itp.) nie mają już znaczenia
          this.state = {
            version: 2,
            created: s.created || base.created,
            points: s.points || {},
            places: Object.assign(base.places, s.places || {}),
            geocoded: s.geocoded || {},
            tracks: s.tracks || {},
            stats: Object.assign(base.stats, s.stats || {}),
            achievements: s.achievements || {},
            settings: {
              baseMap: s.settings && G.BASE_MAPS[s.settings.baseMap] ? s.settings.baseMap : 'streets',
              geocode: !(s.settings && s.settings.geocode === false)
            },
            lastView: s.lastView || null
          };
        }
      } catch (e) {
        console.warn('Nie udało się wczytać danych', e);
      }
      this.rebuild();
    },

    rebuild() {
      this.buckets = new Map();
      this.dayCounts = {};
      this.pointCount = 0;
      const pts = this.state.points;
      for (const k in pts) this._index(k, pts[k]);
      this.recomputeCoverage();
    },

    on(fn) { this._listeners.push(fn); },
    emit(type, data) { this._listeners.forEach(fn => fn(type, data)); },

    save() {
      clearTimeout(this._saveTimer);
      this._saveTimer = setTimeout(() => this.saveNow(), 2500);
    },

    saveNow() {
      clearTimeout(this._saveTimer);
      try {
        localStorage.setItem(KEY, JSON.stringify(this.state));
      } catch (e) {
        console.error(e);
        this.emit('saveError', e);
      }
    },

    /* ---------- punkty ---------- */

    keyFor(lat, lon) {
      return Math.round(lat / GRID) + ',' + Math.round(lon / GRID);
    },

    _index(key, day) {
      const c = key.indexOf(',');
      const i = +key.slice(0, c), j = +key.slice(c + 1);
      const bi = Math.floor(i / CHUNK), bj = Math.floor(j / CHUNK);
      const bk = bi + ':' + bj;
      let b = this.buckets.get(bk);
      if (!b) {
        b = {
          key: bk,
          south: bi * CHUNK * GRID - GRID, north: (bi + 1) * CHUNK * GRID + GRID,
          west: bj * CHUNK * GRID - GRID, east: (bj + 1) * CHUNK * GRID + GRID,
          pts: []
        };
        this.buckets.set(bk, b);
      }
      b.pts.push([i * GRID, j * GRID, day, key]);
      this.dayCounts[day] = (this.dayCounts[day] || 0) + 1;
      this.pointCount++;
    },

    /* Dodaje punkt; zwraca true, gdy to nowe miejsce. */
    addPoint(lat, lon, day) {
      if (!isFinite(lat) || !isFinite(lon) || Math.abs(lat) > 85.05) return false;
      lon = ((lon + 540) % 360) - 180;
      const key = this.keyFor(lat, lon);
      if (key in this.state.points) return false;
      if (day == null) day = dayNumber();
      this.state.points[key] = day;
      this._index(key, day);
      this._cover(lat, lon, day);
      const st = this.state.stats;
      if (!st.home) st.home = [lat, lon];
      else {
        const d = haversine(st.home[0], st.home[1], lat, lon);
        if (d > st.maxFromHome) st.maxFromHome = d;
      }
      return true;
    },

    /* Odsłania odcinek między dwoma odczytami GPS. Zwraca liczbę nowych punktów. */
    revealLine(lat1, lon1, lat2, lon2, day) {
      const d = haversine(lat1, lon1, lat2, lon2);
      const step = Math.max(10, Math.min(GRID * M_PER_DEG_LAT * 0.9, RADIUS / 3));
      const n = Math.max(1, Math.ceil(d / step));
      let added = 0;
      for (let s = 0; s <= n; s++) {
        const t = s / n;
        if (this.addPoint(lat1 + (lat2 - lat1) * t, lon1 + (lon2 - lon1) * t, day)) added++;
      }
      return added;
    },

    eachPointIn(south, west, north, east, maxDay, fn) {
      this.buckets.forEach(b => {
        if (b.north < south || b.south > north || b.east < west || b.west > east) return;
        const pts = b.pts;
        for (let k = 0; k < pts.length; k++) {
          const p = pts[k];
          if (maxDay != null && p[2] > maxDay) continue;
          fn(p[0], p[1], p[2]);
        }
      });
    },

    /* ---------- powierzchnia ---------- */

    _cover(lat, lon, day) {
      const r = RADIUS;
      const dLat = r / M_PER_DEG_LAT;
      const cos = Math.max(0.01, Math.cos(lat * DEG));
      const dLon = dLat / cos;
      const i0 = Math.floor((lat - dLat) / AREA_GRID), i1 = Math.floor((lat + dLat) / AREA_GRID);
      const j0 = Math.floor((lon - dLon) / AREA_GRID), j1 = Math.floor((lon + dLon) / AREA_GRID);
      const cellArea = (AREA_GRID * M_PER_DEG_LAT) ** 2 * cos;
      for (let i = i0; i <= i1; i++) {
        const dy = ((i + 0.5) * AREA_GRID - lat) * M_PER_DEG_LAT;
        for (let j = j0; j <= j1; j++) {
          const dx = ((j + 0.5) * AREA_GRID - lon) * M_PER_DEG_LAT * cos;
          if (dx * dx + dy * dy <= r * r) {
            const k = i + ',' + j;
            if (!this.coverage.has(k)) {
              this.coverage.add(k);
              this.area += cellArea;
              this.dayArea[day] = (this.dayArea[day] || 0) + cellArea;
            }
          }
        }
      }
    },

    recomputeCoverage() {
      this.coverage = new Set();
      this.area = 0;
      this.dayArea = {};
      this.buckets.forEach(b => b.pts.forEach(p => this._cover(p[0], p[1], p[2])));
    },

    /* ---------- ślady ---------- */

    addTrackPoint(lat, lon, newSegment) {
      const day = dayNumber();
      const t = this.state.tracks[day] || (this.state.tracks[day] = []);
      if (newSegment || !t.length) t.push([]);
      const seg = t[t.length - 1];
      const last = seg[seg.length - 1];
      if (last && haversine(last[0], last[1], lat, lon) < 8) return;
      seg.push([+lat.toFixed(6), +lon.toFixed(6)]);
    },

    /* ---------- statystyki ---------- */

    addDistance(m, day) {
      const st = this.state.stats;
      st.distance += m;
      st.activeDays[day] = (st.activeDays[day] || 0) + m;
    },

    activeDaySet() {
      const s = new Set();
      for (const d in this.dayCounts) if (this.dayCounts[d] > 0) s.add(+d);
      for (const d in this.state.stats.activeDays) s.add(+d);
      return s;
    },

    streak() {
      const days = this.activeDaySet();
      let d = dayNumber();
      if (!days.has(d)) d--;
      let n = 0;
      while (days.has(d)) { n++; d--; }
      return n;
    },

    bestStreak() {
      const days = [...this.activeDaySet()].sort((a, b) => a - b);
      let best = 0, cur = 0, prev = null;
      days.forEach(d => { cur = prev !== null && d === prev + 1 ? cur + 1 : 1; best = Math.max(best, cur); prev = d; });
      return best;
    },

    firstDay() {
      let min = null;
      for (const d in this.dayCounts) if (this.dayCounts[d] > 0 && (min === null || +d < min)) min = +d;
      return min;
    }
  };

  global.Store = Store;
})(window);
