package py.com.vetcontrol.agente;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BackoffTest {

    @Test
    void laEsperaCreceHastaUnTope() {
        Backoff backoff = new Backoff();

        assertThat(backoff.siguienteMs()).isEqualTo(5_000L);
        assertThat(backoff.siguienteMs()).isEqualTo(10_000L);
        assertThat(backoff.siguienteMs()).isEqualTo(20_000L);
        assertThat(backoff.siguienteMs()).isEqualTo(40_000L);
        // Topeado: la PC puede estar toda la noche sin internet y no debe dormirse mas que esto,
        // o tardaria en volver cuando la conexion regresa.
        assertThat(backoff.siguienteMs()).isEqualTo(Backoff.MAXIMO_MS);
        assertThat(backoff.siguienteMs()).isEqualTo(Backoff.MAXIMO_MS);
    }

    @Test
    void alRecuperarLaConexionVuelveAEmpezarDeCero() {
        Backoff backoff = new Backoff();
        backoff.siguienteMs();
        backoff.siguienteMs();

        backoff.reiniciar();

        assertThat(backoff.siguienteMs()).isEqualTo(Backoff.INICIAL_MS);
    }
}
