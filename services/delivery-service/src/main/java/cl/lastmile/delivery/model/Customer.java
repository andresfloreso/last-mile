package cl.lastmile.delivery.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Local comercial que recibe la mercadería (botillería, minimarket, bar,
 * restaurant). La tabla se llama {@code customers} por historia del esquema.
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    public enum BusinessType { BOTILLERIA, MINIMARKET, BAR, RESTAURANT, OTRO }

    /**
     * Origen de las coordenadas (HU-09): PROVIDED las entregó el usuario
     * (planilla o pin en el mapa); GEOCODED las encontró Nominatim con
     * precisión de número de calle; REVIEW son aproximadas o no se
     * encontraron, y el despachador debe revisarlas.
     */
    public enum GeocodeStatus { PROVIDED, GEOCODED, REVIEW }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String fullName;

    private String phone;

    @Column(nullable = false)
    private String address;

    private String commune; // comuna (Chile)

    private Double latitude;
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    @Builder.Default
    private GeocodeStatus geocodeStatus = GeocodeStatus.PROVIDED;

    /** Identificador externo usado en las planillas/importaciones (RUT, código de cliente, etc). */
    private String externalCode;

    /** Tipo de local (HU-25). Opcional. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BusinessType businessType;

    /*
     * Modulación (HU-25): horario de recepción que el local DECLARA. Puede
     * diferir de la ventana real que aprende TimeWindowLearningService.
     * Primer tramo obligatorio si hay modulación; segundo tramo opcional.
     */
    private LocalTime declaredWindowStart;
    private LocalTime declaredWindowEnd;
    @Column(name = "declared_window2_start")
    private LocalTime declaredWindow2Start;
    @Column(name = "declared_window2_end")
    private LocalTime declaredWindow2End;

    /** Reemplaza la modulación completa (ya validada con {@link DeclaredWindow#validate}). */
    public void applyDeclaredWindow(DeclaredWindow window) {
        this.declaredWindowStart = window.start();
        this.declaredWindowEnd = window.end();
        this.declaredWindow2Start = window.start2();
        this.declaredWindow2End = window.end2();
    }

    /** Fija coordenadas entregadas por el usuario: dejan de estar "por revisar". */
    public void relocate(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.geocodeStatus = GeocodeStatus.PROVIDED;
    }

    public boolean needsGeocodeReview() {
        return geocodeStatus == GeocodeStatus.REVIEW;
    }

    public DeclaredWindow declaredWindow() {
        return new DeclaredWindow(declaredWindowStart, declaredWindowEnd, declaredWindow2Start, declaredWindow2End);
    }
}
