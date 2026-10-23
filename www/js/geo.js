/* Odkrywca: funkcje geograficzne, formatowanie, mapy podkładowe */
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

  function plural(n, one, few, many) {
    if (n === 1) return one;
    const n10 = n % 10, n100 = n % 100;
    if (n10 >= 2 && n10 <= 4 && (n100 < 12 || n100 > 14)) return few;
    return many;
  }

  const BASE_MAPS = {
    streets: {
      name: 'Plan',
      url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}',
      attribution: 'Esri, HERE, Garmin, OpenStreetMap',
      maxZoom: 19
    },
    topo: {
      name: 'Teren',
      url: 'https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png',
      attribution: '<a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>, SRTM, <a href="https://opentopomap.org">OpenTopoMap</a>',
      subdomains: 'abc', maxZoom: 17
    },
    satellite: {
      name: 'Satelita',
      url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
      attribution: 'Esri, Maxar, Earthstar Geographics',
      maxZoom: 19
    }
  };

  /* Mgła: spokojny papierowy kolor w jasnym trybie, grafit w ciemnym */
  const FOG = {
    light: { top: '#e4e2dc', bottom: '#dcdad3', tint: '#f3f2ee', shade: '#b9b6ad', texture: 0.45 },
    dark: { top: '#363b39', bottom: '#2f3432', tint: '#454b48', shade: '#1f2321', texture: 0.5 }
  };

  global.Geo = {
    EARTH_R, EARTH_AREA_KM2, DEG, M_PER_DEG_LAT,
    haversine, dayNumber, dayToDate, formatDay, formatDistance, formatArea, plural,
    BASE_MAPS, FOG
  };
})(window);
