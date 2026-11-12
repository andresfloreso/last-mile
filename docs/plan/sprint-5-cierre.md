# Sprint 5: cierre

- **Milestone:** Sprint 5 (11 ene – 22 ene 2027)
- **Objetivo (plan v4):** mapa confiable, armado manual y ruta confirmada. El
  despachador:
  - corrige la ruta arrastrando paradas;
  - ve cuándo una queda fuera de su ventana real;
  - la confirma.

  La línea del mapa sigue las calles sin vueltas y no parpadea.
- **Compromiso:**
  - historias: HU-06, HU-12, HU-15 y HU-17, 22 puntos;
  - sin puntos: los arreglos del mapa (#84, #85, #86 y #57), #70 y el análisis
    de ventanas flexibles (#87).
- **Estado al 12 nov 2026:**
  - Desarrollo terminado.
  - Todo está en PRs abiertos, con el CI en verde. No se mergeó nada: el merge
    queda para el dueño del repo.

## Completado

| Ítem | Pts | Issue | PR | Criterios | Evidencia |
|---|---|---|---|---|---|
| Plan v4 | — | — | #88 | — | `plan-de-trabajo.md` v4: HU-15 adelantada, HU-36 y HU-37 nuevas |
| Auditoría de coordenadas | — | #84 | #89 | 3/3 | 0 de 40 locales a más de 100 m de Nominatim; Av. Santa Rosa 500 está bien ubicada |
| Vueltas de la ruta | — | #85 | #90 | 3/3 | 8 locales de esquina enganchaban en la calle transversal; la vuelta en Santa Rosa la obliga el sentido del tránsito |
| Redibujo de la polilínea | — | #86 | #91 | 2/2 | Línea visible a mitad del arrastre: 60–88 % → 100 % |
| Proveedor del mapa | — | #57 | #92 | 1/3 | Stadia por defecto, con detalle hasta zoom 20. **#57 queda abierto:** falta que el dueño elija el proveedor y cargue la key de staging |
| Ruta solo con envíos abiertos | — | #70 | #93 | 3/3 | `ClosedShipmentsRouteIT` |
| **HU-12** Decaimiento y perfil en Redis | 8 | #79 | #95 | 4/4 | Vida media de 90 días; la caché se invalida al registrar un intento; Redis real en los IT |
| **HU-15** Armado manual de la ruta | 8 | #78 | #96 | 10/10 | `docs/plan/hu-15/` (capturas de Playwright) |
| **HU-17** Confirmar y asignar la ruta | 3 | #80 | #97 | 5/5 | `docs/plan/hu-17/`; `RouteStateIT` |
| **HU-06** Renovación de la sesión | 3 | #81 | #98 | 2/2 | `docs/plan/hu-06/`; 7 tests del interceptor |
| Análisis de ventanas flexibles | — | #87 | #100 | 3/3 | `docs/plan/analisis-ventanas-flexibles.md` |

**Puntos completados: 22/22**, todos en PRs abiertos. 0 en `main`.

### HU-15 (#96): armado manual

Lo que hace el despachador:

- arrastra paradas, con la línea del mapa en vivo;
- fija paradas con candado y usa "Reoptimizar el resto";
- deshace cambios;
- ve los indicadores (km, vuelta a bodega y fuera de ventana), cada uno con su
  diferencia frente a la ruta del sistema.

La línea se pinta según la hora de manejo, y la ruta original queda en gris
punteado. La descarga por parada es 8 min + 1,5 por bulto + el ajuste de cada
local (`V5`). Endpoints nuevos: `POST /api/routes/preview` y
`PUT /api/routes/order`.

### HU-17 (#97): ruta como entidad

- Tabla `routes` (`V6`): una ruta por fecha, con estado (borrador, confirmada,
  en curso, cerrada), optimizador, camión y conductor.
- `build` y el guardado manual responden `409` si la ruta no es un borrador.
- `GET /api/routes` ya trae el optimizador y el camión.
- `GET /api/users/drivers` (auth-service) lista los conductores para el diálogo
  "Confirmar y asignar".

### HU-06 (#98): sesión que se renueva sola

- Ante un `401`, el frontend renueva el token y reintenta. Varias requests a la
  vez comparten un solo refresh.
- Si el refresh falla, cierra la sesión y vuelve a `/login`.
- `POST /api/auth/refresh` ya no renueva la sesión de un usuario deshabilitado.
  Antes, con la renovación automática, ese usuario seguía dentro hasta 7 días.

### Análisis de ventanas flexibles (#100)

Prototipo de HU-37 detrás de `routing.optimizer.objective=risk`, apagado por
defecto. La medición sobre los dos días de la semilla, con OSRM:

| | Hoy: ruta actual | Hoy: prototipo | Día cargado: ruta actual | Día cargado: prototipo |
|---|---|---|---|---|
| Km (con vuelta) | 52,0 | 60,7 | 72,3 | 93,2 |
| Sale / vuelve | 08:00 / 19:25 | 08:55 / 20:12 | 08:00 / 20:16 | 08:56 / 20:54 |
| Minutos de espera | 353 | 329 | 204 | 153 |
| Primera visita esperada | 64% | 74% | 70% | 69% |

Conclusiones para la reunión con logística:

1. **El ahorro más simple:** salir de bodega según la primera parada. A las
   08:55 en vez de 08:00, la misma ruta ahorra 55 min de jornada.
2. **La mitad de la mejora en primera visita viene de dos bares** que la ruta
   de hoy visita antes de las 19 h (hallazgo #99).
3. **Las esperas son estructurales:** las franjas están separadas y reordenar
   no las llena.
4. **Con el historial actual hay poco margen para flexibilizar,** y en el día
   cargado el prototipo todavía no es estable.

HU-37 sigue apagado hasta validar los costos con logística.

## Objetivo del sprint

**Cumplido en desarrollo; falta el merge.** El despachador arrastra paradas, ve
avisos rojo y ámbar contra la ventana real, guarda el orden y confirma la ruta
con un conductor. Una ruta confirmada ya no se rearma. La línea sigue las
calles (#90) y no se corta al arrastrar (#91).

## Orden de merge

```
main ← #88 ← #89 ← #90 ← #91 ← #92 ← #93 ← #95 ← #96 ← #97 ← #98 ← #100 ← este cierre
```

1. **#88** (plan v4).
2. **#89 y #90** (semilla).
3. **#91** y después **#92** (mapa).
4. **#93** (#70). Va antes de HU-17.
5. **#95** (HU-12).
6. **#96** (HU-15). Su base es `fix/redibujo-polilinea`: después de #91, hay
   que cambiarla a `main` si GitHub no lo hace solo.
7. **#97** (HU-17). Su base es `feature/HU-15-armado-manual` y ya tiene #93
   mergeado adentro. Después de #96, hay que cambiar la base a `main`, y el
   diff queda solo con HU-17.
8. **#98** (HU-06) y **#100** (análisis), que son independientes.

**Probado:** la mezcla de las 11 ramas en este orden no tiene conflictos, y
el resultado pasa:

- delivery-service `./mvnw verify`: 143 unitarias y 151 de integración (1
  saltada: el análisis, que solo corre a mano);
- auth-service `./mvnw verify`;
- frontend: 68 tests, lint (los 2 warnings de siempre) y build.

## Pendiente

- **El merge de los 11 PRs**, que depende de la aprobación del dueño del repo.
- **#57:** elegir el proveedor del mapa y cargar la key de staging (dueño).
- **Reunión con logística** sobre el análisis de ventanas flexibles: costos,
  hora de salida y si la ventana es un borde duro.
- **Límite conocido de HU-15:** "Reoptimizar el resto" ordena las paradas
  libres sin saber a qué hora pasa el camión por las fijadas. Hacerlo exacto
  pide fijar posiciones dentro del solver; queda para HU-16, que toca el
  solver de todos modos.

## Hallazgos del sprint (ver `hallazgos.md`)

| # | Issue | Tipo | Resumen |
|---|---|---|---|
| 16 | #94 | chore | La semilla pone el ruido de la ventana real en las visitas recientes. Con el decaimiento de HU-12 y una vida media de 60 días o menos, 20 de 40 locales cambian de ventana y `SeedDataIT` falla |
| 17 | #99 | bug | La ventana real de los bares queda en 18–20 por un empate de bloques, aunque solo reciben desde las 19. La ruta de hoy entrega a dos bares antes de las 19, con 20% de probabilidad de que reciban |

Las filas 16 y 17 de `hallazgos.md` van en #100. Se cerraron con este sprint
#70 (hallazgo 13) y el comportamiento del refresh para usuarios deshabilitados,
que se corrigió dentro de HU-06.

## Riesgos del Sprint 6

El Sprint 6 trae HU-07, HU-13, HU-18 y HU-19: 23 puntos.

- **Mucho trabajo sin mergear.** 11 PRs, dos de ellos apilados (#96 sobre
  #91, #97 sobre #96). Si el S6 parte antes del merge, cada rama nueva hereda
  esa pila. Conviene mergear antes del 25 de enero.
- **HU-18 (ruta en el celular) depende de HU-17.**
  - El conductor encuentra su ruta por `routes.driver_id`, que es el `sub` de
    su JWT.
  - Hoy `GET /api/routes/state` responde `403` a `DRIVER`, así que HU-18
    necesita un endpoint propio ("mi ruta de hoy").
  - Mientras haya una ruta por fecha (hasta HU-16), un solo conductor por día.
- **HU-19 mueve el estado de la ruta.** Al salir el conductor, la ruta pasa a
  "en curso"; al terminar, a "cerrada". HU-17 dejó las transiciones listas
  (`PUT /api/routes/state`), pero falta decidir quién las dispara: el conductor
  o el despachador. Cada intento invalida la caché del perfil (HU-12).
- **HU-07 (gestión de usuarios) y las rutas confirmadas.** Si se deshabilita
  a un conductor con una ruta confirmada, la ruta queda con su nombre. Desde
  HU-06, ese conductor ya no puede renovar la sesión, así que hay que
  reasignar la ruta: volver a borrador y confirmar con otro.
- **HU-13 (ventana estimada para locales nuevos) se cruza con el análisis.**
  El análisis mostró que más de un tercio de las modulaciones de la semilla son
  falsas. HU-13 debería tratar la modulación como un punto de partida que el
  historial corrige; la curva del prototipo (`AcceptanceCurve`) es una forma de
  hacerlo.
- **HU-15 cambió el tiempo de descarga** (10 min fijos → 8 + 1,5 por bulto +
  ajuste del local). El análisis de ventanas flexibles se midió sobre `main`,
  con 10 min fijos. Si logística pide repetirlo, hay que correr
  `FlexibleWindowsAnalysisIT` otra vez después del merge.
