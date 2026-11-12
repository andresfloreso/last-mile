// HU-32: consultas a OSRM, llamadas a la API y fluidez del mapa, antes y después.
// Uso: con el stack arriba y infra/seed/tools/contar_osrm.py en el puerto 5099,
// TAG=antes node medir-pantalla.js (necesita playwright 1.48; ver docs/plan/hu-32/medicion.md).
// HOY=aaaa-mm-dd: un día con ruta armada (por defecto el de la medición original); BASE: URL del frontend.
const { chromium } = require("playwright");
const BASE = process.env.BASE || "http://localhost:5173";
const PROXY = "http://localhost:5099";
const count = async (path) => (await fetch(PROXY + path)).json();
const osrmTotal = (c) => Object.values(c).reduce((a, b) => a + b, 0);

async function session(browser, email) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: "light" });
  const page = await ctx.newPage();
  const api = [];
  page.on("request", (r) => { const u = new URL(r.url()); if (u.pathname.startsWith("/api/routes")) api.push(`${r.method()} ${u.pathname}`); });
  const statuses = [];
  page.on("response", (r) => { if (new URL(r.url()).pathname.startsWith("/api/routes")) statuses.push(r.status()); });
  await page.goto(`${BASE}/login`);
  await page.fill('input[type="email"]', email);
  await page.fill('input[type="password"]', "lastmile123");
  await page.click('button[type="submit"]');
  await page.waitForURL(`${BASE}/`);
  return { ctx, page, api, statuses };
}

async function setDate(page, d) {
  await page.locator('input[type="date"]').first().fill(d);
  await page.waitForTimeout(500);
  await page.getByRole("option").first().waitFor({ timeout: 90000 });
  await page.waitForTimeout(1500);
}

(async () => {
  const browser = await chromium.launch();
  const out = { tag: process.env.TAG };

  // 1) Despachador: abrir, cambiar de día y volver, recargar.
  await count("/__reset");
  const d = await session(browser, "despacho@lastmile.cl");
  await setDate(d.page, "2027-01-04");
  await setDate(d.page, (process.env.HOY || "2026-11-10"));
  await setDate(d.page, "2027-01-04");
  await d.page.reload();
  await d.page.getByRole("option").first().waitFor({ timeout: 90000 });
  await d.page.waitForTimeout(1500);
  out.abrir = { api: [...d.api], osrm: await count("/__count") };
  out.abrir.osrmTotal = osrmTotal(out.abrir.osrm);

  // 2) Mover el mapa: 12 arrastres y 4 zooms, midiendo cuadros.
  await count("/__reset");
  d.api.length = 0;
  const box = await d.page.getByRole("region", { name: "Mapa de la ruta del día" }).boundingBox();
  await d.page.evaluate(() => {
    window.__frames = []; window.__long = 0; let last = performance.now();
    const loop = (t) => { window.__frames.push(t - last); last = t; if (window.__run) requestAnimationFrame(loop); };
    window.__run = true; requestAnimationFrame(loop);
    new PerformanceObserver((l) => l.getEntries().forEach((e) => (window.__long += e.duration))).observe({ type: "longtask", buffered: false });
  });
  const cdp = await d.ctx.newCDPSession(d.page);
  await cdp.send("Performance.enable");
  await cdp.send("Emulation.setCPUThrottlingRate", { rate: 4 });
  const metric = async () => Object.fromEntries((await cdp.send("Performance.getMetrics")).metrics.map((m) => [m.name, m.value]));
  const m0 = await metric();
  const cx = box.x + box.width / 2, cy = box.y + box.height / 2;
  const t0 = Date.now();
  for (let i = 0; i < 12; i++) {
    const dx = (i % 2 ? -1 : 1) * 250, dy = (i % 3 - 1) * 120;
    await d.page.mouse.move(cx, cy);
    await d.page.mouse.down();
    await d.page.mouse.move(cx + dx, cy + dy, { steps: 25 });
    await d.page.mouse.up();
  }
  for (const label of ["Acercar el mapa", "Acercar el mapa", "Alejar el mapa", "Alejar el mapa"]) {
    await d.page.getByRole("button", { name: label }).click();
    await d.page.waitForTimeout(400);
  }
  const wall = Date.now() - t0;
  const m1 = await metric();
  await cdp.send("Emulation.setCPUThrottlingRate", { rate: 1 });
  const perf = await d.page.evaluate(() => { window.__run = false; return { frames: window.__frames.slice(1), long: window.__long }; });
  const f = perf.frames.sort((a, b) => a - b);
  const pct = (p) => f[Math.min(f.length - 1, Math.floor(f.length * p))];
  out.mover = {
    api: [...d.api], osrm: await count("/__count"), ms: wall,
    cuadros: f.length, cuadroMedianoMs: +pct(0.5).toFixed(1), cuadroP95Ms: +pct(0.95).toFixed(1),
    cuadrosLentos: f.filter((x) => x > 50).length, tareasLargasMs: Math.round(perf.long),
    scriptMs: Math.round((m1.ScriptDuration - m0.ScriptDuration) * 1000),
    layoutYEstiloMs: Math.round((m1.LayoutDuration - m0.LayoutDuration + m1.RecalcStyleDuration - m0.RecalcStyleDuration) * 1000),
    cpuX: 4,
  };
  out.mover.osrmTotal = osrmTotal(out.mover.osrm);
  await d.ctx.close();

  // 3) Operador: abre la pantalla.
  await count("/__reset");
  const o = await session(browser, "operador@lastmile.cl");
  await o.page.locator('input[type="date"]').first().fill("2027-01-04");
  await o.page.waitForTimeout(8000);
  out.operador = { api: [...o.api], status: [...o.statuses], osrm: await count("/__count"),
    texto: (await o.page.locator("aside, [role=listbox], main").first().innerText()).slice(0, 300) };
  await o.page.screenshot({ path: `shots-hu32/${process.env.TAG}-operador.png` });
  await o.ctx.close();

  await browser.close();
  console.log(JSON.stringify(out, null, 1));
})().catch((e) => { console.error(e); process.exit(1); });
