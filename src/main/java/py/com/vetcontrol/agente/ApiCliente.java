package py.com.vetcontrol.agente;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Cliente HTTP contra la API del agente. Todas las conexiones son SALIENTES: el agente nunca abre
 * un puerto ni recibe conexiones entrantes.
 */
public final class ApiCliente {

    /**
     * El long-poll del servidor dura 25 s. El timeout del cliente tiene que ser holgadamente mayor,
     * o cortariamos la espera justo antes de que el servidor conteste.
     */
    private static final Duration TIMEOUT_LARGO = Duration.ofSeconds(45);

    private static final Duration TIMEOUT_NORMAL = Duration.ofSeconds(20);

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final String baseUrl;
    private String token;

    public ApiCliente(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /** Canjea el codigo de un solo uso por el token permanente del puesto. */
    public Emparejamiento emparejar(String codigo, String host, String version) {
        ObjectNode cuerpo = json.createObjectNode()
            .put("codigo", codigo)
            .put("host", host)
            .put("version", version);
        JsonNode respuesta = pedirJson(
            HttpRequest.newBuilder(uri("/api/impresion/agente/emparejar"))
                .timeout(TIMEOUT_NORMAL)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString())));
        return new Emparejamiento(
            respuesta.path("puestoId").asLong(),
            respuesta.path("nombre").asText(),
            respuesta.path("token").asText());
    }

    public void usarToken(String token) {
        this.token = token;
    }

    public void reportarImpresoras(List<String> impresoras, String host, String version) {
        ObjectNode cuerpo = json.createObjectNode();
        cuerpo.putPOJO("impresoras", impresoras);
        cuerpo.put("host", host);
        cuerpo.put("version", version);
        enviar(autenticado(HttpRequest.newBuilder(uri("/api/impresion/agente/impresoras"))
            .timeout(TIMEOUT_NORMAL)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString()))),
            HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Long-poll. El servidor retiene la peticion hasta 25 s y contesta apenas aparece un trabajo.
     *
     * @return el trabajo, o {@code null} si vencio la ventana sin novedades (204).
     */
    public Trabajo siguienteTrabajo() {
        HttpResponse<String> respuesta = enviar(
            autenticado(HttpRequest.newBuilder(uri("/api/impresion/agente/trabajos"))
                .timeout(TIMEOUT_LARGO)
                .GET()),
            HttpResponse.BodyHandlers.ofString());

        if (respuesta.statusCode() == 204) return null;
        JsonNode nodo = leer(respuesta.body());
        return new Trabajo(
            nodo.path("id").asLong(),
            nodo.path("impresora").asText(),
            nodo.path("formato").asText(),
            nodo.hasNonNull("anchoMm") ? nodo.get("anchoMm").asInt() : null);
    }

    public byte[] pdf(long trabajoId) {
        return enviar(
            autenticado(HttpRequest.newBuilder(uri("/api/impresion/agente/trabajos/" + trabajoId + "/pdf"))
                .timeout(TIMEOUT_NORMAL)
                .GET()),
            HttpResponse.BodyHandlers.ofByteArray()).body();
    }

    public void reportarResultado(long trabajoId, String estado, String error) {
        ObjectNode cuerpo = json.createObjectNode().put("estado", estado);
        if (error != null) cuerpo.put("error", error);
        enviar(autenticado(
            HttpRequest.newBuilder(uri("/api/impresion/agente/trabajos/" + trabajoId + "/resultado"))
                .timeout(TIMEOUT_NORMAL)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString()))),
            HttpResponse.BodyHandlers.ofString());
    }

    // ------------------------------------------------------------- interno

    private URI uri(String ruta) {
        return URI.create(baseUrl + ruta);
    }

    private HttpRequest.Builder autenticado(HttpRequest.Builder builder) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private JsonNode pedirJson(HttpRequest.Builder builder) {
        return leer(enviar(builder, HttpResponse.BodyHandlers.ofString()).body());
    }

    private JsonNode leer(String cuerpo) {
        try {
            return json.readTree(cuerpo);
        } catch (IOException ex) {
            throw new ApiException(0, "Respuesta ilegible de la API: " + ex.getMessage());
        }
    }

    private <T> HttpResponse<T> enviar(HttpRequest.Builder builder, HttpResponse.BodyHandler<T> handler) {
        HttpResponse<T> respuesta;
        try {
            respuesta = http.send(builder.build(), handler);
        } catch (IOException ex) {
            throw new ApiException(0, "No se pudo contactar la API: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "Interrumpido");
        }
        if (respuesta.statusCode() >= 400) {
            throw new ApiException(respuesta.statusCode(), mensajeDeError(respuesta));
        }
        return respuesta;
    }

    /** La API devuelve los errores como {@code {"message": "..."}}; se usa ese texto si esta. */
    private <T> String mensajeDeError(HttpResponse<T> respuesta) {
        Object cuerpo = respuesta.body();
        String texto = cuerpo instanceof byte[] bytes
            ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
            : String.valueOf(cuerpo);
        try {
            JsonNode nodo = json.readTree(texto);
            if (nodo.hasNonNull("message")) {
                return "HTTP " + respuesta.statusCode() + ": " + nodo.get("message").asText();
            }
        } catch (IOException ignorado) {
            // El cuerpo no era JSON (p. ej. una pagina de error de nginx): se usa el status pelado.
        }
        return "HTTP " + respuesta.statusCode();
    }

    public record Emparejamiento(long puestoId, String nombre, String token) {}
}
