package cl.lastmile.delivery.service;

import cl.lastmile.delivery.exception.BadRequestException;
import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.model.Shipment.ShipmentStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Filtros del listado de envíos (HU-10). Todos son opcionales y se combinan con AND.
 *
 * <p>La comuna y la búsqueda libre no distinguen mayúsculas ni tildes: "nunoa"
 * encuentra "Ñuñoa" y "botilleria" encuentra "Botillería".
 *
 * @param from     primer día programado (inclusive)
 * @param to       último día programado (inclusive)
 * @param statuses estados aceptados; vacío = todos
 * @param commune  comuna del local (coincidencia exacta, sin tildes ni mayúsculas)
 * @param query    texto a buscar en el nombre del local, su dirección, el tracking o el código externo
 */
public record ShipmentFilter(LocalDate from, LocalDate to, Set<ShipmentStatus> statuses, String commune, String query) {

    private static final String ACCENTED = "ÁÀÄÂÉÈËÊÍÌÏÎÓÒÖÔÚÙÜÛÑáàäâéèëêíìïîóòöôúùüûñ";
    private static final String PLAIN = "AAAAEEEEIIIIOOOOUUUUNaaaaeeeeiiiioooouuuun";

    public ShipmentFilter {
        statuses = statuses == null || statuses.isEmpty() ? EnumSet.noneOf(ShipmentStatus.class) : EnumSet.copyOf(statuses);
        commune = blankToNull(commune);
        query = blankToNull(query);
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("El rango de fechas es inválido: 'from' (" + from + ") es posterior a 'to' (" + to + ")");
        }
    }

    /**
     * Construye el filtro desde los parámetros de la request. {@code date} es un atajo
     * para un solo día y no se puede combinar con {@code from}/{@code to}.
     */
    public static ShipmentFilter of(LocalDate date, LocalDate from, LocalDate to,
                                    List<String> statuses, String commune, String query) {
        if (date != null && (from != null || to != null)) {
            throw new BadRequestException("Usa 'date' para un día o 'from'/'to' para un rango, no ambos");
        }
        Set<ShipmentStatus> parsed = EnumSet.noneOf(ShipmentStatus.class);
        if (statuses != null) {
            for (String raw : statuses) {
                // Acepta status=PENDING&status=ROUTED y también status=PENDING,ROUTED
                for (String value : raw.split(",")) {
                    if (!value.isBlank()) parsed.add(RequestValues.enumValue(ShipmentStatus.class, value, "status"));
                }
            }
        }
        return date != null
                ? new ShipmentFilter(date, date, parsed, commune, query)
                : new ShipmentFilter(from, to, parsed, commune, query);
    }

    public Specification<Shipment> toSpecification() {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledDate"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("scheduledDate"), to));
            if (!statuses.isEmpty()) predicates.add(root.get("status").in(statuses));

            if (commune != null || query != null) {
                Join<Shipment, Customer> customer = root.join("customer");
                if (commune != null) {
                    predicates.add(cb.equal(normalized(cb, customer.get("commune")), normalize(commune)));
                }
                if (query != null) {
                    String pattern = "%" + escapeLike(normalize(query)) + "%";
                    predicates.add(cb.or(
                            cb.like(normalized(cb, customer.get("fullName")), pattern, '\\'),
                            cb.like(normalized(cb, customer.get("address")), pattern, '\\'),
                            cb.like(normalized(cb, customer.get("externalCode")), pattern, '\\'),
                            cb.like(normalized(cb, root.get("trackingCode")), pattern, '\\')));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** lower(translate(x, 'Ñá…', 'Na…')) en PostgreSQL: quita tildes antes de bajar a minúsculas. */
    private static Expression<String> normalized(CriteriaBuilder cb, Expression<String> column) {
        return cb.lower(cb.function("translate", String.class, column, cb.literal(ACCENTED), cb.literal(PLAIN)));
    }

    /** El mismo criterio que {@link #normalized} aplicado al valor que llega en la request. */
    static String normalize(String value) {
        String noMarks = Normalizer.normalize(value.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noMarks.toLowerCase(Locale.ROOT);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
