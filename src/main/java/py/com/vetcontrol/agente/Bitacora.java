package py.com.vetcontrol.agente;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Log a consola y a archivo. Sin dependencias de logging: el archivo es lo que la clinica manda
 * cuando algo no imprime, asi que tiene que existir siempre y ser legible por una persona.
 */
public final class Bitacora {

    private static final DateTimeFormatter RELOJ = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_BYTES = 2L * 1024 * 1024;

    private final Path archivo;

    public Bitacora(Path archivo) {
        this.archivo = archivo;
    }

    public static Bitacora enDirectorioDeDatos() {
        return new Bitacora(AgenteConfig.directorioDatos().resolve("agente.log"));
    }

    public void info(String mensaje) {
        escribir("INFO ", mensaje);
    }

    public void error(String mensaje) {
        escribir("ERROR", mensaje);
    }

    public void error(String mensaje, Throwable causa) {
        escribir("ERROR", mensaje + " -> " + causa.getClass().getSimpleName() + ": " + causa.getMessage());
    }

    private void escribir(String nivel, String mensaje) {
        String linea = LocalDateTime.now().format(RELOJ) + " [" + nivel + "] " + mensaje;
        System.out.println(linea);
        try {
            Files.createDirectories(archivo.getParent());
            rotarSiHaceFalta();
            Files.writeString(archivo, linea + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            // No poder escribir el log jamas debe tumbar al agente: la impresion sigue siendo lo importante.
            System.err.println("No se pudo escribir la bitacora: " + ex.getMessage());
        }
    }

    private void rotarSiHaceFalta() throws IOException {
        if (Files.exists(archivo) && Files.size(archivo) > MAX_BYTES) {
            Files.move(archivo, archivo.resolveSibling("agente.log.1"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
