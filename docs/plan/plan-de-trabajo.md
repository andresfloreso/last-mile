# LastMile — Plan de trabajo, historias de usuario y sprints

> Fuente de verdad del plan. Versión inicial: 8 de noviembre de 2026.
> **v2 (8 de noviembre de 2026): enfoque B2B**, con feedback de logística en terreno. Ver "Cambios v2" al final.
> **v3 (9 de noviembre de 2026): rutas de calidad**, después de probar la ruta del día con la semilla. Ver "Cambios v3" al final.
> Si el plan cambia (alcance, fechas, historias), actualiza este archivo en el mismo PR que introduce el cambio.

## Resumen

LastMile es una plataforma de ruteo para **distribución B2B**: una empresa (por ejemplo, una cervecería o distribuidora de bebidas) reparte con sus camiones a locales comerciales como botillerías, minimarkets, bares y restaurantes. Está inspirada en foxtrot.io.

El problema que resuelve: cada local **declara** un horario de recepción (la "modulación", por ejemplo 9 a 12), pero en la práctica muchos reciben en otro horario. Cuando el camión llega dentro de la modulación y el local está cerrado, la entrega falla y hay que volver. LastMile aprende, local por local, **en qué horario recibe de verdad**, a partir de las confirmaciones y los rechazos que registra el conductor. Con eso arma el orden de la ruta, y durante el reparto le sugiere al conductor qué local visitar a continuación.

El plan tiene 9 sprints de 2 semanas, 35 historias de usuario en 9 épicas y 186 puntos. Los Sprints 1 a 3 se completaron el 9 de noviembre de 2026.

## Glosario del dominio

| Término | Significado |
| --- | --- |
| Local / cliente | Comercio que recibe la mercadería (botillería, minimarket, bar, restaurant). |
| Modulación | Horario de recepción que el local **declara** (por ejemplo 9:00–12:00). Se carga al crear el cliente o desde la planilla. |
| Ventana real | Horario en que el local **efectivamente recibe**, aprendido de los intentos de entrega. Puede diferir de la modulación. |
| Intento de entrega | Cada llegada del camión a un local: entregado (con hora de confirmación) o no entregado con un motivo. |
| Motivo de no entrega | Local cerrado, encargado no estaba, pidió otro horario, dirección errónea, rechazo de producto. |
| Entrega en primera visita | Entrega lograda en el primer intento, sin tener que volver. Es el indicador principal del producto. |
| Parada | Visita a un local en una fecha. Agrupa todos los envíos de ese local para ese día, con la suma de su peso y su volumen (HU-30). |
| Vehículo | Camión de la distribuidora, con patente, capacidad (kg y m³) y jornada (hora de inicio y de término) (HU-29). |
| Despachador | Quien arma, ajusta y asigna las rutas del día. |
| Conductor | Quien ejecuta la ruta y confirma cada entrega desde el celular. |

## Estado del repositorio al inicio del plan

| Servicio | Qué existía | Brechas detectadas |
| --- | --- | --- |
| `api-gateway` | Spring Cloud Gateway, `JwtAuthenticationFilter`, propaga `X-User-Id` y `X-User-Role` | Sin rate limiting ni CORS centralizado; sin pruebas |
| `auth-service` | `/api/auth/register`, `/login`, `/refresh`; bcrypt (factor 12); JWT HS256 | El registro público aceptaba cualquier rol, incluido `ADMIN`; no hay gestión de usuarios; `ddl-auto: update` |
| `delivery-service` | Clientes, envíos, intentos de entrega; `TimeWindowLearningService` (histograma 24 h); `RouteOptimizationService` (franja + nearest-neighbor); Nominatim y OSRM con fallback Haversine; tracking público | Sin migraciones versionadas; Redis no se usa; no existe la modulación declarada; sin decaimiento temporal ni cold start; un solo camión por ruta |
| `import-service` | Express + TypeScript; `.xlsx` (ExcelJS) y `.csv` (PapaParse); envía lotes a `delivery-service` | Sin reporte de errores por fila ni plantilla descargable |
| `frontend` | React + Vite + Tailwind + Zustand; páginas `/login`, `/` (RouteBuilder estilo Spoke con Leaflet) e `/import` | No lista los envíos existentes; no hay vista de conductor ni listado de envíos; diseño poco legible |
| Transversal | `docker-compose` con PostgreSQL (`authdb`, `deliverydb`) y Redis | 0 pruebas; sin CI; sin datos semilla; sin documentación de API |

## Plan de trabajo

1. **Fundaciones (Sprint 1–2).** Migraciones Flyway, CI, pruebas, datos semilla, roles seguros, importación robusta y modulación declarada.
   - Entregable: `docker compose up` levanta todo con locales de ejemplo, cada uno con su modulación, y cada PR se valida solo.
2. **Núcleo del despachador (Sprint 3–5).** Pantalla de rutas rediseñada, listado de envíos, ruta con hora estimada y modulación vs. ventana real (S3); **rutas de calidad** con semilla real, paradas consolidadas, vehículos con capacidad y optimizador VRPTW (S4); aprendizaje mejorado y asignación a conductor (S5).
   - Entregable: un despachador arma, ajusta y asigna la ruta del día viendo, para cada local, su modulación y en qué horario recibe de verdad, con una ruta que respeta la capacidad del camión y las ventanas reales.
3. **Operación en terreno (Sprint 6–7).** App del conductor con confirmación de entrega y motivos, y ruteo multi-camión.
   - Entregable: el conductor confirma entregas y rechazos, esos intentos alimentan el aprendizaje, y la distribuidora reparte con varios camiones.
4. **Seguimiento, operación en vivo y producción (Sprint 7–9).** Seguimiento para el local, reordenamiento en ruta, monitoreo en vivo y KPIs para el despachador, observabilidad y staging.
   - Entregable: el despachador ve los camiones en vivo y el indicador de entrega en primera visita, y el local sigue su pedido.

### Organización del repositorio

```
last-mile/
├── services/
│   ├── api-gateway/
│   ├── auth-service/
│   ├── delivery-service/
│   └── import-service/
├── frontend/
├── infra/
│   ├── postgres/
│   └── seed/
├── docs/
│   ├── adr/          # decisiones de arquitectura
│   ├── api/          # OpenAPI por servicio
│   └── plan/         # este plan, cierres de sprint, hallazgos
├── .github/
│   ├── workflows/    # CI por servicio
│   └── ISSUE_TEMPLATE/
├── docker-compose.yml
└── README.md
```

### Definición de terminado

- Cumple sus criterios de aceptación y fue revisada en un PR aprobado.
- Tiene pruebas unitarias para la lógica nueva y el CI está en verde.
- Los cambios de esquema van en una migración Flyway.
- Los endpoints nuevos están en el OpenAPI del servicio.
- Funciona con `docker compose up` usando los datos semilla.
- Si toca el frontend, el PR incluye capturas de las pantallas cambiadas.

## Épicas

Cada épica corresponde a un label de GitHub (`epica:E1` … `epica:E9`).

| Épica | Label | Objetivo | Historias | Puntos |
| --- | --- | --- | --- | --- |
| E1 Fundaciones técnicas | `epica:E1` | Repo confiable y reproducible | HU-01 a HU-04, HU-31 (+ reorganización del repo) | 24 |
| E2 Autenticación y usuarios | `epica:E2` | Roles seguros y sesiones estables | HU-05 a HU-07 | 11 |
| E3 Locales, envíos e importación | `epica:E3` | Cargar locales con su modulación y consultar envíos sin fricción | HU-08, HU-09, HU-10, HU-25 | 20 |
| E4 Ventanas horarias | `epica:E4` | Aprender en qué horario recibe de verdad cada local | HU-11 a HU-13 | 18 |
| E5 Rutas | `epica:E5` | Ruta del día óptima, editable, asignada y fácil de leer | HU-14 a HU-17, HU-27 a HU-30, HU-32 | 48 |
| E6 Conductor | `epica:E6` | Ejecutar la ruta, confirmar entregas y reordenar en terreno | HU-18 a HU-20, HU-26 | 26 |
| E7 Seguimiento para el local | `epica:E7` | Que el local sepa cuándo llega su pedido | HU-21 a HU-22 | 11 |
| E8 Operación | `epica:E8` | Observabilidad y despliegue | HU-23 a HU-24 | 10 |
| E9 Operación en vivo y KPIs | `epica:E9` | Que el despachador vea la operación del día en vivo y mida la entrega en primera visita | HU-33 a HU-35 | 18 |

## Backlog de historias de usuario

Puntos en escala Fibonacci (1, 2, 3, 5, 8, 13).

| ID | Épica | Historia de usuario | Criterios de aceptación | Pts | Sprint |
| --- | --- | --- | --- | --- | --- |
| HU-01 | E1 | Como desarrollador, quiero levantar todo con un comando y datos semilla, para probar sin preparar nada a mano. | `docker compose up` deja los 7 contenedores *healthy*; un seed crea un usuario por rol, 30 clientes con historial de intentos y 50 envíos del día. | 3 | S1 ✅ |
| HU-02 | E1 | Como desarrollador, quiero migraciones versionadas, para que el esquema no cambie sin control. | Flyway con `V1__baseline` en `authdb` y `deliverydb`; `ddl-auto: validate`; la base se recrea desde cero sin errores. | 5 | S1 ✅ |
| HU-03 | E1 | Como equipo, quiero CI en cada PR, para no integrar código roto. | GitHub Actions compila los 3 servicios Maven y corre `npm ci`, lint y build en los 2 servicios Node; lockfiles versionados; un fallo bloquea el merge a `main`. | 5 | S1 ✅ |
| HU-04 | E1 | Como desarrollador, quiero pruebas base por servicio, para refactorizar con seguridad. | Pruebas unitarias de `JwtUtil`, `TimeWindowLearningService`, `RouteOptimizationService` y los parsers Excel/CSV; repositorios probados con Testcontainers; cobertura de 60% o más en `algorithm/`. | 8 | S2 ✅ |
| HU-05 | E2 | Como administrador, quiero que solo un ADMIN asigne roles, para que nadie se registre como ADMIN. | `/register` público ignora el rol enviado y crea `CLIENT`; `POST /api/users` (solo ADMIN) crea usuarios con rol; prueba que confirma 403 en otro caso. | 3 | S1 ✅ |
| HU-06 | E2 | Como usuario, quiero que mi sesión se renueve sola, para no perder trabajo cuando expira el token. | El interceptor de axios llama a `/refresh` ante un 401 y reintenta la request; si el refresh falla, cierra sesión y redirige a `/login`. | 3 | S5 |
| HU-07 | E2 | Como administrador, quiero gestionar usuarios, para dar de alta conductores y bajar cuentas. | Pantalla para listar, crear, editar rol y desactivar; un usuario desactivado no puede iniciar sesión. | 5 | S5 |
| HU-08 | E3 | Como despachador, quiero ver los errores de importación por fila, para corregir la planilla rápido. | Resumen con creados, omitidos y errores, cada error con fila y motivo; plantilla `.xlsx` descargable; importar 2 veces el mismo `codigo_externo` no duplica. | 5 | S2 ✅ |
| HU-09 | E3 | Como despachador, quiero geocodificar al importar las direcciones sin coordenadas, para no ingresarlas a mano. | Usa Nominatim respetando 1 request por segundo y caché por dirección; los resultados de baja confianza quedan marcados "revisar" en el mapa. | 5 | S2 ✅ |
| HU-10 | E3 | Como despachador, quiero listar y filtrar envíos, para saber qué sale cada día. | `GET /api/shipments` paginado con filtros por fecha, estado y comuna; tabla en el frontend con búsqueda por local, dirección o código. | 5 | S3 ✅ |
| HU-11 | E4 | Como despachador, quiero ver la modulación de cada local junto a su ventana real aprendida, para entender por qué la ruta lo ordena así. | `GET /api/customers/{id}/time-window` devuelve modulación, ventana real recomendada, histograma por hora y el conteo de intentos; la ventana real se expresa como "recibió X de Y visitas en esa franja" (aceptadas / intentos en la franja), nunca como un puntaje sin explicar; `TimeWindowLearningService` se extiende para entregar el histograma y el conteo, así la ruta y la pantalla usan una sola fuente de verdad; el detalle del local muestra "Declara 9–12 · recibe 12–15 (8 de 10 visitas)" y el histograma de 07 a 22 h, marcando la modulación y los rechazos. | 5 | S3 ✅ |
| HU-12 | E4 | Como sistema, quiero priorizar intentos recientes y cachear el perfil, para aprender cambios de hábito sin recalcular siempre. | Decaimiento temporal configurable; perfil en Redis con TTL; la caché se invalida al registrar un intento. | 8 | S5 |
| HU-13 | E4 | Como despachador, quiero una ventana estimada para locales con poco historial, para ordenarlos desde el primer día. | Con menos de N intentos se usa la modulación declarada; si el local tampoco tiene modulación, se usa el perfil agregado de su comuna y tipo de local; la ventana muestra de dónde salió ("según modulación" o "estimada por comuna") y se reemplaza gradualmente por la aprendida a medida que llegan intentos. | 5 | S5 |
| HU-14 | E5 | Como despachador, quiero ver la ruta del día en el mapa con orden y hora estimada por parada, para validarla antes de salir. | Hora estimada por parada calculada con tiempos de OSRM y duración de parada; respeta `orderPreference` Primera y Última; polilínea en Leaflet; marca las paradas cuya hora estimada cae fuera de su ventana real. | 5 | S3 ✅ |
| HU-15 | E5 | Como despachador, quiero mover paradas y fijar posiciones, para aplicar lo que sé del terreno. | Arrastrar para reordenar; una parada fijada no se mueve al reoptimizar; las horas estimadas se recalculan. | 5 | S6 |
| HU-16 | E5 | Como despachador, quiero repartir los envíos entre varios camiones, para cubrir el volumen del día. | Usa el motor de HU-28, sin agregar otro solver; asigna cada parada (HU-30) a un vehículo (HU-29) respetando su capacidad en kg y m³ y su jornada; una ruta por vehículo, cada una con su hora estimada por parada y su propio color en la pantalla de rutas; las paradas que no caben en la flota quedan sin asignar y se informan con el motivo; en la semilla, mejora los km totales y las paradas fuera de ventana frente a repartir las paradas entre los camiones por turno y ordenar cada ruta con el nearest-neighbor. | 8 | S7 |
| HU-17 | E5 | Como despachador, quiero confirmar la ruta y asignarla a un conductor, para que la vea en su teléfono. | Estados de ruta: borrador, confirmada, en curso y cerrada; una ruta confirmada queda bloqueada para edición. | 3 | S5 |
| HU-18 | E6 | Como conductor, quiero ver mi ruta del día en el celular, para seguir el orden sin llamar al despachador. | Vista móvil con el siguiente local destacado, su ventana real y la hora estimada; botón para abrir la navegación en Google Maps o Waze. | 5 | S6 |
| HU-19 | E6 | Como conductor, quiero confirmar la entrega o registrar por qué no se pudo, para que el sistema aprenda el horario real de cada local. | "Entregado" registra la hora de confirmación (equivalente a la firma de la factura); "No entregado" exige un motivo: local cerrado, encargado no estaba, pidió otro horario, dirección errónea o rechazo de producto; "pidió otro horario" guarda la franja indicada por el local; cada registro crea un `DeliveryAttempt` que alimenta el aprendizaje; funciona con mala señal y sincroniza al recuperar conexión. | 8 | S6 |
| HU-20 | E6 | Como conductor, quiero registrar una prueba de entrega, para respaldar el envío ante reclamos. | Foto de la factura o guía firmada y nombre de quien recibe; se guarda en almacenamiento de objetos y aparece en el detalle del envío. | 5 | S6 |
| HU-21 | E7 | Como encargado de un local, quiero consultar mi pedido con su código, para saber cuándo llega. | Página pública `/tracking/:codigo` sin login; muestra estado, hora estimada e historial de intentos. | 3 | S7 |
| HU-22 | E7 | Como encargado de un local, quiero ver la hora estimada y el camión en vivo, para tener a alguien que reciba. | La posición del conductor se transmite por WebSocket o SSE; la página de seguimiento muestra el mapa y la hora estimada actualizada. | 8 | S7 |
| HU-23 | E8 | Como equipo, quiero observabilidad, para diagnosticar fallas entre servicios. | Health checks en compose; logs JSON con un `traceId` que viaja desde el gateway; métricas de Actuator expuestas. | 5 | S7 |
| HU-24 | E8 | Como equipo, quiero un ambiente de staging, para mostrar avances sin levantar nada local. | Despliegue automático desde `main`; secretos fuera del repo; perfil `seed` desactivado; URL pública documentada en el README. | 5 | S9 |
| HU-25 | E3 | Como despachador, quiero registrar la modulación declarada de cada local, para que la ruta parta de lo que el local dice y el sistema mida cuánto se cumple. | Migración que agrega a `Customer` el tipo de local (botillería, minimarket, bar, restaurant, otro) y la modulación (`declared_window_start`, `declared_window_end`, opcionalmente dos tramos); columnas `tipo_local`, `modulacion_desde` y `modulacion_hasta` en la planilla de importación y en la plantilla descargable; edición de la modulación vía API (`PUT /api/customers/{id}/declared-window`; la pantalla de detalle del local llega con HU-27); la semilla se convierte a 30 locales comerciales de Santiago, con al menos un tercio cuya modulación no coincide con su ventana real. | 5 | S2 ✅ |
| HU-26 | E6 | Como conductor, quiero que la app me sugiera el siguiente local cuando algo cambia en la ruta, para no perder viajes. | Al registrar un "No entregado", o si el camión va más de 20 minutos atrasado, se recalcula el orden de las paradas pendientes con la ventana real y la posición actual; la app muestra la sugerencia ("Ve primero a Botillería El Roble: recibe hasta las 13:00") y el conductor la acepta o la ignora; el despachador ve el cambio en su pantalla; la decisión queda registrada. | 8 | S8 |
| HU-27 | E5 | Como despachador, quiero una pantalla de rutas clara y centrada en el mapa, para leer la ruta del día de un vistazo. | Implementa el diseño combinado aprobado (panel lateral + mapa a pantalla completa + línea de tiempo); modo claro y oscuro; la ruta del día carga sola los envíos de la fecha; colores por franja con leyenda fija; resumen con paradas, km, hora de término y % de entregas en primera visita; detalle del local con modulación vs. ventana real y edición de la modulación (API de HU-25); mapa base neutro (Esri Gray Canvas por defecto, configurable con `VITE_TILE_URL_*`, porque CARTO exige API key desde 2026; el proveedor definitivo se elige antes de HU-24, [#57](https://github.com/andresfloreso/last-mile/issues/57)); usable desde 1280 px y en tablet. | 8 | S3 ✅ |
| HU-28 | E5 | Como despachador, quiero que la ruta del día respete la capacidad del camión y las ventanas reales, para entregar más en primera visita con menos km. | Interfaz nueva (`RouteOptimizer`) con dos implementaciones: VRPTW de un camión con Timefold Solver (preferido) o jsprit, y el nearest-neighbor actual como fallback y línea base; restricciones duras: capacidad del vehículo (kg y m³) y su jornada; restricciones blandas: minutos fuera de la ventana real (peso alto), km totales y `orderPreference` Primera/Última; tiempo de resolución acotado y configurable (10 s por defecto); si el solver falla o no encuentra una solución factible, se usa el fallback y la respuesta lo indica; el PR trae una tabla antes/después sobre la semilla de HU-31, en el día de hoy y en el día cargado (km, hora de término, paradas fuera de ventana y minutos fuera de ventana), y capturas de la ruta antes y después con el mismo zoom; la versión nueva mejora en km en los dos días y en paradas fuera de ventana en el día cargado (hoy la línea base ya tiene 0, así que ahí no puede empeorar); un test de regresión falla si la solución es peor que la línea base en paradas fuera de ventana; autoriza a cambiar `RouteOptimizationService` o a reemplazarlo detrás de la interfaz (los tests de HU-04 se adaptan, no se borran); cobertura de 60% o más en `algorithm/`, incluido el paquete del optimizador. | 8 | S4 |
| HU-29 | E5 | Como despachador, quiero registrar mis camiones con su capacidad, para que la ruta no cargue más de lo que cabe. | Entidad `Vehicle` con patente, capacidad en kg y en m³ y jornada (inicio y término), con migración Flyway y API documentada en springdoc; peso (kg) y volumen (m³) por envío, con columnas en la importación y en la plantilla descargable; los envíos que ya existen sin peso ni volumen reciben un valor por defecto documentado; la semilla trae al menos un camión y el peso y el volumen de cada envío; la parada consolidada (HU-30) suma el peso y el volumen de sus envíos. | 5 | S4 |
| HU-30 | E5 | Como despachador, quiero una sola parada por local aunque tenga varios envíos, para no visitar dos veces el mismo local el mismo día. | Los envíos del mismo local en la misma fecha forman una sola parada, con la suma de sus bultos (el peso y el volumen se suman en HU-29, que los crea); la ruta ordena paradas, no envíos; la lista y el mapa muestran "N envíos" en esa parada; la semilla tiene locales con 2 o más envíos el mismo día. | 3 | S4 |
| HU-31 | E1 | Como equipo, quiero una semilla con direcciones reales en una zona compacta, para evaluar la calidad de la ruta con datos creíbles. | Locales en una zona compacta de comunas contiguas; direcciones de calles comerciales verificables en Google Maps, que caen sobre una calle y no dentro de parques ni estadios; coordenadas fijas en el SQL, sin geocodificar en runtime; el depósito está dentro de la zona; nombres de locales inventados, sin datos personales reales; se mantienen los patrones deterministas de ventana real y la modulación distinta de la ventana real en al menos un tercio de los locales, y `SeedDataIT` los sigue validando; además de los envíos de hoy (una ruta que un camión alcanza a hacer), un **día cargado** de fecha fija con un envío por local, 10 por franja, que no cabe completo dentro de ventana con un camión: es el día con el que HU-28 compara optimizadores. | 3 | S4 |
| HU-32 | E5 | Como despachador, quiero que la pantalla de rutas abra rápido y no cambie la ruta al mirarla, para revisarla sin perder lo que ya está armado. | `GET /api/routes?date=` devuelve la ruta guardada sin recalcular ni escribir; `POST /api/routes/build` solo se llama cuando el despachador lo pide; quedan definidos los roles que ven la pantalla de rutas, y `OPERATOR` no recibe 403; Leaflet con `preferCanvas` y polilínea simplificada según el zoom; mover el mapa no repite consultas a OSRM; el PR mide el tiempo de render o las requests a OSRM al mover el mapa, antes y después; resuelve [#58](https://github.com/andresfloreso/last-mile/issues/58). | 3 | S4 |
| HU-33 | E9 | Como despachador, quiero ver cada camión en vivo y su avance frente al plan, para reaccionar a tiempo. | Mapa con la posición de cada camión, que reutiliza el stream de posición de HU-22; por ruta, paradas hechas frente a las planificadas y atraso o adelanto frente a la hora estimada; se actualiza sin recargar la página; depende de HU-22. | 8 | S8 |
| HU-34 | E9 | Como despachador, quiero alertas cuando una ruta se desvía del plan, para intervenir antes de perder una entrega. | Alerta por atraso mayor que el umbral configurado, por parada que va a caer fuera de su ventana real y por parada saltada; las alertas se ven en la pantalla del despachador; depende de HU-33. | 5 | S9 |
| HU-35 | E9 | Como despachador, quiero un dashboard de KPIs, para medir si la ruta mejora la entrega en primera visita. | Entrega en primera visita a 30 días, con su definición agregada al glosario (resuelve [#59](https://github.com/andresfloreso/last-mile/issues/59)); % de paradas dentro de su ventana real, km por parada y puntualidad (llegada real frente a la hora estimada); endpoint documentado en springdoc y con test de integración; la pantalla de rutas muestra el KPI de primera visita en vez de "—". | 5 | S8 |

## Sprints

Sprints de 2 semanas. Cada sprint es un milestone de GitHub y cierra con una demo. El Sprint 1 terminó antes de lo planificado; las fechas siguientes se mantienen hasta medir la velocidad real del Sprint 2.

| Sprint | Fechas | Objetivo | Historias | Pts | Demo al cierre |
| --- | --- | --- | --- | --- | --- |
| S1 ✅ | 16 nov – 27 nov 2026 (terminado el 8 nov) | Base técnica confiable y roles seguros | HU-01, HU-02, HU-03, HU-05 | 16 | Todo levanta con datos semilla; CI en cada PR |
| S2 ✅ | 30 nov – 11 dic 2026 (terminado el 9 nov) | Pruebas, importación robusta y modulación | HU-04, HU-08, HU-09, HU-25 + bugs #9 y #12 | 23 | Importar una planilla de locales con su modulación y ver los errores por fila |
| S3 ✅ | 14 dic – 25 dic 2026 (terminado el 9 nov) | Pantalla de rutas nueva y ventana real visible | HU-10, HU-11, HU-14, HU-27 | 23 | Ruta del día rediseñada, con hora estimada y "declara vs. recibe" por local |
| S4 | 28 dic 2026 – 8 ene 2027 | Rutas de calidad | HU-31, HU-30, HU-29, HU-28, HU-32 | 22 | Ruta del día sobre direcciones reales, con una parada por local, dentro de la capacidad del camión, y con menos km y menos paradas fuera de ventana que el nearest-neighbor (tabla antes/después) |
| S5 | 11 ene – 22 ene 2027 | Aprendizaje mejorado y ruta asignable | HU-06, HU-07, HU-12, HU-13, HU-17 | 24 | Locales nuevos ordenados por su modulación; ruta confirmada y asignada a un conductor |
| S6 | 25 ene – 5 feb 2027 | App del conductor | HU-15, HU-18, HU-19, HU-20 | 23 | El conductor confirma entregas y rechazos con motivo, y el aprendizaje los usa |
| S7 | 8 feb – 19 feb 2027 | Multi-camión y seguimiento para el local | HU-16, HU-21, HU-22, HU-23 | 24 | Rutas repartidas entre 2 o más camiones; el local sigue su pedido y el camión en vivo |
| S8 | 22 feb – 5 mar 2027 | Operación en vivo y KPIs | HU-26, HU-33, HU-35 | 21 | Un rechazo en ruta reordena las paradas pendientes; el despachador ve los camiones en vivo y la entrega en primera visita |
| S9 | 8 mar – 19 mar 2027 | Alertas y staging | HU-34, HU-24 | 10 | Alertas de atraso y de parada fuera de ventana; staging desplegado desde `main` |

- **El S4 cruza Año Nuevo** y tiene un 20% menos de capacidad (unos 19 puntos). Sus 22 puntos superan ese margen. Si el sprint se atrasa, HU-32 es la que puede pasar al S5, porque la tabla antes/después de HU-28 no la necesita.
- **Dependencias:** HU-28 se mide sobre la semilla de HU-31 y usa las paradas de HU-30 y la capacidad de HU-29. HU-16 usa el motor de HU-28. HU-26 usa los intentos de HU-19. HU-33 depende de HU-22, y HU-34 de HU-33.
- **El S7 y el S8 no alcanzaban para las 9 historias** (55 puntos frente a unos 48). El dueño del producto decidió abrir un **S9** con HU-34 y HU-24 (10 puntos). Queda con capacidad libre para lo que se atrase de los sprints anteriores.

## Riesgos

| Riesgo | Impacto | Mitigación |
| --- | --- | --- |
| Sin intentos reales, el aprendizaje no se puede validar | El diferenciador del producto queda sin demostrar | Semilla con locales cuya modulación difiere de su ventana real, y pruebas que verifican que el motor recupera la ventana real (HU-04, HU-25) |
| Los conductores no registran el motivo o la hora | El algoritmo aprende de datos incompletos | Confirmación en un toque, motivo obligatorio y funcionamiento sin señal (HU-19) |
| Nominatim público limita a 1 request por segundo | Importaciones grandes lentas o bloqueadas | 1 req/s y caché por dirección (HU-09); geocodificación en segundo plano ([#32](https://github.com/andresfloreso/last-mile/issues/32)) u opción de instancia propia (`NOMINATIM_URL`) |
| El optimizador (HU-28) y el reordenamiento en ruta (HU-26) son las historias más inciertas | El S4 o el S8 se atrasan | HU-28 queda detrás de una interfaz, con el nearest-neighbor como fallback; HU-16 reutiliza el motor de HU-28 en vez de traer otro solver |
| El S4 cruza Año Nuevo | Menos capacidad: 22 puntos frente a unos 19 | HU-32 puede pasar al S5 sin afectar la tabla antes/después de HU-28 |
| El perfil `seed` viene activo por defecto en `docker-compose.yml` | Usuarios con contraseñas conocidas si se usa ese compose fuera de desarrollo | Apagado por defecto; lo activa `docker-compose.override.yml` solo en desarrollo ([#25](https://github.com/andresfloreso/last-mile/issues/25)); HU-24 lo exige apagado en staging |
| Un mismo `JWT_SECRET` en 4 servicios | Si se filtra, se compromete todo | Compose sin secretos por defecto y gitleaks en CI ([#17](https://github.com/andresfloreso/last-mile/issues/17)); quitar los defaults del código ([#16](https://github.com/andresfloreso/last-mile/issues/16)); secretos de staging en HU-24; evaluar RS256 con clave pública más adelante |
| Tiempo de resolución del solver (HU-28) | `POST /api/routes/build` queda bloqueado varios segundos, o el solver no encuentra una solución factible en el tiempo dado | Límite de tiempo configurable (10 s por defecto) y corte anticipado si la solución deja de mejorar; nearest-neighbor como fallback, indicado en la respuesta; un test con la semilla mide el tiempo |
| Licencia y tamaño de la dependencia del solver | Una licencia incompatible, o una imagen de `delivery-service` mucho más pesada | Timefold Solver Community es Apache 2.0, y no se usa nada de la edición Enterprise; si se elige jsprit, se verifica su licencia antes de agregarlo; el PR de HU-28 informa el tamaño del jar antes y después |
| OSRM público tiene límite de uso, y el solver necesita la matriz de tiempos entre todas las paradas | Con 50 paradas y varios camiones, la matriz (`/table`) puede fallar o tardar | Una sola llamada a `/table` por armado; fallback a línea recta × 1,3, como en HU-14; evaluar un contenedor OSRM propio con el extracto de Chile antes de HU-16 |
| Envíos existentes sin peso ni volumen (HU-29) | La restricción de capacidad no tiene datos para los envíos cargados antes de la migración | La migración aplica un valor por defecto documentado en `docs/api/` (configurable), y la importación lo usa cuando la planilla no trae peso o volumen |

## Convenciones de trabajo

- **Ramas:** `main` protegida; ramas `feature/HU-xx-descripcion-corta`, `fix/...`, `chore/...` y `docs/...`; merge solo por PR con CI en verde.
- **Commits:** Conventional Commits con el ID de la historia, por ejemplo `feat(delivery): HU-25 modulación declarada por local`.
- **PR:** enlaza su issue con `Closes #n`, describe cómo probarlo e incluye capturas si toca el frontend.
- **Issues:** uno por historia, con labels `sprint-N`, `epica:EN` y `tipo:*`, milestone del sprint y criterios de aceptación como checklist. Se crean al iniciar cada sprint.
- **Tablero:** GitHub Projects con columnas Backlog, En curso, En revisión y Terminado.
- **Lenguaje:** en la interfaz y la documentación se usa el vocabulario del glosario (local, modulación, ventana real, primera visita).
- **Hallazgos fuera de alcance:** issue con `tipo:bug` o `tipo:chore` y nota en `docs/plan/hallazgos.md`.
- **Cierre de sprint:** `docs/plan/sprint-N-cierre.md` con lo completado, lo pendiente y los hallazgos.

## Cambios v2

Feedback de una persona que trabajó en logística de distribución usando foxtrot.io:

- El producto pasa a ser **B2B**: se reparte a locales comerciales, no a personas.
- Se incorpora la **modulación** (horario declarado) como dato propio, distinto de la ventana real aprendida (HU-25, HU-11, HU-13).
- La fuente principal de aprendizaje es la **confirmación del conductor**: hora de entrega y motivo de no entrega, incluido "pidió otro horario" (HU-19).
- La ruta se **reordena durante el reparto**, no solo en la mañana (HU-26).
- Se agrega el **rediseño de la pantalla de rutas** como historia propia (HU-27).
- Se agrega un Sprint 7 y se reordenan historias: HU-06 y HU-07 pasan a S4, HU-15 y HU-20 a S5, HU-16 a S6, y HU-21 a S7.
- El indicador principal es **entrega en primera visita**.

## Cambios v3

Al probar la pantalla de rutas con la semilla del Sprint 3, la ruta del día salió mala: 203 km, término a las 21:59, 21 de 50 paradas fuera de su ventana real, paradas repetidas para un mismo local, direcciones inventadas que caen en lugares absurdos (Av. Grecia 2040 cae dentro del Estadio Nacional), polilíneas lentas al mover el mapa y una pantalla que arma la ruta al abrirse ([#58](https://github.com/andresfloreso/last-mile/issues/58)). El orden viene de un nearest-neighbor que ignora la capacidad y casi no considera las ventanas.

- El S4 pasa a ser **"Rutas de calidad"**: semilla con direcciones reales (HU-31), paradas consolidadas por local (HU-30), vehículos con capacidad (HU-29), optimizador VRPTW de un camión (HU-28), y ruta sin efectos con un mapa más rápido (HU-32).
- **HU-28 autoriza a cambiar `RouteOptimizationService`**, o a reemplazarlo detrás de una interfaz. El nearest-neighbor queda como fallback y como línea base para comparar.
- **HU-16 se redefine** (de 13 a 8 puntos): multi-camión sobre el motor de HU-28, sin un solver propio.
- Épica nueva **E9 "Operación en vivo y KPIs"**: monitoreo en vivo (HU-33), alertas (HU-34) y dashboard de KPIs (HU-35, que resuelve [#59](https://github.com/andresfloreso/last-mile/issues/59)).
- Se agregan los términos **parada** y **vehículo** al glosario.
- Re-sprint: lo que era el S4 pasa al S5 y la app del conductor al S6. Multi-camión, seguimiento para el local, operación en vivo y producción se reparten entre el S7, un **S8 nuevo** (22 feb – 5 mar 2027) y un **S9 nuevo** (8 mar – 19 mar 2027).
- HU-34 y HU-24 (10 puntos) no cabían en el S8 sin pasar de unos 24 puntos. Se abre un **S9** (8 mar – 19 mar 2027) con esas dos historias.
- Riesgos nuevos: tiempo del solver, licencia y tamaño de la dependencia, límite de OSRM público para la matriz de tiempos, y envíos sin peso ni volumen.
