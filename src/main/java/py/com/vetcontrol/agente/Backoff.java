package py.com.vetcontrol.agente;

/**
 * Espera creciente entre reintentos cuando la API no responde (internet caido, VPS reiniciando).
 *
 * <p>Importa que tenga tope: la PC del mostrador puede quedarse sin conexion toda la noche, y el
 * agente no debe ni martillar la API cada segundo ni dormirse tanto que tarde en volver cuando la
 * conexion regresa.
 */
public final class Backoff {

    static final long INICIAL_MS = 5_000L;
    static final long MAXIMO_MS = 60_000L;

    private long actual;

    public Backoff() {
        reiniciar();
    }

    public void reiniciar() {
        actual = INICIAL_MS;
    }

    /** @return cuanto esperar ahora, y duplica la espera para la proxima (topeada). */
    public long siguienteMs() {
        long espera = actual;
        actual = Math.min(MAXIMO_MS, actual * 2);
        return espera;
    }
}
