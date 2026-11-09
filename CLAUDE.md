# CLAUDE.md — reglas del proyecto LastMile

Este archivo lo lee cualquier sesión de Claude Code (u otro agente) que trabaje
en el repo. Las reglas son obligatorias; si algo choca con ellas, pregunta
antes de actuar.

## Al iniciar una sesión

1. `git checkout main && git pull` (o `git pull` en la rama en curso).
2. Si existe `docs/plan/en-curso.md`, léelo primero: dice qué quedó a medias
   y cuál es el siguiente paso exacto.
3. Antes de empezar un sprint, lee `docs/plan/plan-de-trabajo.md`.

## Qué es LastMile

Plataforma de ruteo para **distribución B2B**: una distribuidora reparte con
sus camiones a locales comerciales (botillerías, minimarkets, bares,
restaurantes). Cada local declara un horario de recepción (**modulación**),
pero muchos reciben en otro horario. LastMile aprende, local por local, su
**ventana real** a partir de los intentos de entrega, y con eso ordena la
ruta. El indicador principal es la **entrega en primera visita**.

- **Fuente de verdad del plan:** [`docs/plan/plan-de-trabajo.md`](docs/plan/plan-de-trabajo.md)
  (historias, criterios, sprints, riesgos). Si cambian el alcance o las fechas,
  actualízalo en el mismo PR.
- **Vocabulario:** usa el glosario del plan en código visible, UI y docs:
  *local*, *modulación*, *ventana real*, *intento de entrega*, *motivo de no
  entrega*, *primera visita*, *despachador*, *conductor*. No digas "cliente
  final" ni "franja preferida" cuando hablas de un local.
- No cambies la lógica de `TimeWindowLearningService` ni de
  `RouteOptimizationService` sin que la historia lo pida.

## Estructura

```
services/
  api-gateway/       Spring Cloud Gateway (Java 21): valida JWT y enruta
  auth-service/      Spring Boot: login, registro, usuarios y roles (authdb)
  delivery-service/  Spring Boot: locales, envíos, intentos, ventanas y rutas (deliverydb)
  import-service/    Node 20 + TypeScript + Express: planillas .xlsx/.csv
frontend/            React + Vite + TypeScript + Tailwind + Zustand
infra/postgres/      init de PostgreSQL (crea authdb y deliverydb)
infra/seed/          datos semilla (callbacks afterMigrate de Flyway, perfil seed)
docs/adr/            decisiones de arquitectura
docs/api/            documentación de APIs (springdoc)
docs/plan/           plan, cierres de sprint, hallazgos, en-curso
.github/workflows/   ci.yml (java, node, docker-build) y secret-scan.yml (gitleaks)
```

## Cómo levantarlo

```bash
cp .env.example .env            # reemplaza los valores reemplaza_... (compose falla si faltan)
docker compose up --build --wait
docker compose down -v          # borra los datos (la semilla se vuelve a cargar al subir)
```

- Java: solo JDK 21. Usa siempre el wrapper: `./mvnw verify` (Bash) o
  `.\mvnw.cmd verify` (PowerShell), nunca `mvn`. Los tests de integración
  (`*IT`) usan Testcontainers con PostgreSQL real, así que necesitan Docker.
  No uses H2.
- Node: `npm ci` (no `npm install`), luego `npm run lint` y `npm run build`.
  Los lockfiles están versionados.
- Frontend en desarrollo: `cd frontend && VITE_API_BASE_URL=http://localhost:8080 npm run dev`.
- Usuarios semilla: ver `infra/seed/README.md`.

## Definición de terminado

- Cumple los criterios de aceptación del issue y tiene pruebas unitarias
  (JUnit 5 + Mockito) para la lógica nueva, más integración con Testcontainers
  donde aplique.
- `./mvnw verify` y `npm run build` pasan, y el CI está en verde (11 checks
  obligatorios en `main`).
- El esquema solo cambia con migraciones Flyway (`V<n>__descripcion.sql`).
  Si el cambio afecta a la semilla, actualiza `infra/seed/` en el mismo PR.
- Los endpoints nuevos se documentan con springdoc/OpenAPI.
- Sin secretos en commits. Las variables nuevas van en `.env.example` con un
  valor `reemplaza_...`.

## Reglas de trabajo

- **Una rama y un PR por historia o ítem:** `feature/HU-xx-descripcion-corta`,
  `fix/...`, `chore/...`, `docs/...`, siempre desde `main` actualizado.
- **Commits:** Conventional Commits con el ID de la historia, por ejemplo
  `feat(delivery): HU-25 modulación declarada por local`.
- **PR:** enlaza su issue con `Closes #n`. Describe la historia, los cambios
  y cómo probarlo, e incluye el checklist de criterios marcado.
- **Frontend:** todo PR que toque `frontend/` incluye capturas de las
  pantallas cambiadas, tomadas con Playwright y subidas en la descripción.
- **Nunca** hagas push directo a `main`, force-push sobre ramas compartidas
  ni merge de un PR sin aprobación explícita del dueño del repo. Los PRs
  quedan abiertos para revisión.
- **Alcance:** un bug o deuda fuera del alcance de la historia en curso no se
  arregla de pasada. Se registra como hallazgo (ver abajo).

## GitHub

- **Issues:** uno por historia, con labels `sprint-N`, `epica:EN` y
  `tipo:feature|chore|bug`, el milestone del sprint, los criterios de
  aceptación como checklist `- [ ]` y los puntos. Revisa `gh issue list`
  antes de crear uno, para no duplicar.
- **Épicas:** E1 = HU-01..04 y chores de repo; E2 = HU-05..07; E3 = HU-08..10
  y HU-25; E4 = HU-11..13; E5 = HU-14..17 y HU-27; E6 = HU-18..20 y HU-26;
  E7 = HU-21..22; E8 = HU-23..24.
- **Tablero** (GitHub Project "LastMile"): Backlog → En curso (al crear la
  rama) → En revisión (al abrir el PR) → Terminado (al mergear). Comenta en el
  issue el nombre de la rama y marca los criterios a medida que se cumplen.
- **Hallazgos fuera de alcance:** un issue con `tipo:bug` o `tipo:chore`, sin
  milestone salvo que se planifique, más una fila en
  `docs/plan/hallazgos.md`.
- **Cierre de sprint:** `docs/plan/sprint-N-cierre.md` con lo completado, lo
  pendiente, los hallazgos y los riesgos del siguiente sprint, y el resumen en
  la descripción del milestone (los milestones no admiten comentarios).
- Con el `gh` de esta máquina, `gh pr edit` puede fallar. Para editar la
  descripción de un PR usa `gh api -X PATCH repos/andresfloreso/last-mile/pulls/<n> -F body=@archivo.md`.

## Si la sesión se va a cortar

1. Haz commit de lo que esté en curso con el prefijo `wip:`
   (`wip(delivery): HU-25 migración V2 sin tests`) y haz push de la rama.
2. Crea o actualiza `docs/plan/en-curso.md` en esa misma rama, con la rama, el
   issue, qué está hecho, qué falta y **el siguiente paso exacto** (archivo,
   comando o test).
3. Al retomar y terminar el ítem, borra `docs/plan/en-curso.md` en el mismo PR.
