package cl.lastmile.auth.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void el500EsGenericoYNoIncluyeElMensajeOriginal() {
        var response = new GlobalExceptionHandler()
                .handleGeneric(new RuntimeException("could not execute statement [insert into users ...]"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        String message = (String) response.getBody().get("message");
        assertThat(message).startsWith("Error interno del servidor").doesNotContain("insert", "users");
        assertThat(message).endsWith((String) response.getBody().get("errorId"));
    }
}
