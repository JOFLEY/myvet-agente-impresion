package py.com.vetcontrol.agente;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VinculacionNavegadorTest {

    @Test
    void elSecretoEsLargoImpredecibleYSeguroEnUnaUrl() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String secreto = VinculacionNavegador.nuevoSecreto();
            // 32 bytes en base64url sin padding.
            assertThat(secreto).hasSize(43);
            // Es la unica credencial del flujo: no puede repetirse jamas.
            assertThat(vistos.add(secreto)).isTrue();
            // Sin caracteres que haya que escapar al meterlo en la URL.
            assertThat(secreto).matches("[A-Za-z0-9_-]+");
        }
    }

    @Test
    void elSecretoViajaEnElFragmentoParaNoQuedarEnElLogDeNginx() {
        String url = VinculacionNavegador.urlVinculacion("https://myvet.serfley.com", "abc123");

        assertThat(url).isEqualTo("https://myvet.serfley.com/#/vincular-agente?s=abc123");
        // Todo lo que sigue al '#' se lo queda el navegador y nunca llega al servidor.
        assertThat(url.substring(url.indexOf('#'))).contains("abc123");
    }

    @Test
    void unaUrlConBarraFinalNoGeneraDobleBarra() {
        assertThat(VinculacionNavegador.urlVinculacion("https://myvet.serfley.com/", "x"))
            .isEqualTo("https://myvet.serfley.com/#/vincular-agente?s=x");
    }

    /** El agente aguanta mas que la vigencia del servidor porque se reanuncia antes de vencer. */
    @Test
    void seReanunciaAntesDeQueVenzaLaSolicitud() {
        assertThat(VinculacionNavegador.RENOVAR_CADA).isLessThan(java.time.Duration.ofMinutes(10));
        assertThat(VinculacionNavegador.ESPERA_MAXIMA)
            .isGreaterThan(VinculacionNavegador.RENOVAR_CADA);
    }
}
