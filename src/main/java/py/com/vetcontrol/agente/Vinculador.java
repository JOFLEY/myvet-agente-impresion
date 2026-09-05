package py.com.vetcontrol.agente;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Vincula el equipo a un puesto la primera vez.
 *
 * <p>El orden va del camino que menos molesta al que mas:
 *
 * <ol>
 *   <li>Un `.vcagente` junto al ejecutable: se vincula solo, sin preguntar nada.</li>
 *   <li><b>El navegador</b>: el agente se anuncia y abre VetControl para que el usuario elija el
 *       puesto. Es el camino normal desde que el instalador es uno solo para todas las clinicas.</li>
 *   <li>La ventana de siempre (arrastrar el archivo o pegar el codigo), como plan B.</li>
 * </ol>
 */
public final class Vinculador {

    private enum Resultado { VINCULADO, CANCELADO, PROBAR_OTRO_CAMINO }

    private final AgenteConfig config;
    private final Bitacora bitacora;

    public Vinculador(AgenteConfig config, Bitacora bitacora) {
        this.config = config;
        this.bitacora = bitacora;
    }

    /** @return true si el equipo quedo vinculado. */
    public boolean vincular() {
        if (vincularConArchivoCercano()) return true;

        Resultado porNavegador = vincularPorNavegador();
        if (porNavegador == Resultado.VINCULADO) return true;
        if (porNavegador == Resultado.CANCELADO) return false;

        return vincularConVentana();
    }

    // ------------------------------------------------- 1. archivo cercano

    private boolean vincularConArchivoCercano() {
        Optional<Path> archivo = Vinculacion.buscarJuntoAlEjecutable();
        if (archivo.isEmpty()) return false;

        bitacora.info("Encontrado " + archivo.get().getFileName() + " junto al agente.");
        try {
            // Sin cartel: por este camino nadie esta mirando la pantalla (puede ser el arranque
            // automatico con Windows), y un dialogo modal dejaria al agente colgado esperando un
            // clic que nunca llega, sin llegar a imprimir nada.
            if (canjear(Vinculacion.leer(archivo.get()), true, false)) {
                marcarComoUsado(archivo.get());
                return true;
            }
        } catch (IllegalArgumentException ex) {
            bitacora.error("El archivo de vinculacion no sirve: " + ex.getMessage());
        }
        return false;
    }

    // ----------------------------------------------------- 2. navegador

    /**
     * El agente se presenta y espera a que alguien lo reclame desde VetControl.
     *
     * <p>Mientras espera se reanuncia cada tanto: eso renueva la vigencia en el servidor sin cambiar
     * el secreto, asi que el link que ya esta abierto en el navegador sigue sirviendo aunque la
     * persona tarde en encontrar su sesion.
     */
    private Resultado vincularPorNavegador() {
        String secreto = VinculacionNavegador.nuevoSecreto();
        String host = AgenteMain.hostname();
        ApiCliente api = new ApiCliente(config.baseUrl(), null);

        try {
            api.anunciar(secreto, host, AgenteMain.VERSION);
        } catch (ApiException ex) {
            bitacora.error("No se pudo anunciar esta PC: " + ex.getMessage());
            return Resultado.PROBAR_OTRO_CAMINO;
        }

        String url = VinculacionNavegador.urlVinculacion(config.baseUrl(), secreto);
        if (!VinculacionNavegador.abrir(url, bitacora)) {
            bitacora.error("No se pudo abrir el navegador; se ofrece el camino manual.");
            return Resultado.PROBAR_OTRO_CAMINO;
        }
        bitacora.info("Esperando que elijan el puesto desde el navegador (PC: " + host + ").");

        VentanaEsperando ventana = VentanaEsperando.mostrar(host, url, bitacora);
        try {
            return esperarVinculacion(api, secreto, host, ventana);
        } finally {
            ventana.cerrar();
        }
    }

    private Resultado esperarVinculacion(
        ApiCliente api,
        String secreto,
        String host,
        VentanaEsperando ventana
    ) {
        Instant limite = Instant.now().plus(VinculacionNavegador.ESPERA_MAXIMA);
        Instant proximoAnuncio = Instant.now().plus(VinculacionNavegador.RENOVAR_CADA);

        while (Instant.now().isBefore(limite)) {
            if (ventana.accion() != VentanaEsperando.Accion.ESPERANDO) {
                bitacora.info("Vinculacion por navegador interrumpida por el usuario.");
                return ventana.accion() == VentanaEsperando.Accion.CANCELADO
                    ? Resultado.CANCELADO
                    : Resultado.PROBAR_OTRO_CAMINO;
            }

            try {
                if (Instant.now().isAfter(proximoAnuncio)) {
                    api.anunciar(secreto, host, AgenteMain.VERSION);
                    proximoAnuncio = Instant.now().plus(VinculacionNavegador.RENOVAR_CADA);
                }

                ApiCliente.Emparejamiento par = api.reclamarVinculacion(secreto);
                if (par != null) {
                    guardarVinculacion(par);
                    // SIN cartel de confirmacion. Un JOptionPane es MODAL: bloquearia este hilo
                    // esperando un clic, y el agente nunca llegaria a reportar sus impresoras. Peor
                    // todavia, quien apreto "Vincular" puede estar en otra maquina, asi que ese
                    // clic podria no llegar nunca. La confirmacion ya la da el navegador, que es
                    // donde el usuario esta mirando; aca alcanza con el globo de la bandeja.
                    return Resultado.VINCULADO;
                }
            } catch (ApiException ex) {
                // Un error aca no es fatal: puede ser la red, o el secreto vencido si la PC estuvo
                // suspendida. Se avisa en la ventana y se sigue intentando hasta el tope.
                bitacora.error("Esperando vinculacion: " + ex.getMessage());
                ventana.mensaje("Reintentando... (" + ex.getMessage() + ")");
            }

            dormir(VinculacionNavegador.INTERVALO);
        }

        bitacora.error("Se agoto el tiempo esperando la vinculacion desde el navegador.");
        return Resultado.PROBAR_OTRO_CAMINO;
    }

    // ------------------------------------------------- 3. ventana manual

    private boolean vincularConVentana() {
        VentanaVinculacion.Resultado elegido = VentanaVinculacion.pedir();
        if (elegido == null) {
            bitacora.info("Vinculacion cancelada por el usuario.");
            return false;
        }
        try {
            // Aca si mostramos cartel: el usuario acaba de hacer clic y espera una confirmacion.
            return canjear(elegido.vinculacion(), elegido.arranqueAutomatico(), true);
        } catch (IllegalArgumentException ex) {
            VentanaVinculacion.aviso(ex.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------ comun

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

    /** Canje del codigo por el token permanente (caminos 1 y 3). */
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
            guardarVinculacion(par, arranqueAutomatico);

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

    private void guardarVinculacion(ApiCliente.Emparejamiento par) {
        guardarVinculacion(par, true);
    }

    private void guardarVinculacion(ApiCliente.Emparejamiento par, boolean arranqueAutomatico) {
        config.emparejado(par.token(), par.puestoId(), par.nombre());
        config.guardar();
        bitacora.info("Vinculado al puesto '" + par.nombre() + "' (id " + par.puestoId() + ")");

        // Se activa solo: una PC que imprime tickets tiene que estar lista al prender la maquina, y
        // nadie va a acordarse de abrir el agente cada mañana. Es reversible desde la bandeja.
        if (arranqueAutomatico && ArranqueAutomatico.activar()) {
            bitacora.info("Arranque automatico activado.");
        }
    }

    private void dormir(Duration duracion) {
        try {
            Thread.sleep(duracion.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
