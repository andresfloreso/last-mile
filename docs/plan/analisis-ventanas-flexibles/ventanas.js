// Figuras del análisis de ventanas flexibles (#87). Uso: OUT=dir DATA=resultados.json node ventanas.js
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");
const OUT = process.env.OUT;
const data = JSON.parse(fs.readFileSync(process.env.DATA, "utf8"));
const page_ = path.join(__dirname, "figura-ventanas.html");

(async () => {
  const browser = await chromium.launch();
  for (const [key, title, sub] of [
    ["hoy", "Día de hoy de la semilla: 24 paradas", "Mismo camión, mismos locales y mismos tiempos de viaje (OSRM). Cambia solo cómo se ordena la ruta."],
    ["dia-cargado", "Día cargado (4 de enero de 2027): 40 paradas", "Mismo camión, mismos locales y mismos tiempos de viaje (OSRM). Cambia solo cómo se ordena la ruta."],
  ]) {
    const page = await browser.newPage({ viewport: { width: 1400, height: 760 } });
    page.on("pageerror", (e) => console.log("pageerror", e.message));
    await page.goto("file://" + page_);
    await page.evaluate(([all, k, t, s]) => window.render(all, k, t, s), [data, key, title, sub]);
    await page.screenshot({ path: path.join(OUT, `${key}.png`), fullPage: true });
    await page.close();
    console.log("ok", key);
  }
  await browser.close();
})();
