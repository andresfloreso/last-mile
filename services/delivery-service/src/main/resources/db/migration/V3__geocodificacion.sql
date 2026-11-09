-- =====================================================================
-- V3 (HU-09): origen de las coordenadas de cada local.
--
--   PROVIDED  las entregó el usuario (planilla con latitud/longitud o pin en el mapa)
--   GEOCODED  las encontró Nominatim a nivel de número de calle o edificio
--   REVIEW    aproximadas (solo calle, barrio o comuna) o no encontradas:
--             el despachador debe revisarlas; el mapa las marca "revisar"
--
-- Los locales existentes con coordenadas quedan PROVIDED; los que no tienen,
-- REVIEW, porque no se pueden rutear hasta ubicarlos.
-- =====================================================================

ALTER TABLE customers
    ADD COLUMN geocode_status VARCHAR(20) NOT NULL DEFAULT 'PROVIDED';

UPDATE customers SET geocode_status = 'REVIEW' WHERE latitude IS NULL OR longitude IS NULL;

ALTER TABLE customers
    ADD CONSTRAINT ck_customers_geocode_status
        CHECK (geocode_status IN ('PROVIDED', 'GEOCODED', 'REVIEW'));

-- Listado de locales por revisar.
CREATE INDEX ix_customers_geocode_review ON customers (geocode_status) WHERE geocode_status = 'REVIEW';
