package cl.lastmile.delivery.exception;

/** Datos de entrada inválidos (400). El mensaje se muestra tal cual al cliente. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
