package py.com.vetcontrol.agente;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgenteConfigTest {

    @Test
    void unaConfiguracionNuevaNoEstaEmparejadaYUsaLaUrlDeProduccion(@TempDir Path dir) {
        AgenteConfig config = AgenteConfig.cargarDesde(dir);

        assertThat(config.estaEmparejado()).isFalse();
        assertThat(config.token()).isNull();
        assertThat(config.baseUrl()).isEqualTo(AgenteConfig.URL_POR_DEFECTO);
    }

    @Test
    void elEmparejamientoSobreviveAlReinicio(@TempDir Path dir) {
        AgenteConfig original = AgenteConfig.cargarDesde(dir);
        original.baseUrl("https://myvet.serfley.com");
        original.emparejado("tok-123", 7L, "Mostrador 1");
        original.guardar();

        // Segunda "ejecucion" del agente: tiene que arrancar ya vinculado, sin pedir codigo.
        AgenteConfig recargada = AgenteConfig.cargarDesde(dir);

        assertThat(recargada.estaEmparejado()).isTrue();
        assertThat(recargada.token()).isEqualTo("tok-123");
        assertThat(recargada.puestoNombre()).isEqualTo("Mostrador 1");
    }

    @Test
    void laUrlSeNormalizaSinBarraFinal(@TempDir Path dir) {
        // Sin esto las rutas quedarian con doble barra: https://host//api/...
        AgenteConfig config = AgenteConfig.cargarDesde(dir);
        config.baseUrl("https://myvet.serfley.com/");

        assertThat(config.baseUrl()).isEqualTo("https://myvet.serfley.com");
    }

    @Test
    void guardarCreaElDirectorioSiNoExiste(@TempDir Path dir) {
        Path anidado = dir.resolve("VetControlAgente");
        AgenteConfig config = AgenteConfig.cargarDesde(anidado);
        config.emparejado("tok", 1L, "P");

        config.guardar();

        assertThat(Files.exists(anidado.resolve("agente.properties"))).isTrue();
    }

    @Test
    void elDirectorioDeDatosEsPorUsuarioNoDelSistema() {
        // El agente corre como el usuario logueado, no como servicio: las impresoras de red
        // mapeadas por usuario no son visibles desde la Sesion 0.
        assertThat(AgenteConfig.directorioDatos().toString())
            .doesNotContain("Program Files")
            .doesNotContain("System32");
    }
}
