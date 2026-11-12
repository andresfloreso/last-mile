// Captura el mapa a mitad de un arrastre y quieto en la misma posición (#86).
// Uso: OUT=dir TAG=antes node arrastre.js
const { chromium } = require("playwright");
const BASE = process.env.BASE || "http://localhost:5173", OUT = process.env.OUT, TAG = process.env.TAG;
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: "light" });
  const page = await ctx.newPage();
  await page.goto(`${BASE}/login`);
  await page.fill('input[type="email"]', "despacho@lastmile.cl");
  await page.fill('input[type="password"]', "lastmile123");
  await page.click('button[type="submit"]');
  await page.waitForURL(`${BASE}/`);
  await page.locator('input[type="date"]').first().fill("2027-01-04");
  await page.getByRole("option").first().waitFor({ timeout: 90000 });
  await page.waitForTimeout(2500);
  for (let i = 0; i < +(process.env.ZOOMS || 3); i++) { await page.getByRole("button", { name: "Acercar el mapa" }).click(); await page.waitForTimeout(700); }
  await page.waitForTimeout(1500);
  const region = page.getByRole("region", { name: "Mapa de la ruta del día" });
  const box = await region.boundingBox();
  const clip = { x: box.x, y: box.y, width: box.width, height: box.height };
  const cx = box.x + box.width / 2, cy = box.y + box.height / 2;
  const casos = [["izq", -350, 0], ["abajo", 0, 250], ["diag", 300, -200]];
  for (const [n, dx, dy] of casos) {
    await page.mouse.move(cx, cy);
    await page.mouse.down();
    await page.mouse.move(cx + dx, cy + dy, { steps: 20 });
    await page.waitForTimeout(300);
    await page.screenshot({ path: `${OUT}/${TAG}-${n}-arrastrando.png`, clip });
    await page.mouse.up();
    await page.waitForTimeout(1200);
    await page.screenshot({ path: `${OUT}/${TAG}-${n}-quieto.png`, clip });
    // vuelve al centro para el caso siguiente
    await page.mouse.move(cx + dx, cy + dy); await page.mouse.down();
    await page.mouse.move(cx, cy, { steps: 10 }); await page.mouse.up();
    await page.waitForTimeout(1200);
  }
  await browser.close();
})();
