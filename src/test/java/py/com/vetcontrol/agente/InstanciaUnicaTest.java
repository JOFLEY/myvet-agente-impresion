package py.com.vetcontrol.agente;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstanciaUnicaTest {

    @Test
    void elPrimeroTomaElCandado(@TempDir Path dir) {
        try (InstanciaUnica primero = InstanciaUnica.tomar(dir.resolve("agente.lock"))) {
            assertThat(primero).isNotNull();
        }
    }

    @Test
    void elSegundoNoArranca(@TempDir Path dir) {
        Path archivo = dir.resolve("agente.lock");
        try (InstanciaUnica primero = InstanciaUnica.tomar(archivo)) {
            assertThat(primero).isNotNull();

            // Es lo que pasaba al abrir el acceso directo con el agente ya corriendo: quedaban dos
            // escuchando la misma cola y un ticket podia salir dos veces.
            assertThat(InstanciaUnica.tomar(archivo)).isNull();
        }
    }

    @Test
    void alCerrarQuedaLibreParaElSiguiente(@TempDir Path dir) {
        Path archivo = dir.resolve("agente.lock");
        InstanciaUnica primero = InstanciaUnica.tomar(archivo);
        assertThat(primero).isNotNull();
        primero.close();

        // Cerrar el agente y volver a abrirlo tiene que funcionar siempre: si el candado quedara
        // tomado, la PC se quedaria sin imprimir hasta reiniciar Windows.
        try (InstanciaUnica segundo = InstanciaUnica.tomar(archivo)) {
            assertThat(segundo).isNotNull();
        }
    }

    @Test
    void creaLaCarpetaDeDatosSiTodaviaNoExiste(@TempDir Path dir) {
        Path archivo = dir.resolve("sub").resolve("agente.lock");

        try (InstanciaUnica candado = InstanciaUnica.tomar(archivo)) {
            assertThat(candado).isNotNull();
            assertThat(Files.exists(archivo)).isTrue();
        }
    }
}
