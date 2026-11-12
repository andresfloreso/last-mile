-- =====================================================================
-- V5 (HU-15): ajuste del tiempo de descarga por local.
--
-- El tiempo que el camión pasa en cada parada ya no es fijo (stopMinutes):
-- es base (routing.stop-minutes, 8 min) + minutos por bulto
-- (routing.unload.per-package-minutes, 1,5) + este ajuste del local, por
-- ejemplo "+10 min, estacionamiento difícil". Los locales existentes quedan
-- sin ajuste (0 min).
-- =====================================================================

ALTER TABLE customers
    ADD COLUMN unload_extra_minutes INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN unload_note VARCHAR(120);

ALTER TABLE customers
    ADD CONSTRAINT ck_customers_unload_extra CHECK (unload_extra_minutes BETWEEN 0 AND 120);
