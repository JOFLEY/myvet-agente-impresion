package py.com.vetcontrol.agente;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArranqueAutomaticoTest {

    @Test
    void seConfiguraEnElUsuarioYNoEnLaMaquina() {
        // HKCU y no HKLM: no necesita permisos de administrador (que quien atiende el mostrador no
        // tiene) y, sobre todo, arranca en la sesion del usuario. Un servicio en la Sesion 0 no ve
        // las impresoras de red mapeadas por usuario.
        assertThat(ArranqueAutomatico.CLAVE).startsWith("HKCU\\");
        assertThat(ArranqueAutomatico.CLAVE).doesNotContain("HKLM");
        assertThat(ArranqueAutomatico.CLAVE).endsWith("CurrentVersion\\Run");
    }

    @Test
    void consultarElEstadoNoExplotaEnNingunaPlataforma() {
        // Se llama al abrir la ventana: si tirara una excepcion, no habria forma de vincular.
        assertThat(ArranqueAutomatico.activado()).isIn(true, false);
    }

    @Test
    void sinRutaDeEjecutableNoSeIntentaActivar() {
        // Corriendo desde el JAR (o los tests) no existe jpackage.app-path, y activar el arranque
        // apuntaria a cualquier cosa. Tiene que devolver false, no romper.
        if (ArranqueAutomatico.ejecutable() == null) {
            assertThat(ArranqueAutomatico.activar()).isFalse();
        }
    }
}
