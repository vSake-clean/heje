const { chromium } = require('playwright');
const fs = require('fs');
const html = fs.readFileSync(process.argv[2], 'utf8');
(async () => {
  const browser = await chromium.launch({ args: ['--autoplay-policy=no-user-gesture-required'], proxy: process.env.HTTPS_PROXY ? { server: process.env.HTTPS_PROXY } : undefined });
  const page = await browser.newPage();
  await page.addInitScript(() => { window.__bridge = []; window.RytmBridge = new Proxy({}, { get: (_, n) => (d) => window.__bridge.push([String(n), d]) }); });
  await page.route('https://appassets.androidplatform.net/player/**', r => r.fulfill({ status: 200, contentType: 'text/html', body: html }));
  await page.goto('https://appassets.androidplatform.net/player/');
  const url = 'https://w.soundcloud.com/player/?url=' + encodeURIComponent('https://soundcloud.com/forss/flickermood') + '&auto_play=true';
  await page.evaluate(u => rytm.load(u, true, 100, true), url);
  const sleep = ms => new Promise(r => setTimeout(r, ms));
  for (let i = 0; i < 60; i++) { if (await page.evaluate(() => window.__bridge.some(e => e[0] === 'onProgress'))) break; await sleep(500); }
  const dur = await page.evaluate(() => JSON.parse(window.__bridge.filter(e => e[0] === 'onSound').pop()[1]).duration);
  console.log('duration', dur);
  await page.evaluate(d => rytm.seekTo(d - 2500), dur);
  await page.evaluate(() => { window.__bridge.length = 0; });
  await sleep(9000);
  const ev = await page.evaluate(() => window.__bridge.map(e => e[0] + ' ' + (e[1] || '').slice(0, 60)));
  ev.forEach(e => console.log('  ' + e));
  const positions = ev.filter(e => e.startsWith('onProgress')).map(e => JSON.parse(e.slice(11).replace(/,"relative.*$/, '}') ).position);
  console.log('positions', positions.map(Math.round).join(', '));
  console.log('looped:', positions.some(p => p < 10000));
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
