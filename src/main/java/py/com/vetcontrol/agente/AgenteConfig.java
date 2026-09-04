package py.com.vetcontrol.agente;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Configuracion persistente del agente: donde esta la API y el token del puesto.
 *
 * <p>Vive en el perfil del usuario ({@code %LOCALAPPDATA%\VetControlAgente} en Windows) y no en
 * Archivos de Programa, porque el agente corre como el usuario logueado — no como servicio. Eso no
 * es un detalle: las impresoras de red mapeadas por usuario NO son visibles para un servicio
 * corriendo en la Sesion 0.
 */
public final class AgenteConfig {

    public static final String URL_POR_DEFECTO = "https://myvet.serfley.com";

    private static final String ARCHIVO = "agente.properties";

    private final Path archivo;
    private final Properties props = new Properties();

    AgenteConfig(Path archivo) {
        this.archivo = archivo;
    }

    public static AgenteConfig cargar() {
        return cargarDesde(directorioDatos());
    }

    static AgenteConfig cargarDesde(Path directorio) {
        AgenteConfig config = new AgenteConfig(directorio.resolve(ARCHIVO));
        if (Files.exists(config.archivo)) {
            try (InputStream in = Files.newInputStream(config.archivo)) {
                config.props.load(in);
            } catch (IOException ex) {
                throw new IllegalStateException("No se pudo leer " + config.archivo, ex);
            }
        }
        return config;
    }

    /** {@code %LOCALAPPDATA%\VetControlAgente} en Windows, {@code ~/.vetcontrol-agente} en el resto. */
    public static Path directorioDatos() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Paths.get(localAppData, "VetControlAgente");
        }
        return Paths.get(System.getProperty("user.home"), ".vetcontrol-agente");
    }

    public void guardar() {
        try {
            Files.createDirectories(archivo.getParent());
            try (OutputStream out = Files.newOutputStream(archivo)) {
                props.store(out, "Agente de impresion VetControl - NO compartir: contiene el token del puesto");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo escribir " + archivo, ex);
        }
    }

    public Path archivo() {
        return archivo;
    }

    public String baseUrl() {
        String url = props.getProperty("baseUrl", URL_POR_DEFECTO).trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public void baseUrl(String url) {
        props.setProperty("baseUrl", url.trim());
    }

    public String token() {
        return props.getProperty("token");
    }

    public boolean estaEmparejado() {
        String token = token();
        return token != null && !token.isBlank();
    }

    public void emparejado(String token, long puestoId, String puestoNombre) {
        props.setProperty("token", token);
        props.setProperty("puestoId", Long.toString(puestoId));
        props.setProperty("puestoNombre", puestoNombre);
    }

    public String puestoNombre() {
        return props.getProperty("puestoNombre", "(sin nombre)");
    }
}
