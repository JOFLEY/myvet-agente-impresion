package py.com.vetcontrol.agente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * El archivo `.vcagente` es la puerta de entrada del usuario: si el parseo falla tiene que decir
 * algo que una persona del mostrador entienda, no un stacktrace.
 */
class VinculacionTest {

    private static final String VALIDO = """
        {
          "vetcontrol": "vinculacion-agente",
          "version": 1,
          "url": "https://myvet.serfley.com",
          "puesto": "Mostrador 1",
          "codigo": "ABCD2345",
          "expira": "2026-09-04T15:10:00"
        }
        """;

    @Test
    void seLeenLosTresDatosQueImportan() {
        Vinculacion vinculacion = Vinculacion.parsear(VALIDO);

        assertThat(vinculacion.url()).isEqualTo("https://myvet.serfley.com");
        assertThat(vinculacion.puesto()).isEqualTo("Mostrador 1");
        assertThat(vinculacion.codigo()).isEqualTo("ABCD2345");
    }

    @Test
    void elArchivoLlevaUnCodigoYNoElTokenPermanente() {
        // Es la razon por la que un archivo olvidado en Descargas deja de servir a los 10 minutos.
        assertThat(VALIDO).doesNotContain("token");
    }

    @Test
    void unArchivoDeOtraCosaSeRechazaConUnMensajeEntendible() {
        assertThatThrownBy(() -> Vinculacion.parsear("{\"algo\":\"otra cosa\"}"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no es de vinculacion");
    }

    @Test
    void unArchivoQueNoEsJsonNoTiraStacktrace() {
        assertThatThrownBy(() -> Vinculacion.parsear("esto no es json"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Descarga uno nuevo");
    }

    @Test
    void unArchivoIncompletoSeRechaza() {
        assertThatThrownBy(() -> Vinculacion.parsear(
            "{\"vetcontrol\":\"vinculacion-agente\",\"url\":\"https://x\"}"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("incompleto");
    }

    @Test
    void seLeeDesdeElDisco(@TempDir Path dir) throws Exception {
        Path archivo = dir.resolve("vincular-Mostrador-1.vcagente");
        Files.writeString(archivo, VALIDO);

        assertThat(Vinculacion.leer(archivo).codigo()).isEqualTo("ABCD2345");
    }

    @Test
    void unArchivoInexistenteDaMensajeYNoExcepcionDeIO(@TempDir Path dir) {
        assertThatThrownBy(() -> Vinculacion.leer(dir.resolve("no-existe.vcagente")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No se pudo leer");
    }
}
