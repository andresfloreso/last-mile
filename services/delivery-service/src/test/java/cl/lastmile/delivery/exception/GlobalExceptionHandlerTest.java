package cl.lastmile.delivery.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void el500EsGenericoYNoIncluyeElMensajeOriginal() {
        var response = handler.handleGeneric(
                new IllegalStateException("could not execute statement [SELECT * FROM shipments] password=x"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        String message = (String) response.getBody().get("message");
        String errorId = (String) response.getBody().get("errorId");
        assertThat(message).isEqualTo(GlobalExceptionHandler.GENERIC_500 + errorId);
        assertThat(message).doesNotContain("SELECT", "shipments", "password", "IllegalStateException");
    }

    @Test
    void laViolacionDeIntegridadResponde409SinElSql() {
        var cause = new SQLException(
                "ERROR: duplicate key value violates unique constraint \"uk_shipments_tracking_code\"");
        var response = handler.handleIntegrity(
                new DataIntegrityViolationException("insert into shipments (...) values (...)", cause));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().get("message")).isEqualTo("Ya existe un envío con ese trackingCode");
    }

    @Test
    void otraViolacionDeIntegridadUsaUnMensajeGenerico() {
        var response = handler.handleIntegrity(new DataIntegrityViolationException(
                "x", new SQLException("violates foreign key constraint \"fk_shipments_customer\"")));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().get("message")).isEqualTo("La operación entra en conflicto con datos existentes");
    }
}
