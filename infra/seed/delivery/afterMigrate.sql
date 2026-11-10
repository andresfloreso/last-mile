-- =====================================================================
-- Semilla de deliverydb (HU-01, HU-25, HU-31) — SOLO desarrollo / demo.
--
-- Flyway ejecuta este callback después de cada `migrate` cuando el perfil
-- `seed` agrega esta carpeta a spring.flyway.locations. Es idempotente:
--   1. 30 locales comerciales de Santiago con UUID fijo (se actualizan si ya
--      existían, para que una base vieja reciba el tipo y la modulación).
--      Todos están en una zona compacta de 3 comunas contiguas (Santiago,
--      Providencia y Ñuñoa, 10 por comuna), en direcciones reales (HU-31).
--   2. Historial de 60 días de intentos con patrones conocidos; se genera una
--      sola vez (si los locales semilla aún no tienen intentos).
--   3. 24 envíos para la fecha de HOY, uno por local salvo 6 locales, para que
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

-- HU-31: direcciones reales de calles comerciales en una zona compacta de 3
-- comunas contiguas (Santiago, Providencia y Ñuñoa; 10 locales por comuna, en
-- una caja de unos 6 × 6 km). Los nombres de los locales son inventados.
-- Coordenadas fijas (no se geocodifica en runtime), tomadas de OpenStreetMap
-- (Nominatim, con el número exacto) y verificadas: cada punto queda a menos de
-- 40 m de una calle (OSRM /nearest) y fuera de parques, estadios, canchas y
-- hospitales (Overpass). Ver infra/seed/README.md.
-- El patrón rota con idx % 4 para repartir las ventanas. Los locales 3, 6, 7,
-- 11, 16 y 29 hoy no tienen pedido (tienen historial).
INSERT INTO seed_customer (idx, full_name, business_type, address, commune, lat, lng, pattern, modulacion_distinta) VALUES
    ( 1, 'Minimarket Don Lucho',         'MINIMARKET', 'Merced 300',                              'Santiago',    -33.437229, -70.640901, 'MORNING',   false),
    ( 2, 'Botillería El Roble',          'BOTILLERIA', 'Av. Providencia 2124',                    'Providencia', -33.422072, -70.611427, 'MIDDAY',    false),
    ( 3, 'Restaurant El Encuentro',      'RESTAURANT', 'Av. Providencia 2300',                    'Providencia', -33.420578, -70.607977, 'AFTERNOON', true),
    ( 4, 'Bar La Esquina de Ñuñoa',      'BAR',        'Av. Irarrázaval 3550',                    'Ñuñoa',       -33.454179, -70.593643, 'EVENING',   true),
    ( 5, 'Minimarket Simón Bolívar',     'MINIMARKET', 'Av. Simón Bolívar 3500',                  'Ñuñoa',       -33.448593, -70.594306, 'MORNING',   false),
    ( 6, 'Botillería Los Leones',        'BOTILLERIA', 'Av. Los Leones 400',                      'Providencia', -33.423419, -70.604389, 'MIDDAY',    true),
    ( 7, 'Restaurant Lastarria',         'RESTAURANT', 'José Victorino Lastarria 70',             'Santiago',    -33.439582, -70.641336, 'AFTERNOON', false),
    ( 8, 'Bar El Farol',                 'BAR',        'Lira 600',                                'Santiago',    -33.450374, -70.638593, 'EVENING',   false),
    ( 9, 'Minimarket Los Andes',         'MINIMARKET', 'Av. Ricardo Lyon 2900',                   'Ñuñoa',       -33.447757, -70.602405, 'MORNING',   true),
    (10, 'Botillería Santa Rosa',        'BOTILLERIA', 'Av. Santa Rosa 500',                      'Santiago',    -33.450207, -70.644395, 'MIDDAY',    false),
    (11, 'Restaurant Nueva Providencia', 'RESTAURANT', 'Av. Nueva Providencia 1900',              'Providencia', -33.425280, -70.613628, 'AFTERNOON', false),
    (12, 'Bar Dublé Almeyda',            'BAR',        'Dublé Almeyda 2850',                      'Ñuñoa',       -33.455885, -70.601745, 'EVENING',   true),
    (13, 'Minimarket Carmen',            'MINIMARKET', 'Carmen 500',                              'Santiago',    -33.450027, -70.641394, 'MORNING',   false),
    (14, 'Botillería El Paradero',       'BOTILLERIA', 'Av. Irarrázaval 2100',                    'Ñuñoa',       -33.453503, -70.609235, 'MIDDAY',    false),
    (15, 'Restaurant Barrio Italia',     'RESTAURANT', 'Av. Italia 1180',                         'Providencia', -33.445361, -70.625269, 'AFTERNOON', true),
    (16, 'Bar Bellavista',               'BAR',        'Pío Nono 150',                            'Providencia', -33.432768, -70.636110, 'EVENING',   false),
    (17, 'Minimarket Cañas',             'MINIMARKET', 'José Domingo Cañas 2200',                 'Ñuñoa',       -33.456723, -70.608382, 'MORNING',   false),
    (18, 'Botillería Portugal',          'BOTILLERIA', 'Av. Portugal 1000',                       'Santiago',    -33.455322, -70.633432, 'MIDDAY',    true),
    (19, 'Restaurant Alameda',           'RESTAURANT', 'Av. Libertador Bernardo O''Higgins 1302', 'Santiago',    -33.445884, -70.653969, 'AFTERNOON', false),
    (20, 'Bar Manuel Montt',             'BAR',        'Av. Manuel Montt 1050',                   'Providencia', -33.437644, -70.615823, 'EVENING',   false),
    (21, 'Minimarket Pedro de Valdivia', 'MINIMARKET', 'Av. Pedro de Valdivia 1215',              'Providencia', -33.434241, -70.608945, 'MORNING',   true),
    (22, 'Botillería Manuel Montt',      'BOTILLERIA', 'Av. Manuel Montt 2600',                   'Ñuñoa',       -33.451307, -70.612790, 'MIDDAY',    false),
    (23, 'Restaurant Monjitas',          'RESTAURANT', 'Monjitas 500',                            'Santiago',    -33.436711, -70.644546, 'AFTERNOON', false),
    (24, 'Bar Bilbao',                   'BAR',        'Av. Francisco Bilbao 500',                'Providencia', -33.441534, -70.625609, 'EVENING',   true),
    (25, 'Minimarket Plaza Ñuñoa',       'MINIMARKET', 'Jorge Washington 26',                     'Ñuñoa',       -33.454837, -70.594391, 'MORNING',   false),
    (26, 'Botillería Chile España',      'BOTILLERIA', 'Chile España 400',                        'Ñuñoa',       -33.450969, -70.598213, 'MIDDAY',    false),
    (27, 'Restaurant San Diego',         'RESTAURANT', 'San Diego 860',                           'Santiago',    -33.456309, -70.649562, 'AFTERNOON', true),
    (28, 'Bar El Tranque',               'BAR',        'Arturo Prat 800',                         'Santiago',    -33.455309, -70.647987, 'EVENING',   false),
    (29, 'Minimarket Irarrázaval',       'MINIMARKET', 'Av. Irarrázaval 3000',                    'Ñuñoa',       -33.454343, -70.599834, 'MORNING',   false),
    (30, 'Botillería Providencia',       'BOTILLERIA', 'Av. Providencia 1308',                    'Providencia', -33.428584, -70.620205, 'MIDDAY',    true);

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
-- 15–18 si en verdad recibe en la mañana). La Botillería Chile España (26)
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
-- 3. 24 envíos para HOY: uno por local, salvo los 6 que hoy no piden (3, 6, 7, 11, 16 y 29)
--    Así la franja de la mañana (09–11) tiene 7 locales y alcanza.
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
