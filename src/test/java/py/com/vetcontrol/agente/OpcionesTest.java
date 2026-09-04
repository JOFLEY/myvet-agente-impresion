package py.com.vetcontrol.agente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OpcionesTest {

    @Test
    void sinArgumentosElAgenteSoloEscucha() {
        Opciones opciones = Opciones.parse(new String[] {});

        assertThat(opciones.codigo()).isNull();
        assertThat(opciones.dryRun()).isFalse();
        assertThat(opciones.ayuda()).isFalse();
        assertThat(opciones.listarImpresoras()).isFalse();
    }

    @Test
    void seParseanLasOpcionesConValor() {
        Opciones opciones = Opciones.parse(new String[] {"--url", "http://127.0.0.1:8585", "--emparejar", "ABCD2345"});

        assertThat(opciones.url()).isEqualTo("http://127.0.0.1:8585");
        assertThat(opciones.codigo()).isEqualTo("ABCD2345");
    }

    @Test
    void seParseanLosFlags() {
        Opciones opciones = Opciones.parse(new String[] {"--dry-run", "--listar-impresoras"});

        assertThat(opciones.dryRun()).isTrue();
        assertThat(opciones.listarImpresoras()).isTrue();
    }

    @Test
    void unaOpcionSinValorAvisaEnVezDeSeguirEnSilencio() {
        assertThatThrownBy(() -> Opciones.parse(new String[] {"--emparejar"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("--emparejar");
    }

    @Test
    void unaOpcionDesconocidaNoSeIgnora() {
        // Ignorarla en silencio haria que un typo como --dryrun arranque imprimiendo de verdad.
        assertThatThrownBy(() -> Opciones.parse(new String[] {"--dryrun"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("--dryrun");
    }
}
