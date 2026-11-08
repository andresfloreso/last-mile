-- =====================================================================
-- Semilla de deliverydb (HU-01) — SOLO desarrollo / demo.
--
-- Flyway ejecuta este callback después de cada `migrate` cuando el perfil
-- `seed` agrega esta carpeta a spring.flyway.locations. Es idempotente:
--   1. 30 clientes con UUID fijo (ON CONFLICT DO NOTHING).
--   2. Historial de 60 días de intentos con patrones conocidos; se genera una
--      sola vez (si los clientes semilla aún no tienen intentos).
--   3. 50 envíos para la fecha de HOY; en cada arranque se asegura que
--      existan los del día (tracking SEED-<yyyymmdd>-<nn>).
--
-- Patrones (columna `pattern`), en hora de America/Santiago:
--   MORNING   acepta 09–11 h, rechaza 14–17 h   ("cliente A")
--   MIDDAY    acepta 12–14 h, rechaza 09–11 h
--   AFTERNOON acepta 15–17 h, rechaza 09–12 h
--   EVENING   acepta solo después de las 19 h, rechaza durante el día ("cliente B")
-- ~70% de los intentos cae en la franja buena (ACCEPTED) y ~30% en la mala
-- (REJECTED_NOT_HOME), de forma determinista: siempre los mismos datos.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. Catálogo de clientes semilla (tablas temporales de esta sesión)
-- ---------------------------------------------------------------------
DROP VIEW IF EXISTS seed_customer_v;
DROP TABLE IF EXISTS seed_attempt, seed_pattern, seed_customer;

CREATE TEMP TABLE seed_customer (
    idx       INT PRIMARY KEY,
    full_name TEXT,
    address   TEXT,
    commune   TEXT,
    lat       DOUBLE PRECISION,
    lng       DOUBLE PRECISION,
    pattern   TEXT
);

-- Coordenadas reales en cada comuna (plaza, municipalidad o hito conocido).
-- El patrón se asigna rotando idx % 4 para repartir las franjas por toda la ciudad.
INSERT INTO seed_customer (idx, full_name, address, commune, lat, lng, pattern) VALUES
    ( 1, 'Andrea Muñoz',      'Plaza de Armas s/n',                  'Santiago',            -33.4378, -70.6504, 'MORNING'),
    ( 2, 'Benjamín Rojas',    'Av. Providencia 2124',                'Providencia',         -33.4210, -70.6080, 'MIDDAY'),
    ( 3, 'Catalina Soto',     'Av. Apoquindo 3000',                  'Las Condes',          -33.4166, -70.5974, 'AFTERNOON'),
    ( 4, 'Diego Contreras',   'Av. Irarrázaval 3550',                'Ñuñoa',               -33.4542, -70.5981, 'EVENING'),
    ( 5, 'Elena Silva',       'Av. Larraín 9750',                    'La Reina',            -33.4505, -70.5410, 'MORNING'),
    ( 6, 'Felipe Martínez',   'Av. Vitacura 3600',                   'Vitacura',            -33.4004, -70.5966, 'MIDDAY'),
    ( 7, 'Gabriela Sepúlveda','Av. Raúl Labbé 12100',                'Lo Barnechea',        -33.3530, -70.5170, 'AFTERNOON'),
    ( 8, 'Hernán Morales',    'Av. Macul 3200',                      'Macul',               -33.4893, -70.5994, 'EVENING'),
    ( 9, 'Isidora Fuentes',   'Av. Grecia 8735',                     'Peñalolén',           -33.4697, -70.5498, 'MORNING'),
    (10, 'Joaquín Torres',    'Av. Vicuña Mackenna 7110',            'La Florida',          -33.5202, -70.5985, 'MIDDAY'),
    (11, 'Karina Araya',      'Plaza de Puente Alto, Av. Concha y Toro 1', 'Puente Alto',  -33.6118, -70.5756, 'AFTERNOON'),
    (12, 'Luis Espinoza',     'Gran Avenida José Miguel Carrera 4000','San Miguel',         -33.4925, -70.6520, 'EVENING'),
    (13, 'María Valenzuela',  'Av. Santa Rosa 2900',                 'San Joaquín',         -33.4917, -70.6402, 'MORNING'),
    (14, 'Nicolás Castillo',  'Gran Avenida José Miguel Carrera 8700','La Cisterna',        -33.5317, -70.6649, 'MIDDAY'),
    (15, 'Olivia Tapia',      'Av. Lo Martínez 1155',                'El Bosque',           -33.5603, -70.6780, 'AFTERNOON'),
    (16, 'Pablo Reyes',       'Eyzaguirre 450',                      'San Bernardo',        -33.5925, -70.7046, 'EVENING'),
    (17, 'Paula Quiroz', 'Av. 5 de Abril 0260',                 'Maipú',               -33.5103, -70.7570, 'MORNING'),
    (18, 'Rodrigo Pizarro',   'Av. Pedro Aguirre Cerda 6800',        'Cerrillos',           -33.4958, -70.7148, 'MIDDAY'),
    (19, 'Sofía Herrera',     'Av. Libertador Bernardo O''Higgins 3363', 'Estación Central', -33.4513, -70.6856, 'AFTERNOON'),
    (20, 'Tomás Núñez',       'Av. Carrascal 4447',                  'Quinta Normal',       -33.4246, -70.6982, 'EVENING'),
    (21, 'Valentina Jara',    'Av. San Pablo 5959',                  'Lo Prado',            -33.4436, -70.7246, 'MORNING'),
    (22, 'Vicente Carrasco',  'Av. San Pablo 8444',                  'Pudahuel',            -33.4412, -70.7596, 'MIDDAY'),
    (23, 'Ximena Vergara',    'Av. Mapocho 6810',                    'Cerro Navia',         -33.4262, -70.7417, 'AFTERNOON'),
    (24, 'Yerko Figueroa',    'Av. Domingo Santa María 3540',        'Renca',               -33.4055, -70.7270, 'EVENING'),
    (25, 'Zoe Cortés',        'Av. Manuel Antonio Matta 830',        'Quilicura',           -33.3606, -70.7296, 'MORNING'),
    (26, 'Agustín Navarro',   'Av. Recoleta 5600',                   'Huechuraba',          -33.3727, -70.6365, 'MIDDAY'),
    (27, 'Blanca Olivares',   'Av. Independencia 3499',              'Conchalí',            -33.3857, -70.6745, 'AFTERNOON'),
    (28, 'Cristóbal Lagos',   'Av. Independencia 753',               'Independencia',       -33.4206, -70.6560, 'EVENING'),
    (29, 'Daniela Riquelme',  'Av. Recoleta 2774',                   'Recoleta',            -33.4067, -70.6420, 'MORNING'),
    (30, 'Emilio Salazar',    'Av. Departamental 2700',              'Pedro Aguirre Cerda', -33.4866, -70.6753, 'MIDDAY');

-- Franjas por patrón: horas en que acepta y horas en que rechaza.
CREATE TEMP TABLE seed_pattern (
    pattern      TEXT PRIMARY KEY,
    accept_hours INT[],
    reject_hours INT[]
);

INSERT INTO seed_pattern VALUES
    ('MORNING',   ARRAY[9, 10],  ARRAY[14, 15, 16]),
    ('MIDDAY',    ARRAY[12, 13], ARRAY[9, 10, 11]),
    ('AFTERNOON', ARRAY[15, 16], ARRAY[9, 10, 11]),
    ('EVENING',   ARRAY[19, 19], ARRAY[11, 15, 17]);

CREATE TEMP VIEW seed_customer_v AS
SELECT c.*,
       ('5eed0000-0000-4000-8000-' || lpad(c.idx::text, 12, '0'))::uuid AS customer_id,
       'SEED-C' || lpad(c.idx::text, 2, '0')                             AS external_code
FROM seed_customer c;

-- ---------------------------------------------------------------------
-- 1. Clientes
-- ---------------------------------------------------------------------
INSERT INTO customers (id, full_name, phone, address, commune, latitude, longitude, external_code)
SELECT customer_id, full_name, '+5698' || lpad((1000000 + idx * 7919)::text, 7, '0'),
       address, commune, lat, lng, external_code
FROM seed_customer_v
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------
-- 2. Historial de 60 días (una sola vez)
--    Cada cliente tiene un intento cada 3 días (20 intentos). k numera los
--    intentos del cliente; k % 10 IN (2, 5, 8) => intento en franja mala.
-- ---------------------------------------------------------------------
CREATE TEMP TABLE seed_attempt AS
SELECT c.customer_id,
       c.idx,
       d                                         AS days_ago,
       (d + c.idx) / 3                           AS k,
       ((d + c.idx) / 3) % 10 IN (2, 5, 8)       AS rejected,
       p.accept_hours,
       p.reject_hours,
       (CURRENT_DATE - d)                        AS day
FROM seed_customer_v c
JOIN seed_pattern p USING (pattern)
CROSS JOIN generate_series(1, 60) AS d
WHERE (d + c.idx) % 3 = 0
  AND NOT EXISTS (
        SELECT 1 FROM delivery_attempts a
        WHERE a.customer_id IN (SELECT customer_id FROM seed_customer_v));

ALTER TABLE seed_attempt ADD COLUMN attempted_at TIMESTAMPTZ;
UPDATE seed_attempt SET attempted_at =
    (day + make_time(
        CASE WHEN rejected
             THEN reject_hours[1 + (k / 3) % array_length(reject_hours, 1)]
             ELSE accept_hours[1 + k % array_length(accept_hours, 1)] END,
        (idx * 7 + days_ago * 13) % 60,
        0)) AT TIME ZONE 'America/Santiago';

-- Cada intento histórico pertenece a un envío histórico de ese día.
INSERT INTO shipments (id, tracking_code, customer_id, scheduled_date, status, source, notes,
                       stop_type, package_count, order_preference, label, created_at)
SELECT md5('seed-hist-shipment-' || idx || '-' || days_ago)::uuid,
       'SEED-H-' || to_char(day, 'YYYYMMDD') || '-' || lpad(idx::text, 2, '0'),
       customer_id, day,
       CASE WHEN rejected THEN 'RESCHEDULED' ELSE 'DELIVERED' END,
       'seed', 'Envío histórico de semilla', 'DELIVERY', 1, 'AUTO', 'Default',
       attempted_at - interval '1 day'
FROM seed_attempt
ON CONFLICT (tracking_code) DO NOTHING;

INSERT INTO delivery_attempts (id, shipment_id, customer_id, attempted_at, outcome, rejection_reason)
SELECT md5('seed-hist-attempt-' || idx || '-' || days_ago)::uuid,
       md5('seed-hist-shipment-' || idx || '-' || days_ago)::uuid,
       customer_id, attempted_at,
       CASE WHEN rejected THEN 'REJECTED_NOT_HOME' ELSE 'ACCEPTED' END,
       CASE WHEN rejected THEN 'No había nadie en el domicilio' END
FROM seed_attempt
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------
-- 3. 50 envíos para HOY (n = 1..50 -> cliente ((n - 1) % 30) + 1).
--    Los clientes 1..20 reciben 2 envíos y los 21..30 reciben 1.
-- ---------------------------------------------------------------------
INSERT INTO shipments (id, tracking_code, customer_id, scheduled_date, status, source, notes,
                       stop_type, package_count, order_preference, label, created_at)
SELECT md5('seed-today-' || CURRENT_DATE || '-' || n)::uuid,
       'SEED-' || to_char(CURRENT_DATE, 'YYYYMMDD') || '-' || lpad(n::text, 2, '0'),
       c.customer_id, CURRENT_DATE, 'PENDING', 'seed',
       'Envío de semilla (patrón ' || c.pattern || ')',
       'DELIVERY', 1 + n % 3, 'AUTO', 'Default', now()
FROM generate_series(1, 50) AS n
JOIN seed_customer_v c ON c.idx = ((n - 1) % 30) + 1
ON CONFLICT (tracking_code) DO NOTHING;

-- ---------------------------------------------------------------------
-- Limpieza de objetos temporales
-- ---------------------------------------------------------------------
DROP VIEW IF EXISTS seed_customer_v;
DROP TABLE IF EXISTS seed_attempt, seed_pattern, seed_customer;
