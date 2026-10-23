/* Odkrywca – pomocnicze funkcje geograficzne, motywy i parsery plików */
(function (global) {
  'use strict';

  const EARTH_R = 6371008.8;
  const EARTH_AREA_KM2 = 510072000;
  const DEG = Math.PI / 180;
  const M_PER_DEG_LAT = 111320;

  function haversine(lat1, lon1, lat2, lon2) {
    const dLat = (lat2 - lat1) * DEG;
    const dLon = (lon2 - lon1) * DEG;
    const a = Math.sin(dLat / 2) ** 2 +
      Math.cos(lat1 * DEG) * Math.cos(lat2 * DEG) * Math.sin(dLon / 2) ** 2;
    return 2 * EARTH_R * Math.asin(Math.min(1, Math.sqrt(a)));
  }

  /* Numer dnia w lokalnej strefie czasowej (dni od 1970-01-01). */
  function dayNumber(date) {
    const d = date ? new Date(date) : new Date();
    return Math.floor((d.getTime() - d.getTimezoneOffset() * 60000) / 86400000);
  }

  function dayToDate(day) {
    const d = new Date(day * 86400000);
    return new Date(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate());
  }

  function formatDay(day, opts) {
    return dayToDate(day).toLocaleDateString('pl-PL', opts || { day: 'numeric', month: 'long', year: 'numeric' });
  }

  function formatDistance(m) {
    if (m < 1000) return Math.round(m) + ' m';
    if (m < 100000) return (m / 1000).toFixed(m < 10000 ? 2 : 1).replace('.', ',') + ' km';
    return Math.round(m / 1000).toLocaleString('pl-PL') + ' km';
  }

  function formatArea(m2) {
    const km2 = m2 / 1e6;
    if (km2 < 0.01) return Math.round(m2).toLocaleString('pl-PL') + ' m²';
    if (km2 < 10) return km2.toFixed(2).replace('.', ',') + ' km²';
    if (km2 < 1000) return km2.toFixed(1).replace('.', ',') + ' km²';
    return Math.round(km2).toLocaleString('pl-PL') + ' km²';
  }

  function formatPercent(p) {
    if (p === 0) return '0%';
    if (p >= 1) return p.toFixed(2).replace('.', ',') + '%';
    // dwie cyfry znaczące – np. 0,000000025%
    const digits = Math.min(14, Math.ceil(-Math.log10(p)) + 1);
    return p.toFixed(digits).replace('.', ',') + '%';
  }

  function plural(n, one, few, many) {
    if (n === 1) return one;
    const n10 = n % 10, n100 = n % 100;
    if (n10 >= 2 && n10 <= 4 && (n100 < 12 || n100 > 14)) return few;
    return many;
  }

  function flagEmoji(cc) {
    if (!cc || cc.length !== 2) return '🏳️';
    return String.fromCodePoint(...cc.toUpperCase().split('').map(c => 0x1F1E6 + c.charCodeAt(0) - 65));
  }

  const BASE_MAPS = {
    streets: {
      name: 'Ulice',
      url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}',
      attribution: 'Tiles &copy; Esri &mdash; Esri, HERE, Garmin, &copy; OpenStreetMap',
      maxZoom: 19, preview: 'linear-gradient(135deg,#f4f1ea,#fde9b8 45%,#b9d7ea)'
    },
    osm: {
      name: 'Klasyczna',
      url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      maxZoom: 19, preview: 'linear-gradient(135deg,#f2efe9,#aad3df 50%,#cdebb0)'
    },
    light: {
      name: 'Jasna',
      url: 'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}',
      attribution: 'Tiles &copy; Esri &mdash; Esri, HERE, Garmin, &copy; OpenStreetMap',
      maxZoom: 16, preview: 'linear-gradient(135deg,#fafafa,#e3e6ea 60%,#cfd4da)'
    },
    dark: {
      name: 'Nocna',
      url: 'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}',
      attribution: 'Tiles &copy; Esri &mdash; Esri, HERE, Garmin, &copy; OpenStreetMap',
      maxZoom: 16, dark: true, preview: 'linear-gradient(135deg,#202330,#3a3f4f 60%,#14161e)'
    },
    satellite: {
      name: 'Satelita',
      url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
      attribution: 'Tiles &copy; Esri &mdash; Esri, Maxar, Earthstar Geographics',
      maxZoom: 19, dark: true, preview: 'linear-gradient(135deg,#2c4a2a,#5d7b46 45%,#1e3a5c)'
    },
    topo: {
      name: 'Teren',
      url: 'https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png',
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>, SRTM | &copy; <a href="https://opentopomap.org">OpenTopoMap</a>',
      subdomains: 'abc', maxZoom: 17, preview: 'linear-gradient(135deg,#e8e3c9,#bcd29b 50%,#9fc0d6)'
    }
  };

  /* Motywy mgły: gradient tła, kolor tekstury i siła tekstury */
  const FOG_THEMES = {
    mist:      { name: 'Poranna mgła', top: '#eef0f8', bottom: '#d9dcec', tint: '#ffffff', shade: '#aeb3cc', texture: .55 },
    lavender:  { name: 'Lawenda',      top: '#ece6f6', bottom: '#d6cdea', tint: '#ffffff', shade: '#a596c7', texture: .5 },
    parchment: { name: 'Pergamin',     top: '#f3ead6', bottom: '#e2d3b2', tint: '#fffaf0', shade: '#b39d72', texture: .65 },
    sky:       { name: 'Obłoki',       top: '#e3f0fb', bottom: '#c7dcf0', tint: '#ffffff', shade: '#8fb0d2', texture: .6 },
    mint:      { name: 'Mięta',        top: '#e3f4ee', bottom: '#c6e4d8', tint: '#ffffff', shade: '#86b9a5', texture: .5 },
    night:     { name: 'Noc',          top: '#1d2340', bottom: '#11152a', tint: '#3b4577', shade: '#05060f', texture: .7, dark: true },
    ink:       { name: 'Atrament',     top: '#22252b', bottom: '#15171b', tint: '#3a3e47', shade: '#000000', texture: .6, dark: true }
  };

  /* ---------- Parsery plików importu ---------- */

  function parseGPX(text) {
    const doc = new DOMParser().parseFromString(text, 'application/xml');
    if (doc.querySelector('parsererror')) throw new Error('Nieprawidłowy plik GPX');
    const segments = [];
    const pushPts = (nodes) => {
      const seg = [];
      nodes.forEach(n => {
        const lat = parseFloat(n.getAttribute('lat')), lon = parseFloat(n.getAttribute('lon'));
        if (!isFinite(lat) || !isFinite(lon)) return;
        const t = n.getElementsByTagName('time')[0];
        seg.push({ lat, lon, time: t ? Date.parse(t.textContent) : null });
      });
      if (seg.length) segments.push(seg);
    };
    doc.querySelectorAll('trkseg').forEach(s => pushPts(s.querySelectorAll('trkpt')));
    doc.querySelectorAll('rte').forEach(r => pushPts(r.querySelectorAll('rtept')));
    const wpts = doc.querySelectorAll('wpt');
    if (wpts.length) wpts.forEach(w => pushPts([w]));
    return segments;
  }

  function parseKML(text) {
    const doc = new DOMParser().parseFromString(text, 'application/xml');
    if (doc.querySelector('parsererror')) throw new Error('Nieprawidłowy plik KML');
    const segments = [];
    doc.querySelectorAll('coordinates').forEach(c => {
      const seg = c.textContent.trim().split(/\s+/).map(tok => {
        const [lon, lat] = tok.split(',').map(Number);
        return { lat, lon, time: null };
      }).filter(p => isFinite(p.lat) && isFinite(p.lon));
      if (seg.length) segments.push(seg);
    });
    // gx:Track
    doc.querySelectorAll('Track').forEach(tr => {
      const whens = tr.getElementsByTagName('when');
      const coords = tr.getElementsByTagName('gx:coord').length ? tr.getElementsByTagName('gx:coord') : tr.getElementsByTagName('coord');
      const seg = [];
      for (let i = 0; i < coords.length; i++) {
        const [lon, lat] = coords[i].textContent.trim().split(/\s+/).map(Number);
        if (isFinite(lat) && isFinite(lon)) seg.push({ lat, lon, time: whens[i] ? Date.parse(whens[i].textContent) : null });
      }
      if (seg.length) segments.push(seg);
    });
    return segments;
  }

  function parseGeoJSON(obj) {
    const segments = [];
    const line = (coords, time) => {
      const seg = coords.map((c, i) => ({ lat: c[1], lon: c[0], time: time && time[i] ? Date.parse(time[i]) : null }))
        .filter(p => isFinite(p.lat) && isFinite(p.lon));
      if (seg.length) segments.push(seg);
    };
    const geom = (g, props) => {
      if (!g) return;
      const times = props && (props.coordTimes || props.times);
      switch (g.type) {
        case 'Point': line([g.coordinates]); break;
        case 'MultiPoint': g.coordinates.forEach(c => line([c])); break;
        case 'LineString': line(g.coordinates, times); break;
        case 'MultiLineString': g.coordinates.forEach((l, i) => line(l, times && times[i])); break;
        case 'Polygon': g.coordinates.forEach(l => line(l)); break;
        case 'MultiPolygon': g.coordinates.forEach(p => p.forEach(l => line(l))); break;
        case 'GeometryCollection': g.geometries.forEach(x => geom(x)); break;
      }
    };
    if (obj.type === 'FeatureCollection') obj.features.forEach(f => geom(f.geometry, f.properties));
    else if (obj.type === 'Feature') geom(obj.geometry, obj.properties);
    else geom(obj);
    return segments;
  }

  /* Historia lokalizacji Google (Takeout: Records.json lub nowy format z telefonu) */
  function parseGoogleHistory(obj) {
    const pts = [];
    if (Array.isArray(obj.locations)) {
      obj.locations.forEach(l => {
        const lat = l.latitudeE7 / 1e7, lon = l.longitudeE7 / 1e7;
        if (isFinite(lat) && isFinite(lon)) pts.push({ lat, lon, time: l.timestamp ? Date.parse(l.timestamp) : (l.timestampMs ? +l.timestampMs : null) });
      });
    } else if (Array.isArray(obj.semanticSegments) || Array.isArray(obj)) {
      const segs = obj.semanticSegments || obj;
      const parseGeo = s => {
        const m = /(-?\d+\.\d+)°?,\s*(-?\d+\.\d+)/.exec((s || '').replace('geo:', ''));
        return m ? [parseFloat(m[1]), parseFloat(m[2])] : null;
      };
      segs.forEach(seg => {
        (seg.timelinePath || []).forEach(p => {
          const c = parseGeo(p.point);
          if (c) pts.push({ lat: c[0], lon: c[1], time: p.time ? Date.parse(p.time) : null });
        });
        const v = seg.visit && seg.visit.topCandidate && seg.visit.topCandidate.placeLocation;
        const c = v && parseGeo(v.latLng || v);
        if (c) pts.push({ lat: c[0], lon: c[1], time: seg.startTime ? Date.parse(seg.startTime) : null });
      });
    }
    pts.sort((a, b) => (a.time || 0) - (b.time || 0));
    return pts.length ? [pts] : [];
  }

  global.Geo = {
    EARTH_R, EARTH_AREA_KM2, DEG, M_PER_DEG_LAT,
    haversine, dayNumber, dayToDate, formatDay, formatDistance, formatArea, formatPercent, plural, flagEmoji,
    BASE_MAPS, FOG_THEMES, parseGPX, parseKML, parseGeoJSON, parseGoogleHistory
  };
})(window);
