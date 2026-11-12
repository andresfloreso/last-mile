-- =====================================================================
-- V6 (HU-17): la ruta del día como entidad, con su estado y su conductor.
--
-- Hasta ahora la ruta solo existía como la posición de cada envío
-- (shipments.route_position). La tabla routes guarda, por fecha:
--   - el estado: DRAFT (borrador), CONFIRMED (confirmada), IN_PROGRESS
--     (en curso) y CLOSED (cerrada). Solo un borrador se puede rearmar o
--     reordenar a mano;
--   - con qué optimizador y con qué camión se armó;
--   - el conductor asignado (el id de usuario de auth-service y su nombre al
--     momento de asignarlo: los usuarios viven en otra base, sin FK).
-- El orden de las paradas sigue en shipments.route_position; route_id dice a
-- qué ruta pertenece cada envío.
--
-- Una ruta por fecha mientras haya un camión por día. HU-16 (varios camiones)
-- cambiará la clave única a (route_date, vehicle_id).
-- =====================================================================

CREATE TABLE routes (
    id             UUID                        NOT NULL,
    route_date     DATE                        NOT NULL,
    status         VARCHAR(20)                 NOT NULL DEFAULT 'DRAFT',
    optimizer      VARCHAR(20),
    optimizer_note TEXT,
    vehicle_id     UUID,
    driver_id      UUID,
    driver_name    VARCHAR(120),
    confirmed_at   TIMESTAMP(6) WITH TIME ZONE,
    created_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT pk_routes PRIMARY KEY (id),
    CONSTRAINT uk_routes_date UNIQUE (route_date),
    CONSTRAINT fk_routes_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    CONSTRAINT ck_routes_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'IN_PROGRESS', 'CLOSED')),
    CONSTRAINT ck_routes_driver CHECK (status = 'DRAFT' OR driver_id IS NOT NULL)
);

ALTER TABLE shipments
    ADD COLUMN route_id UUID,
    ADD CONSTRAINT fk_shipments_route FOREIGN KEY (route_id) REFERENCES routes (id);

CREATE INDEX ix_shipments_route ON shipments (route_id);

-- Las rutas que ya estaban armadas (envíos con posición) quedan como borrador.
INSERT INTO routes (id, route_date, status)
SELECT gen_random_uuid(), scheduled_date, 'DRAFT'
FROM shipments
WHERE route_position IS NOT NULL
GROUP BY scheduled_date;

UPDATE shipments s
SET route_id = r.id
FROM routes r
WHERE s.route_position IS NOT NULL AND s.scheduled_date = r.route_date;
