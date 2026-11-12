package cl.lastmile.delivery.service;

import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.model.Shipment.OrderPreference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Paradas consolidadas (HU-30): los envíos del mismo local en la misma fecha forman una sola
 * parada. La ruta ordena paradas, no envíos, así que el camión no visita dos veces el mismo local.
 *
 * <p>Cada parada tiene un <b>envío representante</b>, que es el que recibe el optimizador. Así
 * {@code RouteOptimizationService} ordena paradas sin cambiar su lógica. El representante es el
 * envío que define la preferencia de orden de la parada:
 * <ul>
 *   <li>si algún envío pide ir <b>Primera</b>, la parada va primera;</li>
 *   <li>si no, si alguno pide ir <b>Última</b>, la parada va última;</li>
 *   <li>si no, es automática.</li>
 * </ul>
 * Entre los envíos con esa preferencia, el representante es el de tracking menor (determinista).
 */
public final class StopConsolidator {

    private StopConsolidator() {}

    /**
     * Lo que se entrega en una parada: sus envíos (el representante primero) y la suma de su peso y
     * volumen (HU-29). Peso y volumen son null si no se conocen (paradas armadas sin consolidar).
     *
     * @param unloadExtraMinutes ajuste de descarga del local (HU-15), 0 si no tiene
     * @param unloadNote         por qué el local tiene ajuste ("estacionamiento difícil"), o null
     */
    public record StopLoad(List<UUID> shipmentIds, Double weightKg, Double volumeM3,
                           int unloadExtraMinutes, String unloadNote) {

        public StopLoad {
            shipmentIds = List.copyOf(shipmentIds);
        }

        public StopLoad(List<UUID> shipmentIds, Double weightKg, Double volumeM3) {
            this(shipmentIds, weightKg, volumeM3, 0, null);
        }

        /** Parada de un solo envío, sin peso ni volumen conocidos. */
        public static StopLoad single(UUID shipmentId) {
            return new StopLoad(List.of(shipmentId), null, null);
        }
    }

    /** Una parada: un local, una fecha y sus envíos (el representante va primero). */
    public record DeliveryStop(Shipment representative, List<Shipment> shipments) {

        public DeliveryStop {
            Objects.requireNonNull(representative);
            shipments = List.copyOf(shipments);
        }

        public UUID customerId() {
            return representative.getCustomer().getId();
        }

        public List<UUID> shipmentIds() {
            return shipments.stream().map(Shipment::getId).toList();
        }

        /** Suma de los bultos de los envíos de la parada. */
        public int packageCount() {
            return shipments.stream().mapToInt(s -> s.getPackageCount() == null ? 0 : s.getPackageCount()).sum();
        }

        /** Suma del peso de los envíos de la parada, en kg (HU-29). */
        public double weightKg() {
            return shipments.stream().mapToDouble(s -> s.getWeightKg() == null ? 0 : s.getWeightKg()).sum();
        }

        /** Suma del volumen de los envíos de la parada, en m³ (HU-29). */
        public double volumeM3() {
            return shipments.stream().mapToDouble(s -> s.getVolumeM3() == null ? 0 : s.getVolumeM3()).sum();
        }

        public StopLoad load() {
            Customer customer = representative.getCustomer();
            return new StopLoad(shipmentIds(), weightKg(), volumeM3(),
                    customer == null ? 0 : customer.getUnloadExtraMinutes(),
                    customer == null ? null : customer.getUnloadNote());
        }
    }

    private static final Comparator<Shipment> BY_TRACKING =
            Comparator.comparing(Shipment::getTrackingCode, Comparator.nullsLast(Comparator.naturalOrder()));

    /**
     * Agrupa los envíos por local. Las paradas salen en el orden del primer tracking de cada local,
     * y dentro de cada parada el representante va primero y el resto por tracking.
     */
    public static List<DeliveryStop> consolidate(List<Shipment> shipments) {
        Map<UUID, List<Shipment>> byCustomer = new LinkedHashMap<>();
        shipments.stream().sorted(BY_TRACKING)
                .forEach(s -> byCustomer.computeIfAbsent(s.getCustomer().getId(), k -> new ArrayList<>()).add(s));

        List<DeliveryStop> stops = new ArrayList<>();
        for (List<Shipment> group : byCustomer.values()) {
            Shipment representative = representative(group);
            List<Shipment> ordered = new ArrayList<>();
            ordered.add(representative);
            group.stream().filter(s -> s != representative).forEach(ordered::add);
            stops.add(new DeliveryStop(representative, ordered));
        }
        return stops;
    }

    private static Shipment representative(List<Shipment> group) {
        for (OrderPreference preference : List.of(OrderPreference.FIRST, OrderPreference.LAST)) {
            for (Shipment s : group) {
                if (s.getOrderPreference() == preference) return s;
            }
        }
        return group.get(0);
    }
}
