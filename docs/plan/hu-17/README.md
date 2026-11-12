# HU-17 — Confirmar la ruta y asignarla a un conductor

Capturas de `/` (Ruta del día) con la semilla del 2026-11-12, despacho
(`despacho@lastmile.cl`). Se regeneran con `capturas.js`
(`OUT=docs/plan/hu-17 node capturas.js`, con el stack arriba y el frontend en
`http://localhost:5173`).

| Paso | Captura |
|---|---|
| 1. Ruta recién armada: chip **Borrador**; se puede reoptimizar y editar el orden | `1-borrador.png` |
| 2. **Confirmar y asignar**: despacho elige un conductor activo | `2-elegir-conductor.png` |
| 3. Ruta **Confirmada**, "Asignada a Carlos Conductor". Reoptimizar está deshabilitado y "Editar orden" desaparece | `3-confirmada.png` |
| 4. **Volver a borrador** libera al conductor y permite cambiarla otra vez | `4-de-vuelta-a-borrador.png` |
