package py.com.vetcontrol.agente;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    /**
     * Hasta la 0.3.0 los datos vivian en la carpeta de instalacion, y el instalador la vacia al
     * actualizar: la PC quedaba pidiendo vincularse de nuevo. Comprobado instalando la 0.3.0 sobre
     * la 0.1.0 en una PC real. Al mudarlos hay que heredar lo que quedo, o el arreglo introduce el
     * mismo problema que viene a resolver.
     */
    @Test
    void heredaLaVinculacionDeLaCarpetaQueBorraElInstalador(@TempDir Path base) {
        Path instalacion = base.resolve("MyVetAgente");
        AgenteConfig vieja = AgenteConfig.cargarDesde(instalacion);
        vieja.emparejado("tok-instalacion", 4L, "admin");
        vieja.guardar();

        AgenteConfig nueva = AgenteConfig.cargarDesde(
            base.resolve("MyVet").resolve("Agente"), List.of(instalacion));

        assertThat(nueva.estaEmparejado()).isTrue();
        assertThat(nueva.token()).isEqualTo("tok-instalacion");
        assertThat(nueva.puestoNombre()).isEqualTo("admin");
    }

    /**
     * El agente se llamaba VetControl y ahora se llama MyVet, asi que la carpeta de datos cambio de
     * nombre. Sin heredar la configuracion, cada clinica que ya lo tenia andando veria su PC como
     * "sin vincular" despues de actualizar, y habria que rehacer la vinculacion a mano.
     */
    @Test
    void alCambiarDeNombreHeredaLaVinculacionQueYaExistia(@TempDir Path base) throws Exception {
        Path anterior = base.resolve("VetControlAgente");
        AgenteConfig vieja = AgenteConfig.cargarDesde(anterior);
        vieja.emparejado("tok-heredado", 9L, "Mostrador");
        vieja.guardar();

        AgenteConfig nueva = AgenteConfig.cargarDesde(
            base.resolve("MyVet").resolve("Agente"), List.of(anterior));

        assertThat(nueva.estaEmparejado()).isTrue();
        assertThat(nueva.token()).isEqualTo("tok-heredado");
        assertThat(nueva.puestoNombre()).isEqualTo("Mostrador");
        // Se copia, no se mueve: si algo falla, la instalacion vieja sigue intacta.
        assertThat(Files.exists(anterior.resolve("agente.properties"))).isTrue();
    }

    @Test
    void unaConfiguracionPropiaLeGanaALaHeredada(@TempDir Path base) {
        Path anterior = base.resolve("VetControlAgente");
        AgenteConfig vieja = AgenteConfig.cargarDesde(anterior);
        vieja.emparejado("tok-viejo", 1L, "Viejo");
        vieja.guardar();

        Path actual = base.resolve("MyVetAgente");
        AgenteConfig propia = AgenteConfig.cargarDesde(actual);
        propia.emparejado("tok-actual", 2L, "Actual");
        propia.guardar();

        assertThat(AgenteConfig.cargarDesde(actual).token()).isEqualTo("tok-actual");
    }

    @Test
    void sinCarpetaAnteriorNoHayNadaQueHeredar(@TempDir Path base) {
        AgenteConfig config = AgenteConfig.cargarDesde(base.resolve("MyVetAgente"));

        assertThat(config.estaEmparejado()).isFalse();
    }

    @Test
    void guardarCreaElDirectorioSiNoExiste(@TempDir Path dir) {
        Path anidado = dir.resolve("MyVetAgente");
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
