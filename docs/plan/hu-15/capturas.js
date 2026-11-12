// HU-15: capturas del armado manual. Uso: OUT=dir HOY=2026-11-12 node hu15.js
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
  await page.waitForTimeout(2500);
  await page.screenshot({ path: `${OUT}/0-vista-normal.png` });

  await page.getByRole("button", { name: "Editar orden" }).click();
  const rows = page.getByTestId("editable-stop");
  await rows.first().waitFor({ timeout: 30000 });
  await page.waitForTimeout(1500);
  await page.screenshot({ path: `${OUT}/1-orden-original.png` });

  // Arrastra la parada 8 (Botillería Santa Rosa, recibe 12–14) al lugar 5, entre los minimarkets de la
  // mañana: mientras se arrastra, el mapa redibuja la línea con el orden nuevo.
  const src = rows.nth(7);
  const dst = rows.nth(4);
  await src.evaluate((el) => el.scrollIntoView({ block: "end" }));
  await page.waitForTimeout(300);
  const a = await src.boundingBox();
  await page.mouse.move(a.x + 60, a.y + a.height / 2);
  await page.mouse.down();
  await page.mouse.move(a.x + 60, a.y + a.height / 2 - 20, { steps: 5 });
  for (let i = 0; i < 8; i++) {
    const b = await dst.boundingBox();
    await page.mouse.move(b.x + 60, b.y + b.height / 2, { steps: 4 });
  }
  await page.waitForTimeout(600);
  await page.screenshot({ path: `${OUT}/2-arrastrando.png` });
  await page.mouse.up();
  await page.getByText("Recalculando horas…").waitFor({ state: "detached", timeout: 30000 }).catch(() => {});
  await page.waitForTimeout(1500);
  await page.screenshot({ path: `${OUT}/3-despues-de-mover.png` });
  console.log("lugar 5:", await rows.nth(4).getAttribute("aria-label"));

  // Aviso fuera de ventana: la parada movida queda en rojo; se muestra en la lista.
  const red = page.getByText("Fuera de ventana", { exact: true }).first();
  if (await red.count()) {
    await red.scrollIntoViewIfNeeded();
    await page.waitForTimeout(400);
    await page.screenshot({ path: `${OUT}/4-aviso-fuera-de-ventana.png` });
  }

  // Fija la parada 3 y reoptimiza el resto.
  await rows.first().scrollIntoViewIfNeeded();
  const third = await rows.nth(2).getAttribute("aria-label");
  await rows.nth(2).getByRole("button").click();
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${OUT}/5-parada-fijada.png` });
  await page.getByRole("button", { name: "Reoptimizar el resto" }).click();
  await page.getByRole("button", { name: "Reoptimizar el resto" }).waitFor({ timeout: 60000 });
  await page.waitForFunction(() => !document.body.innerText.includes("Reoptimizando"), null, { timeout: 60000 });
  await page.waitForTimeout(1500);
  console.log("antes, lugar 3:", third, "| después:", await rows.nth(2).getAttribute("aria-label"));
  await page.screenshot({ path: `${OUT}/6-reoptimizado.png` });

  // Deshacer vuelve al paso anterior.
  await page.getByRole("button", { name: "Deshacer" }).click();
  await page.waitForTimeout(800);
  console.log("deshacer, lugar 5:", await rows.nth(4).getAttribute("aria-label"));

  // Oscuro: el editor con el cambio.
  await page.screenshot({ path: `${OUT}/7-deshacer.png` });

  // Guardar: vuelve a la vista normal con el orden nuevo.
  await page.getByRole("button", { name: "Guardar ruta" }).click();
  await page.getByRole("button", { name: "Editar orden" }).waitFor({ timeout: 30000 });
  await page.waitForTimeout(2000);
  await page.screenshot({ path: `${OUT}/8-guardada.png` });
  await browser.close();
})();
