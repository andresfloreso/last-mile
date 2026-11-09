package cl.lastmile.delivery.service;

import cl.lastmile.delivery.exception.BadRequestException;
import cl.lastmile.delivery.model.Shipment;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestValuesTest {

    @Test
    void uuidValido() {
        UUID id = UUID.randomUUID();
        assertThat(RequestValues.uuid(" " + id + " ", "shipmentId")).isEqualTo(id);
    }

    @Test
    void uuidInvalidoOVacio() {
        assertThatThrownBy(() -> RequestValues.uuid("abc", "shipmentId"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("shipmentId no es un identificador válido: abc");
        assertThatThrownBy(() -> RequestValues.uuid(" ", "customerId"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("customerId es obligatorio");
    }

    @Test
    void enumAceptaMinusculas() {
        assertThat(RequestValues.enumValue(Shipment.StopType.class, "pickup", "stopType"))
                .isEqualTo(Shipment.StopType.PICKUP);
    }

    @Test
    void enumInvalidoListaLosValoresPermitidosSinNombresDeClases() {
        assertThatThrownBy(() -> RequestValues.enumValue(Shipment.OrderPreference.class, "PRIMERO", "orderPreference"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("orderPreference inválido: PRIMERO. Valores permitidos: FIRST, AUTO, LAST")
                .hasMessageNotContaining("cl.lastmile");
        assertThatThrownBy(() -> RequestValues.enumValue(Shipment.StopType.class, null, "stopType"))
                .isInstanceOf(BadRequestException.class);
    }
}
