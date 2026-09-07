package py.com.vetcontrol.agente;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Impide que corran dos agentes a la vez en la misma PC.
 *
 * <p>Sin esto, abrir el acceso directo cuando el agente ya estaba corriendo (que es lo normal:
 * arranca solo con Windows) dejaba <b>dos agentes vivos escuchando la misma cola</b>. El sintoma
 * visible era el menor: "le doy doble clic y no pasa nada", porque con la PC ya vinculada el
 * agente no abre ninguna ventana, solo vive en la bandeja. El riesgo real era el otro: dos agentes
 * compitiendo por los mismos trabajos, con lo que un ticket podia salir dos veces.
 *
 * <p>Se usa un candado de archivo del sistema operativo y no un archivo "pid" ni un puerto: si el
 * agente muere de golpe (corte de luz, Administrador de tareas), Windows libera el candado solo. Un
 * archivo marcador habria que limpiarlo a mano y dejaria al agente sin arrancar despues de un
 * apagon.
 */
public final class InstanciaUnica implements AutoCloseable {

    private final FileChannel canal;
    private final FileLock candado;

    private InstanciaUnica(FileChannel canal, FileLock candado) {
        this.canal = canal;
        this.candado = candado;
    }

    /** Archivo del candado, dentro de la carpeta de datos del agente. */
    public static Path archivoPorDefecto() {
        return AgenteConfig.directorioDatos().resolve("agente.lock");
    }

    /**
     * Toma el candado, o devuelve null si ya lo tiene otro proceso.
     *
     * <p>Nunca lanza por un problema del sistema de archivos: si el candado no se puede crear
     * (permisos raros, disco lleno) es preferible dejar arrancar el agente a dejar la PC sin
     * imprimir por un archivo auxiliar.
     */
    public static InstanciaUnica tomar(Path archivo) {
        try {
            if (archivo.getParent() != null) Files.createDirectories(archivo.getParent());
            FileChannel canal = FileChannel.open(
                archivo, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock candado;
            try {
                candado = canal.tryLock();
            } catch (OverlappingFileLockException ex) {
                // Otra instancia dentro de esta misma JVM (solo pasa en tests).
                canal.close();
                return null;
            }
            if (candado == null) {
                canal.close();
                return null;
            }
            return new InstanciaUnica(canal, candado);
        } catch (IOException ex) {
            return new InstanciaUnica(null, null);
        }
    }

    public static InstanciaUnica tomar() {
        return tomar(archivoPorDefecto());
    }

    @Override
    public void close() {
        try {
            if (candado != null) candado.release();
            if (canal != null) canal.close();
        } catch (IOException ex) {
            // Cerrando: no hay nada util que hacer, y el sistema libera el candado igual.
        }
    }
}
