/* Odkrywca: warstwa mgły rysowana na canvasie nad mapą */
(function (global) {
  'use strict';

  const { M_PER_DEG_LAT } = global.Geo;
  const TEX = 384;

  /* Proceduralna tekstura chmur (raz, w pamięci). */
  function makeCloudTexture(theme) {
    const c = document.createElement('canvas');
    c.width = c.height = TEX;
    const ctx = c.getContext('2d');
    let seed = 1337;
    const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
    const blob = (color, count, rMin, rMax, alpha) => {
      for (let n = 0; n < count; n++) {
        const x = rnd() * TEX, y = rnd() * TEX, r = rMin + rnd() * (rMax - rMin);
        // rysujemy z zawinięciem, żeby tekstura była bezszwowa
        for (const ox of [-TEX, 0, TEX]) for (const oy of [-TEX, 0, TEX]) {
          const g = ctx.createRadialGradient(x + ox, y + oy, 0, x + ox, y + oy, r);
          g.addColorStop(0, hexA(color, alpha * (0.4 + rnd() * 0.6)));
          g.addColorStop(1, hexA(color, 0));
          ctx.fillStyle = g;
          ctx.fillRect(x + ox - r, y + oy - r, r * 2, r * 2);
        }
      }
    };
    blob(theme.shade, 26, 40, 140, 0.22);
    blob(theme.tint, 34, 30, 120, 0.35);
    blob(theme.shade, 60, 6, 24, 0.10);
    blob(theme.tint, 60, 6, 20, 0.18);
    return c;
  }

  function hexA(hex, a) {
    const h = hex.replace('#', '');
    const n = parseInt(h.length === 3 ? h.split('').map(x => x + x).join('') : h, 16);
    return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
  }

  const FogLayer = L.Layer.extend({
    options: { padding: 0.35, pane: 'fogPane' },

    initialize(opts) {
      L.setOptions(this, opts);
      this.maxDay = null;
      this._raf = null;
      this.setTheme(opts.theme);
    },

    setTheme(theme) {
      this.theme = theme;
      this._texture = makeCloudTexture(theme);
      this._pattern = null;
      this.redraw();
    },

    setMaxDay(d) { this.maxDay = d; this.redraw(); },

    onAdd(map) {
      if (!map.getPane(this.options.pane)) {
        const p = map.createPane(this.options.pane);
        p.style.zIndex = 350;
        p.style.pointerEvents = 'none';
      }
      this._canvas = L.DomUtil.create('canvas', 'fog-canvas leaflet-zoom-animated');
      this._ctx = this._canvas.getContext('2d');
      this._canvas.style.opacity = this.options.opacity;
      this.getPane().appendChild(this._canvas);
      this._update();
    },

    onRemove() {
      L.DomUtil.remove(this._canvas);
      this._canvas = null;
    },

    getEvents() {
      return {
        viewreset: this._update,
        zoomanim: this._onZoomAnim,
        moveend: this._update,
        zoomend: this._update,
        resize: this._update,
        move: this._onMove,
        zoom: this._onMove
      };
    },

    _onMove() {
      if (this._map && !this._map._animatingZoom) this.redraw();
    },

    _onZoomAnim(ev) {
      const map = this._map;
      const scale = map.getZoomScale(ev.zoom, this._zoom);
      // jak w L.Renderer: przesuwamy i skalujemy płótno podczas animacji
      const viewHalf = map.getSize().multiplyBy(0.5 + this.options.padding);
      const currentCenterPoint = map.project(this._center, ev.zoom);
      const topLeft = viewHalf.multiplyBy(-scale).add(currentCenterPoint).subtract(map._getNewPixelOrigin(ev.center, ev.zoom));
      L.DomUtil.setTransform(this._canvas, topLeft, scale);
    },

    redraw() {
      if (!this._map || this._raf) return;
      this._raf = requestAnimationFrame(() => { this._raf = null; this._update(); });
    },

    _update() {
      const map = this._map;
      if (!map || !this._canvas) return;
      const p = this.options.padding;
      const size = map.getSize();
      const min = map.containerPointToLayerPoint(size.multiplyBy(-p)).round();
      const w = Math.round(size.x * (1 + 2 * p)), h = Math.round(size.y * (1 + 2 * p));
      this._center = map.getCenter();
      this._zoom = map.getZoom();
      this._min = min;
      L.DomUtil.setPosition(this._canvas, min);

      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      const cw = Math.round(w * dpr), ch = Math.round(h * dpr);
      const c = this._canvas;
      if (c.width !== cw || c.height !== ch) {
        c.width = cw; c.height = ch;
        c.style.width = w + 'px'; c.style.height = h + 'px';
      }
      this._draw(cw, ch, dpr, min, w, h);
    },

    _draw(cw, ch, dpr, min, w, h) {
      const ctx = this._ctx, map = this._map, theme = this.theme;
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.globalCompositeOperation = 'source-over';
      ctx.shadowColor = 'transparent';
      ctx.shadowBlur = 0;
      ctx.shadowOffsetX = 0;
      ctx.clearRect(0, 0, cw, ch);

      // 1. tło mgły
      const g = ctx.createLinearGradient(0, 0, 0, ch);
      g.addColorStop(0, theme.top);
      g.addColorStop(1, theme.bottom);
      ctx.fillStyle = g;
      ctx.fillRect(0, 0, cw, ch);

      // 2. tekstura chmur zakotwiczona w świecie (przesuwa się razem z mapą)
      if (!this._pattern) this._pattern = ctx.createPattern(this._texture, 'repeat');
      const origin = map.getPixelOrigin();
      const wx = (min.x + origin.x) * dpr, wy = (min.y + origin.y) * dpr;
      const tScale = dpr * 1.5;
      const tw = TEX * tScale;
      if (this._pattern.setTransform) {
        const ox = -(((wx % tw) + tw) % tw), oy = -(((wy % tw) + tw) % tw);
        this._pattern.setTransform(new DOMMatrix().translate(ox, oy).scale(tScale));
      }
      ctx.globalAlpha = theme.texture;
      ctx.fillStyle = this._pattern;
      ctx.fillRect(0, 0, cw, ch);
      ctx.globalAlpha = 1;

      // 3. wycinanie odkrytych miejsc z miękką krawędzią
      const sw = map.layerPointToLatLng([min.x, min.y + h]);
      const ne = map.layerPointToLatLng([min.x + w, min.y]);
      const center = map.getCenter();
      const zoom = map.getZoom();
      const metersPerPx = 40075016.686 * Math.cos(center.lat * Math.PI / 180) / Math.pow(2, zoom + 8);
      const radiusM = global.Store.RADIUS;
      const rPx = radiusM / metersPerPx;
      const r = Math.max(rPx * 0.9, 2.5) * dpr;
      const blur = Math.min(Math.max(rPx * 0.5, 0.8), 60) * dpr;
      const marginDeg = (radiusM * 2) / M_PER_DEG_LAT;

      const OFF = cw + 4 * blur + 1000;
      ctx.globalCompositeOperation = 'destination-out';
      ctx.fillStyle = '#000';
      ctx.shadowColor = '#000';
      ctx.shadowBlur = blur;   // sigma ≈ blur / 2, miękka, ale czysta krawędź
      ctx.shadowOffsetX = OFF;
      ctx.beginPath();
      let count = 0;
      const mx = -min.x, my = -min.y;
      global.Store.eachPointIn(sw.lat - marginDeg, sw.lng - marginDeg * 3, ne.lat + marginDeg, ne.lng + marginDeg * 3, this.maxDay, (lat, lon) => {
        const pt = map.latLngToLayerPoint([lat, lon]);
        const x = (pt.x + mx) * dpr - OFF, y = (pt.y + my) * dpr;
        ctx.moveTo(x + r, y);
        ctx.arc(x, y, r, 0, Math.PI * 2);
        if (++count % 4000 === 0) { ctx.fill(); ctx.beginPath(); }
      });
      ctx.fill();

      ctx.shadowColor = 'transparent';
      ctx.shadowBlur = 0;
      ctx.shadowOffsetX = 0;
      ctx.globalCompositeOperation = 'source-over';
    }
  });

  global.FogLayer = FogLayer;
})(window);
