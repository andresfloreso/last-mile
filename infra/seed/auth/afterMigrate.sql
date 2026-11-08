-- =====================================================================
-- Semilla de authdb (HU-01) — SOLO desarrollo / demo.
--
-- Flyway ejecuta este callback después de cada `migrate` cuando el perfil
-- `seed` agrega esta carpeta a spring.flyway.locations. Es idempotente: se
-- puede correr cualquier cantidad de veces sin duplicar datos.
--
-- Un usuario por rol. Contraseña de todos: lastmile123
-- (hash bcrypt factor 12, el mismo algoritmo que usa auth-service).
-- =====================================================================

INSERT INTO users (id, full_name, email, password_hash, role, enabled, created_at)
VALUES
    ('5eed0000-0000-4000-8000-000000000001', 'Admin Semilla',      'admin@lastmile.cl',     '$2a$12$dq0d21MQD4YqaQTmmlJIieBqZ1hL.T8jhHnUeigvhcPIuNGHZu/bu', 'ADMIN',      TRUE, now()),
    ('5eed0000-0000-4000-8000-000000000002', 'Daniela Despacho',   'despacho@lastmile.cl',  '$2a$12$dq0d21MQD4YqaQTmmlJIieBqZ1hL.T8jhHnUeigvhcPIuNGHZu/bu', 'DISPATCHER', TRUE, now()),
    ('5eed0000-0000-4000-8000-000000000003', 'Carlos Conductor',   'conductor@lastmile.cl', '$2a$12$dq0d21MQD4YqaQTmmlJIieBqZ1hL.T8jhHnUeigvhcPIuNGHZu/bu', 'DRIVER',     TRUE, now()),
    ('5eed0000-0000-4000-8000-000000000004', 'Olga Operaciones',   'operador@lastmile.cl',  '$2a$12$dq0d21MQD4YqaQTmmlJIieBqZ1hL.T8jhHnUeigvhcPIuNGHZu/bu', 'OPERATOR',   TRUE, now()),
    ('5eed0000-0000-4000-8000-000000000005', 'Camila Cliente',     'cliente@lastmile.cl',   '$2a$12$dq0d21MQD4YqaQTmmlJIieBqZ1hL.T8jhHnUeigvhcPIuNGHZu/bu', 'CLIENT',     TRUE, now())
ON CONFLICT (email) DO NOTHING;
