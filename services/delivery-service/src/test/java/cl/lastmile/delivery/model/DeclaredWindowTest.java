package cl.lastmile.delivery.model;

import cl.lastmile.delivery.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeclaredWindowTest {

    private static LocalTime h(int hour) {
        return LocalTime.of(hour, 0);
    }

    @Test
    void sinModulacionEsValidaYVacia() {
        assertThat(DeclaredWindow.NONE.validate().isEmpty()).isTrue();
        assertThat(DeclaredWindow.NONE.contains(h(10))).isFalse();
    }

    @Test
    void containsIncluyeElInicioYExcluyeElTermino() {
        var w = new DeclaredWindow(h(9), h(12), h(16), h(18)).validate();
        assertThat(w.contains(h(9))).isTrue();
        assertThat(w.contains(LocalTime.of(11, 59))).isTrue();
        assertThat(w.contains(h(12))).isFalse();
        assertThat(w.contains(h(14))).isFalse();
        assertThat(w.contains(h(17))).isTrue();
        assertThat(w.contains(h(18))).isFalse();
    }

    @Test
    void rechazaTramosIncompletosOInvertidos() {
        assertThatThrownBy(() -> new DeclaredWindow(h(9), null, null, null).validate())
                .isInstanceOf(BadRequestException.class).hasMessageContaining("inicio y de término");
        assertThatThrownBy(() -> new DeclaredWindow(h(12), h(12), null, null).validate())
                .isInstanceOf(BadRequestException.class).hasMessageStartingWith("Modulación inválida");
        assertThatThrownBy(() -> new DeclaredWindow(h(9), h(12), h(16), null).validate())
                .isInstanceOf(BadRequestException.class).hasMessageContaining("segundo tramo");
    }

    @Test
    void elSegundoTramoRequiereElPrimeroYNoPuedeSolaparse() {
        assertThatThrownBy(() -> new DeclaredWindow(null, null, h(16), h(18)).validate())
                .isInstanceOf(BadRequestException.class).hasMessageContaining("requiere el primero");
        assertThatThrownBy(() -> new DeclaredWindow(h(9), h(12), h(11), h(13)).validate())
                .isInstanceOf(BadRequestException.class).hasMessageContaining("después del término");
        assertThat(new DeclaredWindow(h(9), h(12), h(12), h(14)).validate().contains(h(13))).isTrue();
    }
}
