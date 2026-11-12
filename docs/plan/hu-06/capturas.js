// HU-06: la sesión se renueva sola. Uso: OUT=dir HOY=2026-11-12 node hu06.js
// Simula un access token vencido (y luego también un refresh token vencido) editando la sesión guardada.
const { chromium } = require("playwright");
const BASE = process.env.BASE || "http://localhost:5173";
const OUT = process.env.OUT;
const HOY = process.env.HOY || "2026-11-12";

async function note(page, lines) {
  await page.evaluate((lines) => {
    const d = document.createElement("div");
    d.style.cssText =
      "position:fixed;right:16px;bottom:16px;z-index:9999;max-width:460px;padding:12px 14px;border-radius:10px;" +
      "background:#111827;color:#f9fafb;font:13px/1.45 ui-monospace,monospace;box-shadow:0 4px 16px rgba(0,0,0,.3)";
    d.innerText = "Captura de prueba (HU-06)\n" + lines.join("\n");
    document.body.appendChild(d);
  }, lines);
}

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: "light" });
  const page = await ctx.newPage();
  const log = [];
  page.on("response", (r) => {
    const u = new URL(r.url());
    if (u.pathname.startsWith("/api/")) log.push(`${r.request().method()} ${u.pathname} → ${r.status()}`);
  });

  await page.goto(`${BASE}/login`);
  await page.fill('input[type="email"]', "despacho@lastmile.cl");
  await page.fill('input[type="password"]', "lastmile123");
  await page.click('button[type="submit"]');
  await page.waitForURL(`${BASE}/`);
  await page.locator('input[type="date"]').first().fill(HOY);
  await page.getByRole("option").first().waitFor({ timeout: 90000 });

  // 1. Access token vencido: la página sigue funcionando.
  const before = await page.evaluate(() => JSON.parse(localStorage.getItem("lastmile_session")).refreshToken);
  await page.evaluate(() => {
    const s = JSON.parse(localStorage.getItem("lastmile_session"));
    s.accessToken = "token.vencido.x";
    localStorage.setItem("lastmile_session", JSON.stringify(s));
  });
  log.length = 0;
  await page.goto(`${BASE}/envios`);
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(800);
  const after = await page.evaluate(() => JSON.parse(localStorage.getItem("lastmile_session")));
  console.log("url:", page.url());
  console.log(log.join("\n"));
  console.log("refresh token nuevo:", after.refreshToken !== before, "access nuevo:", after.accessToken !== "token.vencido.x");
  await note(page, [
    "Access token guardado reemplazado por uno vencido.",
    ...log.slice(0, 4),
    "→ la sesión se renovó y la pantalla cargó sin volver al login.",
  ]);
  await page.screenshot({ path: `${OUT}/1-token-vencido-se-renueva.png` });

  // 2. Access y refresh vencidos: vuelve al login.
  await page.evaluate(() => {
    const s = JSON.parse(localStorage.getItem("lastmile_session"));
    s.accessToken = "token.vencido.x";
    s.refreshToken = "refresh.vencido.x";
    localStorage.setItem("lastmile_session", JSON.stringify(s));
  });
  log.length = 0;
  await page.goto(`${BASE}/envios`);
  await page.waitForURL(`${BASE}/login`, { timeout: 15000 });
  await page.waitForTimeout(600);
  console.log("url:", page.url());
  console.log(log.join("\n"));
  console.log("sesión borrada:", (await page.evaluate(() => localStorage.getItem("lastmile_session"))) === null);
  await note(page, ["Access y refresh token vencidos.", ...log.slice(0, 4), "→ se cerró la sesión y volvió a /login."]);
  await page.screenshot({ path: `${OUT}/2-refresh-vencido-vuelve-al-login.png` });
  await browser.close();
})();
