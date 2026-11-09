-- =====================================================================
-- V2 (HU-25): tipo de local y modulación declarada en customers.
--
-- La modulación es el horario de recepción que el local DECLARA. Es distinta
-- de la ventana real, que el motor aprende de los intentos de entrega.
-- Admite un segundo tramo opcional (por ejemplo, 09–12 y 16–18).
-- Todas las columnas son opcionales: los locales existentes quedan sin tipo
-- ni modulación.
-- =====================================================================

ALTER TABLE customers
    ADD COLUMN business_type          VARCHAR(20),
    ADD COLUMN declared_window_start  TIME,
    ADD COLUMN declared_window_end    TIME,
    ADD COLUMN declared_window2_start TIME,
    ADD COLUMN declared_window2_end   TIME;

ALTER TABLE customers
    ADD CONSTRAINT ck_customers_business_type
        CHECK (business_type IN ('BOTILLERIA', 'MINIMARKET', 'BAR', 'RESTAURANT', 'OTRO')),

    -- Primer tramo: ambos extremos o ninguno, y el inicio antes del término.
    ADD CONSTRAINT ck_customers_declared_window
        CHECK ((declared_window_start IS NULL AND declared_window_end IS NULL)
            OR (declared_window_start IS NOT NULL AND declared_window_end IS NOT NULL
                AND declared_window_start < declared_window_end)),

    -- Segundo tramo: opcional, requiere el primero y empieza después de que este termina.
    ADD CONSTRAINT ck_customers_declared_window2
        CHECK ((declared_window2_start IS NULL AND declared_window2_end IS NULL)
            OR (declared_window2_start IS NOT NULL AND declared_window2_end IS NOT NULL
                AND declared_window_end IS NOT NULL
                AND declared_window2_start < declared_window2_end
                AND declared_window2_start >= declared_window_end));

-- El perfil agregado por comuna y tipo de local (HU-13) filtra por estas columnas.
CREATE INDEX ix_customers_commune_business_type ON customers (commune, business_type);
