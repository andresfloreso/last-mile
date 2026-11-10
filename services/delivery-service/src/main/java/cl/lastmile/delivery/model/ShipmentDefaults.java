package cl.lastmile.delivery.model;

/**
 * Peso y volumen de un envío sin dato (HU-29). Son los valores de la migración V4 para los
 * envíos que ya existían; para los nuevos se pueden cambiar con shipments.default-weight-kg y
 * shipments.default-volume-m3 (ver ShipmentService).
 */
public final class ShipmentDefaults {

    public static final double WEIGHT_KG = 10.0;
    public static final double VOLUME_M3 = 0.05;

    private ShipmentDefaults() {}
}
