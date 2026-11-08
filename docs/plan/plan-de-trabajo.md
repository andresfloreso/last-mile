# LastMile — Plan de trabajo, historias de usuario y sprints

> Fuente de verdad del plan. Versión inicial: 8 de noviembre de 2026.
> Si el plan cambia (alcance, fechas, historias), actualiza este archivo en el mismo PR que introduce el cambio.

## Resumen

LastMile se reorganiza en 6 sprints de 2 semanas, con 24 historias de usuario agrupadas en 8 épicas (127 puntos en total). Primero se consolida la base técnica (migraciones, CI, pruebas, seguridad de roles). Luego se completa el circuito que hoy falta: el conductor registra intentos de entrega, ese historial alimenta el aprendizaje de ventanas horarias y la ruta del día se arma con él.

LastMile es una plataforma de última milla inspirada en recibelo.cl y foxtrot.io. Su diferenciador es aprender, por cliente, la franja horaria en que recibe o rechaza envíos, y usarla para ordenar las paradas del camión.

## Estado del repositorio al inicio del plan

| Servicio | Qué existía | Brechas detectadas |
| --- | --- | --- |
| `api-gateway` | Spring Cloud Gateway, `JwtAuthenticationFilter`, propaga `X-User-Id` y `X-User-Role` | Sin rate limiting ni CORS centralizado; sin pruebas |
| `auth-service` | `/api/auth/register`, `/login`, `/refresh`; bcrypt (factor 12); JWT HS256 | El registro público acepta cualquier rol, incluido `ADMIN`; no hay gestión de usuarios; `ddl-auto: update` |
| `delivery-service` | Clientes, envíos, intentos de entrega; `TimeWindowLearningService` (histograma 24 h); `RouteOptimizationService` (franja + nearest-neighbor); Nominatim y OSRM con fallback Haversine; tracking público | Sin migraciones versionadas; Redis no se usa; sin decaimiento temporal ni cold start en el aprendizaje; un solo camión por ruta |
| `import-service` | Express + TypeScript; `.xlsx` (ExcelJS) y `.csv` (PapaParse); envía lotes a `delivery-service` | Sin reporte de errores por fila ni plantilla descargable; sin lockfile |
| `frontend` | React + Vite + Tailwind + Zustand; páginas `/login`, `/` (RouteBuilder estilo Spoke con Leaflet) e `/import` | No hay vista de conductor, listado de envíos, tracking público ni renovación de token; sin lockfile |
| Transversal | `docker-compose` con PostgreSQL (`authdb`, `deliverydb`) y Redis; README con flujo `curl` | 0 archivos de prueba; sin `.github/workflows`; sin datos semilla; sin documentación de API |

La brecha más importante es funcional: `POST /api/shipments/attempts` existe, pero ninguna pantalla lo usa. Sin intentos registrados, el motor de ventanas horarias no tiene datos para aprender.

## Plan de trabajo

1. **Fundaciones (Sprint 1–2).** Migraciones Flyway, CI, pruebas, datos semilla, roles seguros e importación robusta.
   - Entregable: `docker compose up` levanta todo con datos de ejemplo y cada PR se valida solo.
2. **Núcleo del despachador (Sprint 3–4).** Listado de envíos, ruta con ETA, ajustes manuales, asignación a conductor y aprendizaje mejorado.
   - Entregable: un despachador arma, ajusta y asigna la ruta del día viendo la ventana aprendida de cada cliente.
3. **Operación en terreno (Sprint 5).** App del conductor y ruteo multi-camión.
   - Entregable: el conductor registra entregas y rechazos, y esos intentos alimentan el aprendizaje.
4. **Cliente final y producción (Sprint 6).** Tracking en vivo, prueba de entrega, observabilidad y staging.
   - Entregable: ambiente de staging desplegado desde `main`, con tracking público en vivo.

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

## Épicas

Cada épica corresponde a un label de GitHub (`epica:E1` … `epica:E8`).

| Épica | Label | Objetivo | Historias | Puntos |
| --- | --- | --- | --- | --- |
| E1 Fundaciones técnicas | `epica:E1` | Repo confiable y reproducible | HU-01 a HU-04 (+ reorganización del repo) | 21 |
| E2 Autenticación y usuarios | `epica:E2` | Roles seguros y sesiones estables | HU-05 a HU-07 | 11 |
| E3 Envíos e importación | `epica:E3` | Cargar y consultar envíos sin fricción | HU-08 a HU-10 | 15 |
| E4 Ventanas horarias | `epica:E4` | Aprendizaje preciso y visible | HU-11 a HU-13 | 18 |
| E5 Rutas | `epica:E5` | Ruta del día óptima, editable y asignada | HU-14 a HU-17 | 26 |
| E6 Conductor | `epica:E6` | Ejecutar la ruta y registrar intentos | HU-18 a HU-20 | 15 |
| E7 Cliente final | `epica:E7` | Seguimiento público y en vivo | HU-21 a HU-22 | 11 |
| E8 Operación | `epica:E8` | Observabilidad y despliegue | HU-23 a HU-24 | 10 |

## Backlog de historias de usuario

Puntos en escala Fibonacci (1, 2, 3, 5, 8, 13).

| ID | Épica | Historia de usuario | Criterios de aceptación | Pts | Sprint |
| --- | --- | --- | --- | --- | --- |
| HU-01 | E1 | Como desarrollador, quiero levantar todo con un comando y datos semilla, para probar sin preparar nada a mano. | `docker compose up` deja los 7 contenedores *healthy*; un seed crea un usuario por rol, 30 clientes con historial de intentos y 50 envíos del día. | 3 | S1 |
| HU-02 | E1 | Como desarrollador, quiero migraciones versionadas, para que el esquema no cambie sin control. | Flyway con `V1__baseline` en `authdb` y `deliverydb`; `ddl-auto: validate`; la base se recrea desde cero sin errores. | 5 | S1 |
| HU-03 | E1 | Como equipo, quiero CI en cada PR, para no integrar código roto. | GitHub Actions compila los 3 servicios Maven y corre `npm ci`, lint y build en los 2 servicios Node; lockfiles versionados; un fallo bloquea el merge a `main`. | 5 | S1 |
| HU-04 | E1 | Como desarrollador, quiero pruebas base por servicio, para refactorizar con seguridad. | Pruebas unitarias de `JwtUtil`, `TimeWindowLearningService`, `RouteOptimizationService` y los parsers Excel/CSV; repositorios probados con Testcontainers; cobertura de 60% o más en `algorithm/`. | 8 | S2 |
| HU-05 | E2 | Como administrador, quiero que solo un ADMIN asigne roles, para que nadie se registre como ADMIN. | `/register` público ignora el rol enviado y crea `CLIENT`; `POST /api/users` (solo ADMIN) crea usuarios con rol; prueba que confirma 403 en otro caso. | 3 | S1 |
| HU-06 | E2 | Como usuario, quiero que mi sesión se renueve sola, para no perder trabajo cuando expira el token. | El interceptor de axios llama a `/refresh` ante un 401 y reintenta la request; si el refresh falla, cierra sesión y redirige a `/login`. | 3 | S2 |
| HU-07 | E2 | Como administrador, quiero gestionar usuarios, para dar de alta conductores y bajar cuentas. | Pantalla para listar, crear, editar rol y desactivar; un usuario desactivado no puede iniciar sesión. | 5 | S3 |
| HU-08 | E3 | Como despachador, quiero ver los errores de importación por fila, para corregir la planilla rápido. | Resumen con creados, omitidos y errores, cada error con fila y motivo; plantilla `.xlsx` descargable; importar 2 veces el mismo `codigo_externo` no duplica. | 5 | S2 |
| HU-09 | E3 | Como despachador, quiero geocodificar al importar las direcciones sin coordenadas, para no ingresarlas a mano. | Usa Nominatim respetando 1 request por segundo y caché por dirección; los resultados de baja confianza quedan marcados "revisar" en el mapa. | 5 | S2 |
| HU-10 | E3 | Como despachador, quiero listar y filtrar envíos, para saber qué sale cada día. | `GET /api/shipments` paginado con filtros por fecha, estado y comuna; tabla en el frontend con búsqueda por tracking. | 5 | S3 |
| HU-11 | E4 | Como despachador, quiero ver la ventana aprendida de cada cliente y su confianza, para entender el orden propuesto. | `GET /api/customers/{id}/time-window` devuelve histograma, franja recomendada y confianza; el panel de la parada muestra un histograma de 24 h. | 5 | S3 |
| HU-12 | E4 | Como sistema, quiero priorizar intentos recientes y cachear el perfil, para aprender cambios de hábito sin recalcular siempre. | Decaimiento temporal configurable; perfil en Redis con TTL; la caché se invalida al registrar un intento. | 8 | S4 |
| HU-13 | E4 | Como despachador, quiero una ventana estimada para clientes nuevos, para ordenarlos aunque no tengan historial. | Si el cliente tiene menos de N intentos, se usa el perfil agregado de su comuna; la confianza se muestra como "baja". | 5 | S4 |
| HU-14 | E5 | Como despachador, quiero ver la ruta del día en el mapa con orden y hora estimada por parada, para validarla antes de salir. | ETA por parada calculada con tiempos de OSRM y duración de parada; respeta `orderPreference` Primera y Última; polilínea en Leaflet. | 5 | S3 |
| HU-15 | E5 | Como despachador, quiero mover paradas y fijar posiciones, para aplicar lo que sé del terreno. | Arrastrar para reordenar; una parada fijada no se mueve al reoptimizar; las ETA se recalculan. | 5 | S4 |
| HU-16 | E5 | Como despachador, quiero repartir los envíos entre varios camiones, para cubrir el volumen del día. | Solver VRP (OR-Tools o jsprit) con capacidad por paquetes y ventanas aprendidas; una ruta por vehículo; el resultado mejora la distancia total frente al nearest-neighbor actual. | 13 | S5 |
| HU-17 | E5 | Como despachador, quiero confirmar la ruta y asignarla a un conductor, para que la vea en su teléfono. | Estados de ruta: borrador, confirmada, en curso y cerrada; una ruta confirmada queda bloqueada para edición. | 3 | S4 |
| HU-18 | E6 | Como conductor, quiero ver mi ruta del día en el celular, para seguir el orden sin llamar al despachador. | Vista móvil con la siguiente parada destacada; botón para abrir la navegación en Google Maps o Waze. | 5 | S5 |
| HU-19 | E6 | Como conductor, quiero marcar entregado o rechazado en un toque, para registrar el resultado de cada parada. | Crea un `DeliveryAttempt` con hora del dispositivo y motivo (no está, dirección errónea, rechaza); el intento alimenta el aprendizaje de la ventana. | 5 | S5 |
| HU-20 | E6 | Como conductor, quiero registrar una prueba de entrega, para respaldar el envío ante reclamos. | Foto y nombre de quien recibe; se guarda en almacenamiento de objetos y aparece en el detalle del envío. | 5 | S6 |
| HU-21 | E7 | Como cliente final, quiero consultar mi envío con su código, para saber cuándo llega. | Página pública `/tracking/:codigo` sin login; muestra estado e historial de intentos. | 3 | S3 |
| HU-22 | E7 | Como cliente final, quiero ver la franja estimada y el camión en vivo, para estar en casa cuando llegue. | La posición del conductor se transmite por WebSocket o SSE; la página de tracking muestra el mapa y la franja estimada. | 8 | S6 |
| HU-23 | E8 | Como equipo, quiero observabilidad, para diagnosticar fallas entre servicios. | Health checks en compose; logs JSON con un `traceId` que viaja desde el gateway; métricas de Actuator expuestas. | 5 | S6 |
| HU-24 | E8 | Como equipo, quiero un ambiente de staging, para mostrar avances sin levantar nada local. | Despliegue automático desde `main`; secretos fuera del repo; URL pública documentada en el README. | 5 | S6 |

## Sprints

Sprints de 2 semanas desde el lunes 16 de noviembre de 2026. Cada sprint es un milestone de GitHub (`Sprint 1` … `Sprint 6`) y cierra con una demo.

| Sprint | Fechas | Objetivo | Historias | Pts | Demo al cierre |
| --- | --- | --- | --- | --- | --- |
| S1 | 16 nov – 27 nov 2026 | Base técnica confiable y roles seguros | HU-01, HU-02, HU-03, HU-05 | 16 | Todo levanta con datos semilla; un PR con error queda bloqueado por CI |
| S2 | 30 nov – 11 dic 2026 | Pruebas e importación robusta | HU-04, HU-06, HU-08, HU-09 | 21 | Importar una planilla con errores y ver el detalle por fila; direcciones geocodificadas solas |
| S3 | 14 dic – 25 dic 2026 | Envíos visibles y ruta con ETA | HU-07, HU-10, HU-11, HU-14, HU-21 | 23 | Ruta del día con ETA y ventana aprendida por parada; tracking público por código |
| S4 | 28 dic 2026 – 8 ene 2027 | Aprendizaje mejorado y ruta asignable | HU-12, HU-13, HU-15, HU-17 | 21 | Reordenar y fijar paradas, confirmar la ruta y asignarla; clientes nuevos con ventana estimada |
| S5 | 11 ene – 22 ene 2027 | Operación en terreno y multi-camión | HU-16, HU-18, HU-19 | 23 | Conductor registra entregas desde el celular; rutas repartidas entre 2 o más camiones |
| S6 | 25 ene – 5 feb 2027 | Cliente final y producción | HU-20, HU-22, HU-23, HU-24 | 23 | Camión en vivo en la página de tracking; prueba de entrega; staging desplegado desde `main` |

## Riesgos

| Riesgo | Impacto | Mitigación |
| --- | --- | --- |
| Sin intentos reales, el aprendizaje no se puede validar | El diferenciador del producto queda sin demostrar | Seed con historial sintético de patrones conocidos y pruebas que verifican que el motor los recupera (HU-01, HU-04) |
| Nominatim público limita a 1 request por segundo | Importaciones grandes lentas o bloqueadas | Caché por dirección, cola de geocodificación y opción de instancia propia (HU-09) |
| El VRP (HU-16) es la historia más grande e incierta | Sprint 5 se atrasa | Spike de 2 días al final del Sprint 4 para elegir entre OR-Tools y jsprit |
| Sprints 3 y 4 cruzan fiestas de fin de año | Menos capacidad | Planificar esos sprints con 20% menos de carga o mover historias de S4 a S5 |
| Un mismo `JWT_SECRET` en 4 servicios | Si se filtra, se compromete todo | Secretos fuera del repo desde HU-24; evaluar RS256 con clave pública más adelante |

## Convenciones de trabajo

- **Ramas:** `main` protegida; ramas `feature/HU-xx-descripcion-corta`, `fix/...` y `chore/...`; merge solo por PR con CI en verde.
- **Commits:** Conventional Commits con el ID de la historia, por ejemplo `feat(delivery): HU-12 decaimiento temporal en ventanas`.
- **PR:** enlaza su issue con `Closes #n`, describe cómo probarlo e incluye capturas si toca el frontend.
- **Issues:** uno por historia, con labels `sprint-N`, `epica:EN` y `tipo:*`, milestone del sprint y criterios de aceptación como checklist.
- **Tablero:** GitHub Projects con columnas Backlog, En curso, En revisión y Terminado.
- **Hallazgos fuera de alcance:** issue con `tipo:bug` o `tipo:chore` y nota en `docs/plan/hallazgos.md`.
- **Cierre de sprint:** `docs/plan/sprint-N-cierre.md` con lo completado, lo pendiente y los hallazgos.
- **Ceremonias:** planificación al inicio del sprint, revisión con demo al cierre y retrospectiva breve.
