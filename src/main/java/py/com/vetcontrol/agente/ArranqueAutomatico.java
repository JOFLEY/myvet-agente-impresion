package py.com.vetcontrol.agente;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Hace que el agente arranque solo al iniciar sesion, sin que nadie corra un script.
 *
 * <p>Usa la clave Run del usuario (HKCU) y no un servicio de Windows a proposito: un servicio corre
 * en la Sesion 0 y desde ahi las impresoras de red mapeadas por usuario NO son visibles. Ademas
 * HKCU no necesita permisos de administrador, que es justo lo que no tiene quien atiende el
 * mostrador.
 */
public final class ArranqueAutomatico {

    static final String CLAVE = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    static final String VALOR = "MyVetAgente";

    /**
     * Como se llamaba la entrada cuando el agente era VetControl.
     *
     * <p>Hay que borrarla al activar la nueva, o Windows arrancaria DOS agentes: el viejo (que
     * apunta a una instalacion que quiza ya no existe) y el nuevo.
     */
    static final String VALOR_ANTERIOR = "VetControlAgente";

    private ArranqueAutomatico() {}

    public static boolean disponible() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** Ruta del ejecutable que lanzo esta JVM (el .exe de jpackage), si se puede determinar. */
    public static Path ejecutable() {
        String launcher = System.getProperty("jpackage.app-path");
        return launcher == null || launcher.isBlank() ? null : Path.of(launcher);
    }

    public static boolean activado() {
        if (!disponible()) return false;
        try {
            Process proceso = new ProcessBuilder("reg", "query", CLAVE, "/v", VALOR)
                .redirectErrorStream(true)
                .start();
            return proceso.waitFor() == 0;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** @return true si quedo aplicado. No lanza: no poder configurarlo no debe frenar la vinculacion. */
    public static boolean activar() {
        Path exe = ejecutable();
        if (!disponible() || exe == null) return false;
        // Se limpia la entrada del nombre viejo antes de poner la nueva: dos agentes arrancando a
        // la vez se pelearian los mismos tickets.
        ejecutarReg("delete", CLAVE, "/v", VALOR_ANTERIOR, "/f");
        return ejecutarReg("add", CLAVE, "/v", VALOR, "/t", "REG_SZ", "/d", exe.toString(), "/f");
    }

    public static boolean desactivar() {
        if (!disponible()) return false;
        return ejecutarReg("delete", CLAVE, "/v", VALOR, "/f");
    }

    private static boolean ejecutarReg(String... argumentos) {
        try {
            String[] comando = new String[argumentos.length + 1];
            comando[0] = "reg";
            System.arraycopy(argumentos, 0, comando, 1, argumentos.length);
            Process proceso = new ProcessBuilder(comando).redirectErrorStream(true).start();
            return proceso.waitFor() == 0;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
