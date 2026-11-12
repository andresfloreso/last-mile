// HU-17: capturas de confirmar y asignar. Uso: OUT=dir HOY=2026-11-12 node hu17.js
const { chromium } = require("playwright");
const BASE = process.env.BASE || "http://localhost:5173";
const OUT = process.env.OUT;
const HOY = process.env.HOY || "2026-11-12";

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: "light" });
  const page = await ctx.newPage();
  page.on("pageerror", (e) => console.log("pageerror", e.message));
  await page.goto(`${BASE}/login`);
  await page.fill('input[type="email"]', "despacho@lastmile.cl");
  await page.fill('input[type="password"]', "lastmile123");
  await page.click('button[type="submit"]');
  await page.waitForURL(`${BASE}/`);
  await page.locator('input[type="date"]').first().fill(HOY);
  await page.getByRole("option").first().waitFor({ timeout: 90000 });
  await page.waitForTimeout(2000);
  console.log("estado:", await page.getByTestId("route-status").innerText());
  await page.screenshot({ path: `${OUT}/1-borrador.png` });

  await page.getByRole("button", { name: "Confirmar y asignar" }).click();
  await page.getByRole("dialog").getByRole("combobox").waitFor({ timeout: 15000 });
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${OUT}/2-elegir-conductor.png` });
  await page.getByRole("button", { name: "Confirmar ruta" }).click();
  await page.getByRole("dialog").waitFor({ state: "detached", timeout: 15000 });
  await page.waitForTimeout(800);
  console.log("estado:", await page.getByTestId("route-status").innerText());
  console.log("editar orden visible:", await page.getByRole("button", { name: "Editar orden" }).count());
  console.log("reoptimizar habilitado:", await page.getByRole("button", { name: "Reoptimizar" }).isEnabled());
  await page.screenshot({ path: `${OUT}/3-confirmada.png` });

  await page.getByRole("button", { name: "Volver a borrador" }).click();
  await page.getByRole("button", { name: "Confirmar y asignar" }).waitFor({ timeout: 15000 });
  await page.waitForTimeout(500);
  console.log("estado:", await page.getByTestId("route-status").innerText());
  await page.screenshot({ path: `${OUT}/4-de-vuelta-a-borrador.png` });
  await browser.close();
})();
