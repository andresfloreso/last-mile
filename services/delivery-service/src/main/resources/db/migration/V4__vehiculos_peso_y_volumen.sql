-- =====================================================================
-- V4 (HU-29): camiones con capacidad y peso/volumen por envío.
--
-- vehicles: camión de la distribuidora, con patente, capacidad (kg y m³) y
-- jornada (hora de inicio y de término). "active = false" lo da de baja sin
-- borrarlo, para no perder las rutas que ya tuvo.
--
-- shipments.weight_kg / volume_m3: peso y volumen del envío. Los envíos que
-- ya existían no los tenían: quedan con el valor por defecto documentado en
-- docs/api/README.md (10 kg y 0,05 m³ por envío). Los envíos nuevos sin peso o
-- volumen toman shipments.default-weight-kg / default-volume-m3 (configurables;
-- por defecto, los mismos valores).
-- =====================================================================

CREATE TABLE vehicles (
    id          UUID                        NOT NULL,
    plate       VARCHAR(12)                 NOT NULL,
    name        VARCHAR(80),
    capacity_kg DOUBLE PRECISION            NOT NULL,
    capacity_m3 DOUBLE PRECISION            NOT NULL,
    shift_start TIME                        NOT NULL,
    shift_end   TIME                        NOT NULL,
    active      BOOLEAN                     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT pk_vehicles PRIMARY KEY (id),
    CONSTRAINT uk_vehicles_plate UNIQUE (plate),
    CONSTRAINT ck_vehicles_capacity CHECK (capacity_kg > 0 AND capacity_m3 > 0),
    CONSTRAINT ck_vehicles_shift CHECK (shift_start < shift_end)
);

ALTER TABLE shipments
    ADD COLUMN weight_kg DOUBLE PRECISION NOT NULL DEFAULT 10,
    ADD COLUMN volume_m3 DOUBLE PRECISION NOT NULL DEFAULT 0.05;

ALTER TABLE shipments
    ADD CONSTRAINT ck_shipments_weight_volume CHECK (weight_kg > 0 AND volume_m3 > 0);
