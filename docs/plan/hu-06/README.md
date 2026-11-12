# HU-06 — La sesión se renueva sola

Capturas tomadas con Playwright (`capturas.js`) sobre la semilla, como despacho.
El script reemplaza los tokens guardados en `localStorage` por tokens vencidos
para simular la expiración sin esperar una hora. El recuadro oscuro lo agrega el
script y muestra las llamadas que hizo la página.

| Caso | Captura |
|---|---|
| Access token vencido: `401` → `POST /api/auth/refresh` `200` → se reintenta la request y la pantalla carga | `1-token-vencido-se-renueva.png` |
| Access y refresh token vencidos: el refresh responde `401` → se cierra la sesión y se vuelve a `/login` | `2-refresh-vencido-vuelve-al-login.png` |
