package py.com.vetcontrol.agente;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Configuracion persistente del agente: donde esta la API y el token del puesto.
 *
 * <p>Vive en el perfil del usuario y no en Archivos de Programa, porque el agente corre como el
 * usuario logueado — no como servicio. Eso no es un detalle: las impresoras de red mapeadas por
 * usuario NO son visibles para un servicio corriendo en la Sesion 0.
 *
 * <p>Y vive FUERA de la carpeta donde se instala el programa. Hasta la 0.3.0 los datos estaban en
 * {@code %LOCALAPPDATA%\MyVetAgente}, que es justo el directorio de instalacion: actualizar el
 * agente borraba la vinculacion y la PC quedaba pidiendo vincularse de nuevo. Se comprobo
 * instalando la 0.3.0 sobre la 0.1.0.
 */
public final class AgenteConfig {

    public static final String URL_POR_DEFECTO = "https://myvet.serfley.com";

    private static final String ARCHIVO = "agente.properties";

    /**
     * Carpetas donde vivieron los datos antes, en orden de preferencia: la del instalador hasta la
     * 0.3.0, y la de cuando el agente se llamaba VetControl.
     *
     * <p>Se leen una sola vez para no obligar a nadie a volver a vincular su PC: quien ya tenia el
     * agente andando cambio de carpeta, no de puesto.
     */
    private static final List<String> CARPETAS_ANTERIORES = List.of("MyVetAgente", "VetControlAgente");

    private final Path archivo;
    private final Properties props = new Properties();

    AgenteConfig(Path archivo) {
        this.archivo = archivo;
    }

    public static AgenteConfig cargar() {
        return cargarDesde(directorioDatos(), carpetasHeredables());
    }

    static AgenteConfig cargarDesde(Path directorio) {
        return cargarDesde(directorio, List.of());
    }

    static AgenteConfig cargarDesde(Path directorio, List<Path> anteriores) {
        Path archivo = directorio.resolve(ARCHIVO);
        if (!Files.exists(archivo)) {
            heredarDeCarpetaAnterior(anteriores, directorio, archivo);
        }

        AgenteConfig config = new AgenteConfig(archivo);
        if (Files.exists(config.archivo)) {
            try (InputStream in = Files.newInputStream(config.archivo)) {
                config.props.load(in);
            } catch (IOException ex) {
                throw new IllegalStateException("No se pudo leer " + config.archivo, ex);
            }
        }
        return config;
    }

    /**
     * Trae la configuracion de la carpeta que usaba el agente cuando se llamaba VetControl.
     *
     * <p>Sin esto, actualizar el agente dejaria a la PC "sin vincular" y habria que rehacer la
     * vinculacion en cada clinica que ya lo tenia funcionando. Se copia y no se mueve: si algo sale
     * mal, la instalacion vieja sigue intacta.
     */
    private static void heredarDeCarpetaAnterior(List<Path> anteriores, Path directorio, Path destino) {
        Path anterior = null;
        for (Path carpeta : anteriores) {
            Path candidato = carpeta.resolve(ARCHIVO);
            if (Files.exists(candidato)) {
                anterior = candidato;
                break;
            }
        }
        if (anterior == null) return;
        try {
            Files.createDirectories(directorio);
            Files.copy(anterior, destino);
        } catch (IOException ex) {
            // Si no se pudo, el agente simplemente pedira vincular de nuevo: molesto, no grave.
        }
    }

    /**
     * {@code %LOCALAPPDATA%\MyVet\Agente} en Windows, {@code ~/.myvet-agente} en el resto.
     *
     * <p>No es {@code %LOCALAPPDATA%\MyVetAgente}: esa es la carpeta de instalacion y el
     * instalador la vacia al actualizar.
     */
    public static Path directorioDatos() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Paths.get(localAppData, "MyVet", "Agente");
        }
        return Paths.get(System.getProperty("user.home"), ".myvet-agente");
    }

    /** Donde buscar una vinculacion anterior cuando la carpeta nueva todavia esta vacia. */
    static List<Path> carpetasHeredables() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank()) return List.of();
        List<Path> carpetas = new ArrayList<>();
        for (String nombre : CARPETAS_ANTERIORES) {
            carpetas.add(Paths.get(localAppData, nombre));
        }
        return carpetas;
    }

    public void guardar() {
        try {
            Files.createDirectories(archivo.getParent());
            try (OutputStream out = Files.newOutputStream(archivo)) {
                props.store(out, "Agente de impresion MyVet - NO compartir: contiene el token del puesto");
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
