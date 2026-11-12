# HU-15: armado manual de la ruta

Capturas de Playwright sobre la semilla (jueves 12 de noviembre de 2026, 24
paradas, un camión). Se generan con [`capturas.js`](capturas.js)
(`OUT=dir HOY=2026-11-12 node capturas.js`, con la app en `localhost:5173`).

| Archivo | Qué muestra |
|---|---|
| `0-vista-normal.png` | Vista normal: la línea va por tramos, del color de la hora a la que el camión maneja cada uno |
| `1-orden-original.png` | Al entrar a "Editar orden": indicadores iguales al sistema, descarga por parada, candados |
| `2-arrastrando.png` | Mientras se arrastra la Botillería Santa Rosa al lugar 5: el mapa ya dibuja el orden nuevo |
| `3-despues-de-mover.png` | Al soltar: horas recalculadas, avisos rojos "Fuera de ventana", +7,2 km y +6 fuera de ventana frente al sistema, línea gris punteada de la ruta original |
| `5-parada-fijada.png` | La parada 3 fijada con el candado |
| `6-reoptimizado.png` | "Reoptimizar el resto": la fijada sigue tercera y el resto vuelve al orden del sistema |
| `8-guardada.png` | Después de guardar: la vista normal muestra el orden guardado |

Referencia visual: [`docs/design/edicion-manual.png`](../../design/edicion-manual.png)
(un camión) y [`docs/design/multicamion.png`](../../design/multicamion.png)
(vista futura de HU-16). El mockup usa flechas ↑ ↓; la app arrastra (y con el
teclado, Alt + ↑ / ↓). Los porcentajes "probabilidad de que reciba" son de
HU-37.

## Decisiones

- **Descarga por parada** = base (`routing.stop-minutes`, 8) + 1,5 min por
  bulto + ajuste del local (`V5`). Antes eran 10 min fijos. Con la semilla, la
  descarga va de 10 min (1 bulto) a 21 min (Santa Rosa: 2 bultos + 10 min de
  ajuste); la última parada de hoy sale 19:29 y el camión vuelve a la bodega
  19:36.
- **Vista previa sin escribir** (`POST /api/routes/preview`): cada cambio pide
  la ruta al backend, que calcula las horas con OSRM y la misma regla que el
  armado. Mientras se arrastra, la línea se dibuja en el navegador (tramos ya
  conocidos por calle y rectas para los nuevos) y las horas se recalculan al
  soltar.
- **Indicadores vs. sistema**: la línea base es la ruta guardada al entrar a
  editar.
- **Reoptimizar el resto**: el optimizador ordena las paradas sin candado y
  las pone en los lugares libres. No sabe a qué hora pasa el camión por las
  fijadas: si se fija una parada de la tarde al principio, el resto no lo
  compensa. Queda anotado como límite para HU-16/HU-37.
- **Columnas por camión**: `TruckColumn` recibe las paradas de un camión; el
  panel las pone en una fila con scroll horizontal. HU-16 agrega una columna
  por camión y el arrastre entre columnas.
- **Guardar** (`PUT /api/routes/order`) solo cambia la posición. HU-17 bloquea
  la edición de una ruta confirmada.
