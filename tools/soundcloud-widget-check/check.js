// Ładuje app/src/main/assets/soundcloud_player.html z tego samego adresu bazowego co w aplikacji
// i steruje prawdziwym widżetem SoundCloud przez nasze API `rytm.*`.
const { chromium } = require('playwright');
const fs = require('fs');
const html = fs.readFileSync(process.argv[2], 'utf8');
const track = process.argv[3] || 'https://soundcloud.com/forss/flickermood';

(async () => {
  const browser = await chromium.launch({
    args: ['--autoplay-policy=no-user-gesture-required'],
    proxy: process.env.HTTPS_PROXY ? { server: process.env.HTTPS_PROXY } : undefined,
  });
  const page = await browser.newPage({ ignoreHTTPSErrors: false });
  await page.addInitScript(() => {
    window.__bridge = [];
    window.RytmBridge = new Proxy({}, { get: (_, name) => (data) => window.__bridge.push([String(name), data, Date.now()]) });
  });
  await page.route('https://appassets.androidplatform.net/player/**', route =>
    route.fulfill({ status: 200, contentType: 'text/html; charset=utf-8', body: html }));
  await page.goto('https://appassets.androidplatform.net/player/');
  const events = () => page.evaluate(() => window.__bridge.map(e => e[0] + (e[1] ? ' ' + e[1].slice(0, 160) : '')));
  const waitFor = async (name, ms) => {
    const t0 = Date.now();
    while (Date.now() - t0 < ms) {
      const ev = await page.evaluate(n => window.__bridge.some(e => e[0] === n), name);
      if (ev) return true;
      await new Promise(r => setTimeout(r, 200));
    }
    return false;
  };
  console.log('pageLoaded:', await waitFor('onPageLoaded', 10000));
  const widgetUrl = 'https://w.soundcloud.com/player/?url=' + encodeURIComponent(track) +
    '&auto_play=true&visual=false&show_comments=false&buying=false&sharing=false&download=false&show_teaser=false&single_active=false&hide_related=true';
  await page.evaluate(u => rytm.load(u, true, 100, true), widgetUrl);
  console.log('ready:', await waitFor('onReady', 30000));
  console.log('sound:', await waitFor('onSound', 15000));
  console.log('play:', await waitFor('onPlay', 15000));
  console.log('progress:', await waitFor('onProgress', 15000));
  await page.evaluate(() => rytm.setVolume(75));
  await new Promise(r => setTimeout(r, 500));
  await page.evaluate(() => rytm.getVolume());
  console.log('volume:', await waitFor('onVolume', 5000));
  await page.evaluate(() => rytm.pause());
  console.log('pause:', await waitFor('onPause', 5000));
  await page.evaluate(() => rytm.play());
  await new Promise(r => setTimeout(r, 3000));
  console.log('--- events ---');
  (await events()).forEach(e => console.log('  ' + e));
  await browser.close();
})().catch(e => { console.error('ERROR', e); process.exit(1); });
