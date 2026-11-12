package cl.lastmile.delivery.service;

import cl.lastmile.delivery.exception.BadRequestException;
import cl.lastmile.delivery.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-15: reglas del armado manual que no necesitan base de datos. */
class RouteEditServiceTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final UUID d = UUID.randomUUID();

    @Test
    void lasFijadasQuedanEnSuLugarYLasDemasEnElOrdenDelOptimizador() {
        // Orden actual a, b, c, d con b y d fijadas; el optimizador ordena las libres como c, a.
        List<UUID> merged = RouteEditService.mergeIntoFreeSlots(List.of(a, b, c, d), Set.of(b, d), List.of(c, a));
        assertThat(merged).containsExactly(c, b, a, d);
    }

    @Test
    void sinFijadasEsElOrdenDelOptimizador() {
        assertThat(RouteEditService.mergeIntoFreeSlots(List.of(a, b, c), Set.of(), List.of(c, b, a)))
                .containsExactly(c, b, a);
    }

    @Test
    void elOrdenDebeTraerExactamenteLasParadasGuardadas() {
        Set<UUID> saved = Set.of(a, b, c);
        assertThatCode(() -> RouteEditService.checkSameStops(saved, List.of(c, a, b))).doesNotThrowAnyException();
        assertThatThrownBy(() -> RouteEditService.checkSameStops(saved, List.of(a, b))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> RouteEditService.checkSameStops(saved, List.of(a, b, c, d))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> RouteEditService.checkSameStops(saved, List.of(a, a, b))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> RouteEditService.checkSameStops(saved, List.of())).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> RouteEditService.checkSameStops(Set.of(), List.of(a))).isInstanceOf(ConflictException.class);
    }
}
