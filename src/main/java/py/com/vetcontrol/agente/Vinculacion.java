package py.com.vetcontrol.agente;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * El archivo `.vcagente` que se descarga desde VetControl y se arrastra sobre la ventana del agente.
 *
 * <p>Existe para que nadie tenga que tipear un codigo ni abrir una consola. Lleva un codigo de
 * emparejamiento de un solo uso con 10 minutos de vida, no el token permanente, asi que un archivo
 * olvidado en Descargas deja de servir solo.
 */
public record Vinculacion(String url, String puesto, String codigo) {

    public static final String EXTENSION = ".vcagente";

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Lee y valida el archivo. Lanza {@link IllegalArgumentException} con un mensaje mostrable. */
    public static Vinculacion leer(Path archivo) {
        String contenido;
        try {
            contenido = Files.readString(archivo);
        } catch (IOException ex) {
            throw new IllegalArgumentException("No se pudo leer el archivo: " + ex.getMessage());
        }
        return parsear(contenido);
    }

    static Vinculacion parsear(String contenido) {
        JsonNode nodo;
        try {
            nodo = JSON.readTree(contenido);
        } catch (IOException ex) {
            throw new IllegalArgumentException(
                "El archivo no tiene el formato esperado. Descarga uno nuevo desde MyVet.");
        }
        if (!"vinculacion-agente".equals(nodo.path("vetcontrol").asText())) {
            throw new IllegalArgumentException(
                "Ese archivo no es de vinculacion de MyVet.");
        }
        String url = nodo.path("url").asText("").trim();
        String codigo = nodo.path("codigo").asText("").trim();
        if (url.isEmpty() || codigo.isEmpty()) {
            throw new IllegalArgumentException(
                "El archivo esta incompleto. Descarga uno nuevo desde MyVet.");
        }
        return new Vinculacion(url, nodo.path("puesto").asText("").trim(), codigo);
    }

    /**
     * Busca un `.vcagente` al lado del ejecutable. Cubre a quien copia el archivo a la carpeta del
     * agente en vez de arrastrarlo: en ese caso el agente se vincula solo al arrancar.
     */
    public static Optional<Path> buscarJuntoAlEjecutable() {
        Path directorio = Path.of("").toAbsolutePath();
        try (Stream<Path> archivos = Files.list(directorio)) {
            return archivos
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase().endsWith(EXTENSION))
                .findFirst();
        } catch (IOException ex) {
            return Optional.empty();
        }
    }
}
