package cl.lastmile.delivery.model;

import cl.lastmile.delivery.exception.BadRequestException;

import java.time.LocalTime;

/**
 * Modulación de un local (HU-25): uno o dos tramos horarios declarados.
 * Todos los valores nulos significan "sin modulación".
 */
public record DeclaredWindow(LocalTime start, LocalTime end, LocalTime start2, LocalTime end2) {

    public static final DeclaredWindow NONE = new DeclaredWindow(null, null, null, null);

    public boolean isEmpty() {
        return start == null && end == null && start2 == null && end2 == null;
    }

    /** ¿La hora cae dentro de algún tramo declarado? (inicio incluido, término excluido). */
    public boolean contains(LocalTime time) {
        return within(time, start, end) || within(time, start2, end2);
    }

    private static boolean within(LocalTime time, LocalTime from, LocalTime to) {
        return from != null && to != null && !time.isBefore(from) && time.isBefore(to);
    }

    /**
     * Valida las mismas reglas que las constraints de la migración V2, con
     * mensajes para el usuario. Devuelve la misma modulación si es válida.
     */
    public DeclaredWindow validate() {
        if ((start == null) != (end == null)) {
            throw new BadRequestException("La modulación necesita hora de inicio y de término");
        }
        if (start != null && !start.isBefore(end)) {
            throw new BadRequestException("Modulación inválida: el inicio (" + start + ") debe ser anterior al término (" + end + ")");
        }
        if ((start2 == null) != (end2 == null)) {
            throw new BadRequestException("El segundo tramo de la modulación necesita hora de inicio y de término");
        }
        if (start2 != null) {
            if (start == null) {
                throw new BadRequestException("El segundo tramo de la modulación requiere el primero");
            }
            if (!start2.isBefore(end2)) {
                throw new BadRequestException("Segundo tramo inválido: el inicio (" + start2 + ") debe ser anterior al término (" + end2 + ")");
            }
            if (start2.isBefore(end)) {
                throw new BadRequestException("El segundo tramo debe empezar después del término del primero (" + end + ")");
            }
        }
        return this;
    }
}
