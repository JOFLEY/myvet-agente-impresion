package py.com.vetcontrol.agente;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Vincula el equipo a un puesto la primera vez.
 *
 * <p>El orden va del camino que menos molesta al que mas:
 *
 * <ol>
 *   <li>Un `.vcagente` que ya este junto al ejecutable: se vincula solo, sin preguntar nada.</li>
 *   <li>La ventana, donde se arrastra el archivo descargado.</li>
 *   <li>El codigo tipeado a mano, dentro de esa misma ventana.</li>
 * </ol>
 */
public final class Vinculador {

    private final AgenteConfig config;
    private final Bitacora bitacora;

    public Vinculador(AgenteConfig config, Bitacora bitacora) {
        this.config = config;
        this.bitacora = bitacora;
    }

    /** @return true si el equipo quedo vinculado. */
    public boolean vincularConVentana() {
        Optional<Path> archivoCercano = Vinculacion.buscarJuntoAlEjecutable();
        if (archivoCercano.isPresent()) {
            bitacora.info("Encontrado " + archivoCercano.get().getFileName() + " junto al agente.");
            try {
                // Sin cartel: por este camino nadie esta mirando la pantalla (puede ser el arranque
                // automatico con Windows), y un dialogo modal dejaria al agente colgado esperando
                // un clic que nunca llega, sin llegar a imprimir nada.
                if (canjear(Vinculacion.leer(archivoCercano.get()), true, false)) {
                    marcarComoUsado(archivoCercano.get());
                    return true;
                }
            } catch (IllegalArgumentException ex) {
                bitacora.error("El archivo de vinculacion no sirve: " + ex.getMessage());
            }
        }

        VentanaVinculacion.Resultado elegido = VentanaVinculacion.pedir();
        if (elegido == null) {
            bitacora.info("Vinculacion cancelada por el usuario.");
            return false;
        }
        try {
            // Aca si: el usuario acaba de hacer clic y espera una confirmacion.
            return canjear(elegido.vinculacion(), elegido.arranqueAutomatico(), true);
        } catch (IllegalArgumentException ex) {
            VentanaVinculacion.aviso(ex.getMessage());
            return false;
        }
    }

    /**
     * Renombra el archivo ya canjeado en vez de borrarlo: el codigo que lleva adentro ya no sirve,
     * pero es un archivo del usuario y no nos corresponde eliminarlo. Al no terminar en
     * {@code .vcagente} deja de ser candidato en el proximo arranque.
     */
    private void marcarComoUsado(Path archivo) {
        try {
            Path usado = archivo.resolveSibling(archivo.getFileName() + ".usado");
            java.nio.file.Files.move(
                archivo, usado, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            bitacora.info("Archivo de vinculacion marcado como usado: " + usado.getFileName());
        } catch (Exception ex) {
            bitacora.error("No se pudo renombrar el archivo de vinculacion", ex);
        }
    }

    /** Canje del codigo por el token permanente. Comun a los tres caminos. */
    private boolean canjear(
        Vinculacion vinculacion,
        boolean arranqueAutomatico,
        boolean mostrarCartel
    ) {
        // Un codigo tipeado a mano no trae URL: se usa la que ya tenga configurada el agente.
        if (vinculacion.url() != null && !vinculacion.url().isBlank()) {
            config.baseUrl(vinculacion.url());
        }

        try {
            ApiCliente api = new ApiCliente(config.baseUrl(), null);
            ApiCliente.Emparejamiento par = api.emparejar(
                vinculacion.codigo(), AgenteMain.hostname(), AgenteMain.VERSION);
            config.emparejado(par.token(), par.puestoId(), par.nombre());
            config.guardar();
            bitacora.info("Vinculado al puesto '" + par.nombre() + "' (id " + par.puestoId() + ")");

            if (arranqueAutomatico && ArranqueAutomatico.activar()) {
                bitacora.info("Arranque automatico activado.");
            }

            if (mostrarCartel) {
                VentanaVinculacion.aviso(
                    "Listo. Esta PC quedo vinculada al puesto \"" + par.nombre() + "\".\n\n"
                        + "Ahora entra a VetControl y elegi cual de sus impresoras es la de tickets.");
            }
            return true;
        } catch (ApiException ex) {
            String detalle = ex.status() == 0
                ? "No se pudo contactar a VetControl. Revisa la conexion a internet."
                : ex.getMessage();
            bitacora.error("Fallo la vinculacion: " + detalle);
            if (mostrarCartel) {
                VentanaVinculacion.aviso(
                    "No se pudo vincular:\n\n" + detalle
                        + "\n\nSi el archivo tiene mas de 10 minutos, descarga uno nuevo desde VetControl.");
            }
            return false;
        }
    }
}
