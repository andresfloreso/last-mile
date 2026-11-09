-- =====================================================================
-- Semilla de deliverydb (HU-01, HU-25) — SOLO desarrollo / demo.
--
-- Flyway ejecuta este callback después de cada `migrate` cuando el perfil
-- `seed` agrega esta carpeta a spring.flyway.locations. Es idempotente:
--   1. 30 locales comerciales de Santiago con UUID fijo (se actualizan si ya
--      existían, para que una base vieja reciba el tipo y la modulación).
--   2. Historial de 60 días de intentos con patrones conocidos; se genera una
--      sola vez (si los locales semilla aún no tienen intentos).
--   3. 50 envíos para la fecha de HOY; en cada arranque se asegura que
--      existan los del día (tracking SEED-<yyyymmdd>-<nn>).
--
-- Ventana real (columna `pattern`), en hora de America/Santiago:
--   MORNING   recibe 09–11 h, rechaza 14–17 h   (minimarkets; "local A")
--   MIDDAY    recibe 12–14 h, rechaza 09–11 h   (botillerías)
--   AFTERNOON recibe 15–17 h, rechaza 09–12 h   (restaurantes)
--   EVENING   recibe solo después de las 19 h, rechaza durante el día (bares; "local B")
-- ~70% de los intentos cae en la ventana real (ACCEPTED) y ~30% fuera de ella
-- (REJECTED_NOT_HOME, "local cerrado"), de forma determinista.
--
-- Modulación (HU-25): lo que el local DECLARA. 19 locales declaran su
-- ventana real; 11 (columna `modulacion_distinta`, más de un tercio) declaran
-- otro horario, como pasa en terreno: "recibo de 9 a 12" y en verdad recibe
-- en la tarde. Los tests verifican que el motor aprende la ventana real.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. Catálogo de locales semilla (tablas temporales de esta sesión)
-- ---------------------------------------------------------------------
DROP VIEW IF EXISTS seed_customer_v;
DROP TABLE IF EXISTS seed_attempt, seed_pattern, seed_customer;

CREATE TEMP TABLE seed_customer (
    idx                 INT PRIMARY KEY,
    full_name           TEXT,
    business_type       TEXT,
    address             TEXT,
    commune             TEXT,
    lat                 DOUBLE PRECISION,
    lng                 DOUBLE PRECISION,
    pattern             TEXT,
    modulacion_distinta BOOLEAN
);

-- Coordenadas reales en cada comuna (plaza, municipalidad o hito conocido).
-- El patrón rota con idx % 4 para repartir las ventanas por toda la ciudad.
INSERT INTO seed_customer (idx, full_name, business_type, address, commune, lat, lng, pattern, modulacion_distinta) VALUES
    ( 1, 'Minimarket Don Lucho',      'MINIMARKET', 'Plaza de Armas s/n',                         'Santiago',            -33.4378, -70.6504, 'MORNING',   false),
    ( 2, 'Botillería El Roble',       'BOTILLERIA', 'Av. Providencia 2124',                       'Providencia',         -33.4210, -70.6080, 'MIDDAY',    false),
    ( 3, 'Restaurant El Apoquindo',   'RESTAURANT', 'Av. Apoquindo 3000',                         'Las Condes',          -33.4166, -70.5974, 'AFTERNOON', true),
    ( 4, 'Bar La Esquina de Ñuñoa',   'BAR',        'Av. Irarrázaval 3550',                       'Ñuñoa',               -33.4542, -70.5981, 'EVENING',   true),
    ( 5, 'Minimarket La Reina',       'MINIMARKET', 'Av. Larraín 9750',                           'La Reina',            -33.4505, -70.5410, 'MORNING',   false),
    ( 6, 'Botillería Vitacura',       'BOTILLERIA', 'Av. Vitacura 3600',                          'Vitacura',            -33.4004, -70.5966, 'MIDDAY',    true),
    ( 7, 'Restaurant El Arrayán',     'RESTAURANT', 'Av. Raúl Labbé 12100',                       'Lo Barnechea',        -33.3530, -70.5170, 'AFTERNOON', false),
    ( 8, 'Bar El Macul',              'BAR',        'Av. Macul 3200',                             'Macul',               -33.4893, -70.5994, 'EVENING',   false),
    ( 9, 'Minimarket Los Andes',      'MINIMARKET', 'Av. Grecia 8735',                            'Peñalolén',           -33.4697, -70.5498, 'MORNING',   true),
    (10, 'Botillería La Florida',     'BOTILLERIA', 'Av. Vicuña Mackenna 7110',                   'La Florida',          -33.5202, -70.5985, 'MIDDAY',    false),
    (11, 'Restaurant La Plaza',       'RESTAURANT', 'Plaza de Puente Alto, Av. Concha y Toro 1',  'Puente Alto',         -33.6118, -70.5756, 'AFTERNOON', false),
    (12, 'Bar La Gran Avenida',       'BAR',        'Gran Avenida José Miguel Carrera 4000',      'San Miguel',          -33.4925, -70.6520, 'EVENING',   true),
    (13, 'Minimarket Santa Rosa',     'MINIMARKET', 'Av. Santa Rosa 2900',                        'San Joaquín',         -33.4917, -70.6402, 'MORNING',   false),
    (14, 'Botillería El Paradero',    'BOTILLERIA', 'Gran Avenida José Miguel Carrera 8700',      'La Cisterna',         -33.5317, -70.6649, 'MIDDAY',    false),
    (15, 'Restaurant Lo Martínez',    'RESTAURANT', 'Av. Lo Martínez 1155',                       'El Bosque',           -33.5603, -70.6780, 'AFTERNOON', true),
    (16, 'Bar Maestranza',            'BAR',        'Eyzaguirre 450',                             'San Bernardo',        -33.5925, -70.7046, 'EVENING',   false),
    (17, 'Minimarket 5 de Abril',     'MINIMARKET', 'Av. 5 de Abril 0260',                        'Maipú',               -33.5103, -70.7570, 'MORNING',   false),
    (18, 'Botillería Cerrillos',      'BOTILLERIA', 'Av. Pedro Aguirre Cerda 6800',               'Cerrillos',           -33.4958, -70.7148, 'MIDDAY',    true),
    (19, 'Restaurant Alameda',        'RESTAURANT', 'Av. Libertador Bernardo O''Higgins 3363',    'Estación Central',    -33.4513, -70.6856, 'AFTERNOON', false),
    (20, 'Bar Carrascal',             'BAR',        'Av. Carrascal 4447',                         'Quinta Normal',       -33.4246, -70.6982, 'EVENING',   false),
    (21, 'Minimarket San Pablo',      'MINIMARKET', 'Av. San Pablo 5959',                         'Lo Prado',            -33.4436, -70.7246, 'MORNING',   true),
    (22, 'Botillería Pudahuel',       'BOTILLERIA', 'Av. San Pablo 8444',                         'Pudahuel',            -33.4412, -70.7596, 'MIDDAY',    false),
    (23, 'Restaurant Mapocho',        'RESTAURANT', 'Av. Mapocho 6810',                           'Cerro Navia',         -33.4262, -70.7417, 'AFTERNOON', false),
    (24, 'Bar Santa María',           'BAR',        'Av. Domingo Santa María 3540',               'Renca',               -33.4055, -70.7270, 'EVENING',   true),
    (25, 'Minimarket Quilicura',      'MINIMARKET', 'Av. Manuel Antonio Matta 830',               'Quilicura',           -33.3606, -70.7296, 'MORNING',   false),
    (26, 'Botillería Huechuraba',     'BOTILLERIA', 'Av. Recoleta 5600',                          'Huechuraba',          -33.3727, -70.6365, 'MIDDAY',    false),
    (27, 'Restaurant Independencia',  'RESTAURANT', 'Av. Independencia 3499',                     'Conchalí',            -33.3857, -70.6745, 'AFTERNOON', true),
    (28, 'Bar El Tranque',            'BAR',        'Av. Independencia 753',                      'Independencia',       -33.4206, -70.6560, 'EVENING',   false),
    (29, 'Minimarket Recoleta',       'MINIMARKET', 'Av. Recoleta 2774',                          'Recoleta',            -33.4067, -70.6420, 'MORNING',   false),
    (30, 'Botillería Departamental',  'BOTILLERIA', 'Av. Departamental 2700',                     'Pedro Aguirre Cerda', -33.4866, -70.6753, 'MIDDAY',    true);

-- Ventana real por patrón (horas en que recibe y en que rechaza) y la
-- modulación que declara un local que SÍ dice la verdad.
CREATE TEMP TABLE seed_pattern (
    pattern        TEXT PRIMARY KEY,
    accept_hours   INT[],
    reject_hours   INT[],
    declared_start TIME,
    declared_end   TIME
);

INSERT INTO seed_pattern VALUES
    ('MORNING',   ARRAY[9, 10],  ARRAY[14, 15, 16], '09:00', '12:00'),
    ('MIDDAY',    ARRAY[12, 13], ARRAY[9, 10, 11],  '12:00', '15:00'),
    ('AFTERNOON', ARRAY[15, 16], ARRAY[9, 10, 11],  '15:00', '18:00'),
    ('EVENING',   ARRAY[19, 19], ARRAY[11, 15, 17], '18:00', '21:00');

-- Modulación final: si el local declara otro horario, el típico 09–12 (o
-- 15–18 si en verdad recibe en la mañana). La Botillería Huechuraba (26)
-- declara además un segundo tramo, de 17 a 19 h.
CREATE TEMP VIEW seed_customer_v AS
SELECT c.*,
       ('5eed0000-0000-4000-8000-' || lpad(c.idx::text, 12, '0'))::uuid AS customer_id,
       'SEED-C' || lpad(c.idx::text, 2, '0')                             AS external_code,
       CASE WHEN NOT c.modulacion_distinta THEN p.declared_start
            WHEN c.pattern = 'MORNING'     THEN TIME '15:00'
            ELSE TIME '09:00' END                                       AS decl_start,
       CASE WHEN NOT c.modulacion_distinta THEN p.declared_end
            WHEN c.pattern = 'MORNING'     THEN TIME '18:00'
            ELSE TIME '12:00' END                                       AS decl_end,
       CASE WHEN c.idx = 26 THEN TIME '17:00' END                       AS decl2_start,
       CASE WHEN c.idx = 26 THEN TIME '19:00' END                       AS decl2_end
FROM seed_customer c
JOIN seed_pattern p USING (pattern);

-- ---------------------------------------------------------------------
-- 1. Locales (se actualizan si ya existían: bases creadas con la semilla
--    de HU-01 pasan a locales comerciales con tipo y modulación)
-- ---------------------------------------------------------------------
INSERT INTO customers (id, full_name, phone, address, commune, latitude, longitude, external_code,
                       business_type, declared_window_start, declared_window_end,
                       declared_window2_start, declared_window2_end)
SELECT customer_id, full_name, '+5622' || lpad((1000000 + idx * 7919)::text, 7, '0'),
       address, commune, lat, lng, external_code,
       business_type, decl_start, decl_end, decl2_start, decl2_end
FROM seed_customer_v
ON CONFLICT (id) DO UPDATE SET
    full_name              = EXCLUDED.full_name,
    phone                  = EXCLUDED.phone,
    business_type          = EXCLUDED.business_type,
    declared_window_start  = EXCLUDED.declared_window_start,
    declared_window_end    = EXCLUDED.declared_window_end,
    declared_window2_start = EXCLUDED.declared_window2_start,
    declared_window2_end   = EXCLUDED.declared_window2_end;

-- ---------------------------------------------------------------------
-- 2. Historial de 60 días (una sola vez)
--    Cada local tiene un intento cada 3 días (20 intentos). k numera los
--    intentos del local; k % 10 IN (2, 5, 8) => intento en franja mala.
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
       CASE WHEN rejected THEN 'Local cerrado' END
FROM seed_attempt
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------
-- 3. 50 envíos para HOY (n = 1..50 -> local ((n - 1) % 30) + 1).
--    Los locales 1..20 reciben 2 envíos y los 21..30 reciben 1.
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
