package py.com.vetcontrol.agente;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * Vinculacion desde el navegador, al estilo "device flow" (como vincular una Smart TV).
 *
 * <p>Es lo que permite que el instalador sea UNO SOLO para todas las clinicas. El agente inventa un
 * secreto, se anuncia con el y abre VetControl en el navegador del usuario -- que ya tiene su sesion
 * abierta -- para que elija a que puesto pertenece esta PC. El usuario no tipea ni copia nada.
 *
 * <p>El secreto no se manda por la URL de ninguna peticion: viaja en el fragmento
 * ({@code /#/vincular-agente?s=...}), que el navegador nunca envia al servidor, y de ahi la app lo
 * pone en el cuerpo de un POST. Asi no queda escrito en el log de accesos de nginx.
 */
public final class VinculacionNavegador {

    /** 256 bits: es la unica credencial del flujo, tiene que ser imposible de adivinar. */
    private static final int BYTES_SECRETO = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Cada cuanto se pregunta si ya eligieron el puesto. */
    static final Duration INTERVALO = Duration.ofSeconds(2);

    /**
     * El anuncio vence a los 10 minutos en el servidor, pero la persona puede tardar mas (buscar la
     * contraseña, entrar desde otra PC). Reanunciarse renueva la vigencia sin cambiar el secreto,
     * asi que el link que ya se abrio en el navegador sigue sirviendo.
     */
    static final Duration RENOVAR_CADA = Duration.ofMinutes(4);

    /** Tope para no dejar una ventana abierta para siempre en un mostrador. */
    static final Duration ESPERA_MAXIMA = Duration.ofMinutes(30);

    private VinculacionNavegador() {}

    public static String nuevoSecreto() {
        byte[] bytes = new byte[BYTES_SECRETO];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** El link que se abre en el navegador. */
    static String urlVinculacion(String baseUrl, String secreto) {
        String limpia = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return limpia + "/#/vincular-agente?s=" + secreto;
    }

    /**
     * Abre el navegador por defecto del usuario.
     *
     * <p>{@link Desktop} es el camino normal; {@code rundll32} es el plan B porque en algunas
     * sesiones de Windows (perfiles restringidos, arranques tempranos) el Desktop no esta
     * disponible y sin navegador no hay forma de completar la vinculacion.
     *
     * @return false si no se pudo abrir por ningun camino.
     */
    static boolean abrir(String url, Bitacora bitacora) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return true;
            }
        } catch (IOException | RuntimeException ex) {
            bitacora.error("No se pudo abrir el navegador con Desktop: " + ex.getMessage());
        }
        try {
            new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            return true;
        } catch (IOException ex) {
            bitacora.error("Tampoco se pudo abrir el navegador con rundll32: " + ex.getMessage());
            return false;
        }
    }
}
