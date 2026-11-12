package cl.lastmile.delivery.algorithm.optimizer;

/**
 * Tiempo de descarga en una parada (HU-15): base + minutos por bulto + ajuste del local.
 *
 * <p>Reemplaza el {@code stopMinutes} fijo de HU-14, que queda como la base. Por ejemplo, con la base
 * por defecto (8 min) y 1,5 min por bulto, una parada de 6 bultos en un local con "+10 min,
 * estacionamiento difícil" toma 8 + 9 + 10 = 27 min.
 *
 * @param baseMinutes       minutos fijos de cada parada (estacionar, firmar, salir)
 * @param perPackageMinutes minutos por cada bulto que se baja
 */
public record UnloadTime(int baseMinutes, double perPackageMinutes) {

    public UnloadTime {
        if (baseMinutes < 0) throw new IllegalArgumentException("baseMinutes < 0");
        if (perPackageMinutes < 0) throw new IllegalArgumentException("perPackageMinutes < 0");
    }

    /** El tiempo fijo de HU-14: los mismos minutos en cada parada, sin importar los bultos. */
    public static UnloadTime fixed(int minutes) {
        return new UnloadTime(minutes, 0);
    }

    /** Segundos de descarga de una parada con {@code packages} bultos y el ajuste de su local. */
    public int seconds(int packages, int extraMinutes) {
        double minutes = baseMinutes + perPackageMinutes * Math.max(0, packages) + Math.max(0, extraMinutes);
        return (int) Math.round(minutes * 60);
    }
}
