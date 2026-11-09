package cl.lastmile.delivery.exception;

/** El recurso choca con uno existente (409). El mensaje se muestra tal cual al cliente. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
