package cl.lastmile.delivery.service;

import cl.lastmile.delivery.exception.BadRequestException;
import cl.lastmile.delivery.model.Shipment.ShipmentStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-10: construcción y validación de los filtros del listado de envíos. */
class ShipmentFilterTest {

    private static final LocalDate D = LocalDate.of(2026, 12, 15);

    @Test
    void dateEsUnAtajoParaUnSoloDia() {
        ShipmentFilter f = ShipmentFilter.of(D, null, null, null, null, null);
        assertThat(f.from()).isEqualTo(D);
        assertThat(f.to()).isEqualTo(D);
    }

    @Test
    void sinParametrosNoFiltraNada() {
        ShipmentFilter f = ShipmentFilter.of(null, null, null, null, "  ", "");
        assertThat(f.from()).isNull();
        assertThat(f.to()).isNull();
        assertThat(f.statuses()).isEmpty();
        assertThat(f.commune()).isNull();
        assertThat(f.query()).isNull();
    }

    @Test
    void aceptaEstadosRepetidosOSeparadosPorComaEnCualquierCaja() {
        ShipmentFilter f = ShipmentFilter.of(null, null, null, List.of("pending,ROUTED", " delivered "), null, null);
        assertThat(f.statuses()).containsExactlyInAnyOrder(
                ShipmentStatus.PENDING, ShipmentStatus.ROUTED, ShipmentStatus.DELIVERED);
    }

    @Test
    void unEstadoDesconocidoEsUn400ConLosValoresPermitidos() {
        assertThatThrownBy(() -> ShipmentFilter.of(null, null, null, List.of("PERDIDO"), null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageStartingWith("status inválido: PERDIDO")
                .hasMessageContaining("PENDING, ROUTED");
    }

    @Test
    void noSePuedeCombinarDateConUnRango() {
        assertThatThrownBy(() -> ShipmentFilter.of(D, D, null, null, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no ambos");
    }

    @Test
    void unRangoInvertidoEsUn400() {
        assertThatThrownBy(() -> ShipmentFilter.of(null, D.plusDays(1), D, null, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("es posterior a");
    }

    @Test
    void unRangoAbiertoEsValido() {
        assertThat(ShipmentFilter.of(null, D, null, null, null, null).to()).isNull();
        assertThat(ShipmentFilter.of(null, null, D, null, null, null).from()).isNull();
    }

    @Test
    void normalizaSinTildesNiMayusculas() {
        assertThat(ShipmentFilter.normalize("  Ñuñoa ")).isEqualTo("nunoa");
        assertThat(ShipmentFilter.normalize("BOTILLERÍA El Güemes")).isEqualTo("botilleria el guemes");
    }
}
