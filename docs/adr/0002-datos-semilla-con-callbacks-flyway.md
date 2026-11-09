# ADR 0002 — Datos semilla con callbacks `afterMigrate` de Flyway

- **Estado:** Aceptada
- **Fecha:** 2026-11-08
- **Historia:** HU-01 (Sprint 1)

## Contexto

Para desarrollar, probar y hacer demos se necesita un ambiente con datos
realistas: un usuario por rol, 30 clientes en Santiago, 60 días de intentos
de entrega con **patrones conocidos** (para demostrar que el motor de
ventanas horarias los aprende) y envíos para **la fecha de hoy** (50 al
inicio; 24 en una zona de 5 comunas desde el Sprint 3).

Requisitos:

1. Reproducible: mismo resultado en cada máquina y en CI.
2. Fuera del artefacto de producción: el JAR no debe poder sembrar datos de
   demo por accidente.
3. Idempotente: reiniciar los servicios no duplica datos.
4. "Hoy" debe ser hoy: si el ambiente se levanta otro día, deben existir los
   envíos de ese día.
5. El esquema solo se modifica vía Flyway (HU-02).

## Opciones consideradas

| Opción | A favor | En contra |
|---|---|---|
| **A. `CommandLineRunner` Java con `@Profile("seed")`** | Lógica en Java, tipado | El código de demo queda dentro del JAR de producción; requiere repetir la lógica en cada servicio; más difícil de revisar como datos |
| **B. Migración versionada `V100__seed.sql` en un perfil** | Simple | Corre una sola vez: al día siguiente los "envíos de hoy" quedan en el pasado; mezcla datos de demo con el historial de esquema |
| **C. Callback SQL `afterMigrate.sql` en `infra/seed/`, montado en el contenedor y agregado a `spring.flyway.locations` solo con el perfil `seed`** | Corre después de cada `migrate` (cumple 4); vive fuera del JAR (cumple 2); SQL plano, fácil de revisar; no queda en `flyway_schema_history` | El SQL debe escribirse idempotente a mano |

## Decisión

Se adopta la **opción C**.

- `infra/seed/auth/afterMigrate.sql` y `infra/seed/delivery/afterMigrate.sql`.
- Cada servicio trae un `application-seed.yml` que agrega
  `filesystem:${SEED_LOCATION:/seed}` a `spring.flyway.locations`.
- `docker-compose.override.yml` (versionado, solo desarrollo) monta
  `./infra/seed/<servicio>` en `/seed` (solo lectura) y activa
  `SPRING_PROFILES_ACTIVE=seed`. `docker-compose.yml` no lo activa
  (actualizado en Sprint 2, #25: antes venía activo por defecto).
- Idempotencia: UUIDs fijos o derivados con `md5(...)::uuid`, `ON CONFLICT DO
  NOTHING` y el historial se genera solo si los clientes semilla no tienen
  intentos. Los envíos del día usan tracking `SEED-<yyyymmdd>-<nn>`.
- Los patrones son deterministas (sin `random()`), así los tests de
  integración pueden afirmar qué ventana aprende el motor para cada cliente.

## Consecuencias

- Los tests de integración (`SeedDataIT` en auth y delivery) cargan la misma
  semilla real apuntando `SEED_LOCATION` a `infra/seed/...`, así que un cambio
  en la semilla que rompa el criterio de HU-01 falla en CI.
- Un cambio de esquema que afecte a la semilla debe actualizar el SQL de
  `infra/seed/` en el mismo PR.
- El perfil `seed` **nunca** debe activarse en staging/producción (HU-24
  deberá fijar `SPRING_PROFILES_ACTIVE` explícitamente).
- Las contraseñas semilla (`lastmile123`) son públicas por diseño y solo
  sirven para desarrollo.
