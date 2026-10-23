/* Odkrywca: service worker. Aplikacja działa offline, kafelki mapy są buforowane. */
const VERSION = 'odkrywca-v4';
const SHELL = [
  './', 'index.html', 'css/style.css', 'manifest.webmanifest', 'icons/icon.svg',
  'js/geo.js', 'js/store.js', 'js/fog.js', 'js/achievements.js', 'js/app.js',
  'vendor/leaflet/leaflet.css', 'vendor/leaflet/leaflet.js'
];
const TILE_CACHE = 'odkrywca-tiles';
const TILE_LIMIT = 1500;
const TILE_HOSTS = /tile\.openstreetmap\.org|tile\.opentopomap\.org|arcgisonline\.com/;

self.addEventListener('install', e => {
  e.waitUntil(caches.open(VERSION).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(keys => Promise.all(
    keys.filter(k => k !== VERSION && k !== TILE_CACHE).map(k => caches.delete(k))
  )).then(() => self.clients.claim()));
});

let putsSinceTrim = 0;
async function trimTiles() {
  if (++putsSinceTrim < 100) return;
  putsSinceTrim = 0;
  const cache = await caches.open(TILE_CACHE);
  const keys = await cache.keys();
  for (let i = 0; i < keys.length - TILE_LIMIT; i++) await cache.delete(keys[i]);
}

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);

  // kafelki mapy: najpierw sieć, w razie braku pamięć podręczna
  if (TILE_HOSTS.test(url.hostname)) {
    e.respondWith(
      fetch(req).then(res => {
        if (res.ok) {
          const copy = res.clone();
          caches.open(TILE_CACHE).then(c => c.put(req, copy)).then(trimTiles);
        }
        return res;
      }).catch(() => caches.match(req))
    );
    return;
  }

  // wyszukiwanie miejsc zawsze z sieci
  if (url.hostname.includes('nominatim')) return;

  // pliki aplikacji: najpierw sieć (świeże wersje), offline z pamięci
  e.respondWith(
    fetch(req).then(res => {
      if (res.ok && url.origin === location.origin) {
        const copy = res.clone();
        caches.open(VERSION).then(c => c.put(req, copy));
      }
      return res;
    }).catch(() => caches.match(req).then(r => r || caches.match('index.html')))
  );
});
