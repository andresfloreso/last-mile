package cl.lastmile.delivery.algorithm.optimizer;

import cl.lastmile.delivery.geo.OsrmRoutingClient;

import java.util.List;
import java.util.Optional;

/**
 * Tiempos y distancias entre el depósito (punto 0) y los locales (HU-28).
 *
 * <p>Salen de la tabla de OSRM. Si OSRM no responde, o un par no tiene ruta, se estiman igual que
 * en la hora estimada (HU-14, {@code RouteTimingService}): línea recta × {@value #DETOUR_FACTOR} a
 * la velocidad urbana de respaldo. Así el optimizador y el horario que ve el despachador usan la
 * misma regla.
 */
public final class TravelTimes {

    static final double DETOUR_FACTOR = 1.3;

    private final int[][] seconds;
    private final int[][] meters;
    private final boolean estimated;

    TravelTimes(int[][] seconds, int[][] meters, boolean estimated) {
        this.seconds = seconds;
        this.meters = meters;
        this.estimated = estimated;
    }

    /**
     * @param points            (lat, lng) del depósito y de cada local con coordenadas, en ese orden
     * @param fallbackSpeedKmh  velocidad para estimar los pares sin dato de OSRM
     */
    public static TravelTimes of(List<double[]> points, OsrmRoutingClient osrm, double fallbackSpeedKmh) {
        Optional<OsrmRoutingClient.Table> table = osrm.table(points);
        int n = points.size();
        int[][] seconds = new int[n][n];
        int[][] meters = new int[n][n];
        double metersPerSecond = fallbackSpeedKmh * 1000.0 / 3600.0;
        double[][] osrmSeconds = table.map(OsrmRoutingClient.Table::seconds).orElse(null);
        double[][] osrmMeters = table.map(OsrmRoutingClient.Table::meters).orElse(null);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double s = osrmSeconds == null ? Double.NaN : osrmSeconds[i][j];
                double m = osrmMeters == null ? Double.NaN : osrmMeters[i][j];
                if (Double.isNaN(s) || Double.isNaN(m)) {
                    m = i == j ? 0 : haversineMeters(points.get(i), points.get(j)) * DETOUR_FACTOR;
                    s = m / metersPerSecond;
                }
                seconds[i][j] = (int) Math.round(s);
                meters[i][j] = (int) Math.round(m);
            }
        }
        return new TravelTimes(seconds, meters, table.isEmpty());
    }

    public int seconds(int from, int to) {
        return seconds[from][to];
    }

    public int meters(int from, int to) {
        return meters[from][to];
    }

    /** true si OSRM no respondió y todo es estimado. */
    public boolean estimated() {
        return estimated;
    }

    static double haversineMeters(double[] a, double[] b) {
        double dLat = Math.toRadians(b[0] - a[0]);
        double dLon = Math.toRadians(b[1] - a[1]);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a[0])) * Math.cos(Math.toRadians(b[0]))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6_371_000.0 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}
