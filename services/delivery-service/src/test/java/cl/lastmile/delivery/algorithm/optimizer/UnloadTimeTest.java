package cl.lastmile.delivery.algorithm.optimizer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-15: tiempo de descarga = base + minutos por bulto + ajuste del local. */
class UnloadTimeTest {

    @Test
    void sumaBaseBultosYAjuste() {
        UnloadTime unload = new UnloadTime(8, 1.5);
        assertThat(unload.seconds(0, 0)).isEqualTo(8 * 60);
        assertThat(unload.seconds(6, 10)).isEqualTo(27 * 60);
        assertThat(unload.seconds(3, 0)).isEqualTo(750); // 12,5 min
    }

    @Test
    void fijoIgnoraLosBultosPeroNoElAjuste() {
        assertThat(UnloadTime.fixed(10).seconds(40, 0)).isEqualTo(600);
        assertThat(UnloadTime.fixed(10).seconds(40, 5)).isEqualTo(900);
    }

    @Test
    void valoresNegativosNoRestan() {
        assertThat(new UnloadTime(8, 1.5).seconds(-3, -10)).isEqualTo(480);
        assertThatThrownBy(() -> new UnloadTime(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UnloadTime(8, -0.5)).isInstanceOf(IllegalArgumentException.class);
    }
}
