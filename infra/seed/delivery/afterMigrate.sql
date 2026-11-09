-- =====================================================================
-- Semilla de deliverydb (HU-01, HU-25) — SOLO desarrollo / demo.
--
-- Flyway ejecuta este callback después de cada `migrate` cuando el perfil
-- `seed` agrega esta carpeta a spring.flyway.locations. Es idempotente:
--   1. 30 locales comerciales de Santiago con UUID fijo (se actualizan si ya
--      existían, para que una base vieja reciba el tipo y la modulación).
--      25 están en 5 comunas vecinas (Santiago, Providencia, Ñuñoa, Macul y
--      San Joaquín, 5 por comuna) y 5 fuera de esa zona.
--   2. Historial de 60 días de intentos con patrones conocidos; se genera una
--      sola vez (si los locales semilla aún no tienen intentos).
--   3. 24 envíos para la fecha de HOY, todos en la zona de 5 comunas, para que
--      un camión alcance a hacer la ruta dentro del horario. En cada arranque
--      se asegura que existan los del día (tracking SEED-<yyyymmdd>-<nn>).
--
-- Ventana real (columna `pattern`), en hora de America/Santiago:
--   MORNING   recibe 09–11 h, rechaza 14–17 h   (minimarkets; "local A")
--   MIDDAY    recibe 12–14 h, rechaza 09–11 h   (botillerías)
--   AFTERNOON recibe 15–17 h, rechaza 09–12 h   (restaurantes)
--   EVENING   recibe solo después de las 19 h, rechaza durante el día (bares; "local B")
-- Cada local tiene 20 intentos: 14 en su ventana real y 6 fuera de ella
-- (REJECTED_NOT_HOME, "local cerrado"). Dentro de la ventana no recibe
-- siempre: entre 1 y 4 de las 14 visitas son "encargado no estaba", así que
-- acepta entre 71% y 93% (10 a 13 de 14), de forma determinista. Los rechazos
-- dentro de la ventana se reparten entre sus dos horas para que el motor siga
-- aprendiendo la misma ventana.
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

-- Coordenadas aproximadas de calles reales de cada comuna. El patrón rota con
-- idx % 4 para repartir las ventanas. Los locales 3, 6, 7, 11 y 16 quedan fuera
-- de la zona de reparto del día (Las Condes, Vitacura, Lo Barnechea, Puente
-- Alto y San Bernardo): tienen historial pero hoy no reciben.
INSERT INTO seed_customer (idx, full_name, business_type, address, commune, lat, lng, pattern, modulacion_distinta) VALUES
    ( 1, 'Minimarket Don Lucho',            'MINIMARKET', 'Plaza de Armas s/n',                       'Santiago',     -33.4378, -70.6504, 'MORNING',   false),
    ( 2, 'Botillería El Roble',             'BOTILLERIA', 'Av. Providencia 2124',                     'Providencia',  -33.4210, -70.6080, 'MIDDAY',    false),
    ( 3, 'Restaurant El Apoquindo',         'RESTAURANT', 'Av. Apoquindo 3000',                       'Las Condes',   -33.4166, -70.5974, 'AFTERNOON', true),
    ( 4, 'Bar La Esquina de Ñuñoa',         'BAR',        'Av. Irarrázaval 3550',                     'Ñuñoa',        -33.4542, -70.5981, 'EVENING',   true),
    ( 5, 'Minimarket Sierra Bella',         'MINIMARKET', 'Av. Sierra Bella 2000',                    'San Joaquín',  -33.4810, -70.6250, 'MORNING',   false),
    ( 6, 'Botillería Vitacura',             'BOTILLERIA', 'Av. Vitacura 3600',                        'Vitacura',     -33.4004, -70.5966, 'MIDDAY',    true),
    ( 7, 'Restaurant El Arrayán',           'RESTAURANT', 'Av. Raúl Labbé 12100',                     'Lo Barnechea', -33.3530, -70.5170, 'AFTERNOON', false),
    ( 8, 'Bar El Macul',                    'BAR',        'Av. Macul 3200',                           'Macul',        -33.4893, -70.5994, 'EVENING',   false),
    ( 9, 'Minimarket Los Andes',            'MINIMARKET', 'Av. Grecia 2040',                          'Ñuñoa',        -33.4668, -70.6010, 'MORNING',   true),
    (10, 'Botillería Quilín',               'BOTILLERIA', 'Av. Quilín 3500',                          'Macul',        -33.4940, -70.5930, 'MIDDAY',    false),
    (11, 'Restaurant La Plaza',             'RESTAURANT', 'Plaza de Puente Alto, Av. Concha y Toro 1', 'Puente Alto', -33.6118, -70.5756, 'AFTERNOON', false),
    (12, 'Bar Dublé Almeyda',               'BAR',        'Dublé Almeyda 2850',                       'Ñuñoa',        -33.4500, -70.6045, 'EVENING',   true),
    (13, 'Minimarket Santa Rosa',           'MINIMARKET', 'Av. Santa Rosa 2900',                      'San Joaquín',  -33.4917, -70.6402, 'MORNING',   false),
    (14, 'Botillería El Paradero',          'BOTILLERIA', 'Av. Macul 2500',                           'Macul',        -33.4800, -70.6000, 'MIDDAY',    false),
    (15, 'Restaurant Alessandri',           'RESTAURANT', 'Av. José Pedro Alessandri 1200',           'Macul',        -33.4777, -70.5933, 'AFTERNOON', true),
    (16, 'Bar Maestranza',                  'BAR',        'Eyzaguirre 450',                           'San Bernardo', -33.5925, -70.7046, 'EVENING',   false),
    (17, 'Minimarket Exequiel Fernández',   'MINIMARKET', 'Av. Exequiel Fernández 3000',              'Macul',        -33.4820, -70.6120, 'MORNING',   false),
    (18, 'Botillería Carlos Valdovinos',    'BOTILLERIA', 'Av. Carlos Valdovinos 600',                'San Joaquín',  -33.4772, -70.6350, 'MIDDAY',    true),
    (19, 'Restaurant Alameda',              'RESTAURANT', 'Av. Libertador Bernardo O''Higgins 1302',  'Santiago',     -33.4447, -70.6555, 'AFTERNOON', false),
    (20, 'Bar Bellavista',                  'BAR',        'Constitución 40',                          'Providencia',  -33.4335, -70.6345, 'EVENING',   false),
    (21, 'Minimarket Los Leones',           'MINIMARKET', 'Av. Los Leones 1100',                      'Providencia',  -33.4290, -70.6006, 'MORNING',   true),
    (22, 'Botillería Manuel Montt',         'BOTILLERIA', 'Av. Manuel Montt 1000',                    'Providencia',  -33.4337, -70.6176, 'MIDDAY',    false),
    (23, 'Restaurant Mapocho',              'RESTAURANT', 'Mapocho 1520',                             'Santiago',     -33.4319, -70.6624, 'AFTERNOON', false),
    (24, 'Bar Santa María',                 'BAR',        'Av. Santa María 2150',                     'Providencia',  -33.4249, -70.6155, 'EVENING',   true),
    (25, 'Minimarket Plaza Ñuñoa',          'MINIMARKET', 'Jorge Washington 26',                      'Ñuñoa',        -33.4566, -70.5947, 'MORNING',   false),
    (26, 'Botillería Villa Frei',           'BOTILLERIA', 'Av. Ramón Cruz 1350',                      'Ñuñoa',        -33.4610, -70.5800, 'MIDDAY',    false),
    (27, 'Restaurant San Diego',            'RESTAURANT', 'San Diego 860',                            'Santiago',     -33.4560, -70.6505, 'AFTERNOON', true),
    (28, 'Bar El Tranque',                  'BAR',        'Bandera 520',                              'Santiago',     -33.4362, -70.6530, 'EVENING',   false),
    (29, 'Minimarket Isabel Riquelme',      'MINIMARKET', 'Av. Isabel Riquelme 1000',                 'San Joaquín',  -33.4730, -70.6400, 'MORNING',   false),
    (30, 'Botillería Departamental',        'BOTILLERIA', 'Av. Departamental 1200',                   'San Joaquín',  -33.4870, -70.6300, 'MIDDAY',    true);

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
    address                = EXCLUDED.address,
    commune                = EXCLUDED.commune,
    latitude               = EXCLUDED.latitude,
    longitude              = EXCLUDED.longitude,
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

-- Ruido dentro de la ventana real: el local no siempre recibe en su franja.
-- Cada local tiene 1 + idx % 4 visitas en su ventana con "encargado no estaba"
-- (de 14: acepta 13, 12, 11 o 10). Se reparten entre las dos horas de la
-- ventana (la mitad en cada una, ordenadas por fecha) para que ninguna hora
-- quede con saldo negativo y el motor aprenda la misma ventana.
ALTER TABLE seed_attempt ADD COLUMN absent BOOLEAN NOT NULL DEFAULT false;
WITH ranked AS (
    SELECT idx, days_ago,
           extract(hour FROM attempted_at AT TIME ZONE 'America/Santiago')::int AS hour,
           accept_hours,
           1 + idx % 4 AS quota,
           row_number() OVER (
               PARTITION BY idx, extract(hour FROM attempted_at AT TIME ZONE 'America/Santiago')
               ORDER BY days_ago) AS rn
    FROM seed_attempt
    WHERE NOT rejected
)
UPDATE seed_attempt s SET absent = true
FROM ranked r
WHERE s.idx = r.idx AND s.days_ago = r.days_ago
  AND r.rn <= CASE WHEN r.accept_hours[1] = r.accept_hours[2] THEN r.quota
                   WHEN r.hour = r.accept_hours[1] THEN (r.quota + 1) / 2
                   ELSE r.quota / 2 END;

-- Cada intento histórico pertenece a un envío histórico de ese día.
INSERT INTO shipments (id, tracking_code, customer_id, scheduled_date, status, source, notes,
                       stop_type, package_count, order_preference, label, created_at)
SELECT md5('seed-hist-shipment-' || idx || '-' || days_ago)::uuid,
       'SEED-H-' || to_char(day, 'YYYYMMDD') || '-' || lpad(idx::text, 2, '0'),
       customer_id, day,
       CASE WHEN rejected OR absent THEN 'RESCHEDULED' ELSE 'DELIVERED' END,
       'seed', 'Envío histórico de semilla (patrón ' || c.pattern || ')', 'DELIVERY', 1, 'AUTO', 'Default',
       attempted_at - interval '1 day'
FROM seed_attempt
JOIN seed_customer c USING (idx)
ON CONFLICT (tracking_code) DO NOTHING;

INSERT INTO delivery_attempts (id, shipment_id, customer_id, attempted_at, outcome, rejection_reason)
SELECT md5('seed-hist-attempt-' || idx || '-' || days_ago)::uuid,
       md5('seed-hist-shipment-' || idx || '-' || days_ago)::uuid,
       customer_id, attempted_at,
       CASE WHEN rejected OR absent THEN 'REJECTED_NOT_HOME' ELSE 'ACCEPTED' END,
       CASE WHEN rejected THEN 'Local cerrado' WHEN absent THEN 'Encargado no estaba' END
FROM seed_attempt
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------
-- 3. 24 envíos para HOY: uno por local de la zona de 5 comunas, salvo el 29
--    (así la franja de la mañana, 09–11, tiene 7 locales y alcanza).
--    Por franja: mañana 7, mediodía 7, tarde 4, noche 6.
-- ---------------------------------------------------------------------
INSERT INTO shipments (id, tracking_code, customer_id, scheduled_date, status, source, notes,
                       stop_type, package_count, order_preference, label, created_at)
SELECT md5('seed-today-' || CURRENT_DATE || '-' || n)::uuid,
       'SEED-' || to_char(CURRENT_DATE, 'YYYYMMDD') || '-' || lpad(n::text, 2, '0'),
       c.customer_id, CURRENT_DATE, 'PENDING', 'seed',
       'Envío de semilla (patrón ' || c.pattern || ')',
       'DELIVERY', 1 + n % 3, 'AUTO', 'Default', now()
FROM (SELECT idx, row_number() OVER (ORDER BY idx) AS n
      FROM seed_customer
      WHERE idx NOT IN (3, 6, 7, 11, 16, 29)) AS today
JOIN seed_customer_v c USING (idx)
ON CONFLICT (tracking_code) DO NOTHING;

-- ---------------------------------------------------------------------
-- Limpieza de objetos temporales
-- ---------------------------------------------------------------------
DROP VIEW IF EXISTS seed_customer_v;
DROP TABLE IF EXISTS seed_attempt, seed_pattern, seed_customer;
