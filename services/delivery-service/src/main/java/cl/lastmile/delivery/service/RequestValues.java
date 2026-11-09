package cl.lastmile.delivery.service;

import cl.lastmile.delivery.exception.BadRequestException;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Convierte valores que llegan como texto (ids y enums). Si no son válidos,
 * lanza {@link BadRequestException} con un mensaje claro para el cliente, en
 * vez del IllegalArgumentException de {@code UUID.fromString} / {@code valueOf},
 * cuyo mensaje expone nombres de clases internas.
 */
public final class RequestValues {

    private RequestValues() {
    }

    public static UUID uuid(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " es obligatorio");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(field + " no es un identificador válido: " + value);
        }
    }

    public static <E extends Enum<E>> E enumValue(Class<E> type, String value, String field) {
        if (value != null) {
            try {
                return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // se informa abajo con los valores permitidos
            }
        }
        String allowed = Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "));
        throw new BadRequestException(field + " inválido: " + value + ". Valores permitidos: " + allowed);
    }
}
