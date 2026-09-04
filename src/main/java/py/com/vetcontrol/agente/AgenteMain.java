package py.com.vetcontrol.agente;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;
import javax.print.PrintService;

/**
 * Agente de impresion de VetControl.
 *
 * <p>Corre en la PC que tiene la impresora, hace long-poll SALIENTE contra la API y imprime los
 * tickets que le entregan. No abre ningun puerto: el navegador nunca lo contacta. Ver
 * docs/plan-impresion-directa-tickets.md.
 */
public final class AgenteMain {

    public static final String VERSION = "0.1.0";

    /** Cada cuantos ciclos vacios se vuelve a reportar la lista de impresoras. */
    private static final int CICLOS_ENTRE_REPORTES = 60;

    private final AgenteConfig config;
    private final ApiCliente api;
    private final ImpresorPdf impresor;
    private final Bitacora bitacora;
    private final boolean dryRun;

    private AgenteMain(AgenteConfig config, ApiCliente api, Bitacora bitacora, boolean dryRun) {
        this.config = config;
        this.api = api;
        this.bitacora = bitacora;
        this.dryRun = dryRun;
        this.impresor = new ImpresorPdf(dryRun, bitacora);
    }

    public static void main(String[] args) {
        Opciones opciones;
        try {
            opciones = Opciones.parse(args);
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            imprimirAyuda();
            System.exit(2);
            return;
        }

        if (opciones.ayuda()) {
            imprimirAyuda();
            return;
        }
        if (opciones.version()) {
            System.out.println("Agente de impresion VetControl " + VERSION);
            return;
        }
        if (opciones.listarImpresoras()) {
            listarImpresoras();
            return;
        }

        Bitacora bitacora = Bitacora.enDirectorioDeDatos();
        AgenteConfig config = AgenteConfig.cargar();
        if (opciones.url() != null) {
            config.baseUrl(opciones.url());
            config.guardar();
        }

        if (opciones.codigo() != null) {
            System.exit(emparejar(config, bitacora, opciones.codigo()));
            return;
        }

        if (!config.estaEmparejado()) {
            System.err.println("""
                Este equipo todavia no esta vinculado a un puesto de impresion.

                1) En VetControl: Configuraciones > Impresion > Puestos de impresion > Agregar puesto.
                2) Copia el codigo que aparece en pantalla (vence en 10 minutos).
                3) Volve a ejecutar:  vetcontrol-agente --emparejar CODIGO
                """);
            System.exit(2);
            return;
        }

        ApiCliente api = new ApiCliente(config.baseUrl(), config.token());
        new AgenteMain(config, api, bitacora, opciones.dryRun()).correr();
    }

    // ------------------------------------------------------------ acciones

    private static void listarImpresoras() {
        List<String> nombres = Impresoras.nombres();
        if (nombres.isEmpty()) {
            System.out.println("No se encontro ninguna impresora instalada en Windows.");
            return;
        }
        String porDefecto = Impresoras.nombrePorDefecto().orElse(null);
        System.out.println("Impresoras instaladas (" + nombres.size() + "):");
        for (String nombre : nombres) {
            System.out.println("  - " + nombre + (nombre.equals(porDefecto) ? "   [predeterminada]" : ""));
        }
    }

    private static int emparejar(AgenteConfig config, Bitacora bitacora, String codigo) {
        ApiCliente api = new ApiCliente(config.baseUrl(), null);
        try {
            ApiCliente.Emparejamiento par = api.emparejar(codigo, hostname(), VERSION);
            config.emparejado(par.token(), par.puestoId(), par.nombre());
            config.guardar();
            bitacora.info("Emparejado con el puesto '" + par.nombre() + "' (id " + par.puestoId() + ")");
            System.out.println("Listo. Este equipo quedo vinculado al puesto '" + par.nombre() + "'.");
            System.out.println("Configuracion guardada en: " + config.archivo());
            System.out.println("Ya podes ejecutar el agente sin argumentos para que quede escuchando.");
            return 0;
        } catch (ApiException ex) {
            bitacora.error("Fallo el emparejamiento: " + ex.getMessage());
            System.err.println("No se pudo emparejar: " + ex.getMessage());
            System.err.println("Revisa que el codigo sea el correcto y que no hayan pasado 10 minutos.");
            return 1;
        }
    }

    private void correr() {
        bitacora.info("Agente " + VERSION + " iniciado. Puesto='" + config.puestoNombre()
            + "' api=" + config.baseUrl() + (dryRun ? " [DRY-RUN]" : ""));
        reportarImpresoras();

        Backoff backoff = new Backoff();
        int ciclosVacios = 0;

        while (true) {
            try {
                Trabajo trabajo = api.siguienteTrabajo();
                backoff.reiniciar();

                if (trabajo == null) {
                    if (++ciclosVacios % CICLOS_ENTRE_REPORTES == 0) reportarImpresoras();
                    continue;
                }
                ciclosVacios = 0;
                procesar(trabajo);
            } catch (ApiException ex) {
                if (ex.tokenRechazado()) {
                    bitacora.error("La API rechazo el token de este puesto (" + ex.getMessage() + ").");
                    System.err.println("""
                        Este puesto ya no esta autorizado. Puede que lo hayan eliminado o regenerado
                        su codigo desde VetControl. Volve a vincularlo con:
                          vetcontrol-agente --emparejar CODIGO
                        """);
                    return;
                }
                esperar(backoff.siguienteMs(), "API no disponible: " + ex.getMessage());
            } catch (RuntimeException ex) {
                esperar(backoff.siguienteMs(), "Error inesperado en el ciclo: " + ex.getMessage());
            }
        }
    }

    /**
     * Un fallo al imprimir NO tumba al agente: se reporta como ERROR para que se vea en VetControl y
     * el ciclo sigue. Si el agente se cayera con cada trabajo problematico, la caja se quedaria sin
     * imprimir hasta que alguien lo reinicie a mano.
     */
    private void procesar(Trabajo trabajo) {
        bitacora.info("Trabajo " + trabajo.id() + " -> impresora='" + trabajo.impresora()
            + "' formato=" + trabajo.formato() + " ancho=" + trabajo.anchoMm() + "mm");
        try {
            Optional<PrintService> destino = Impresoras.buscar(trabajo.impresora());
            if (destino.isEmpty()) {
                fallar(trabajo, "La impresora '" + trabajo.impresora()
                    + "' no esta instalada en esta PC. Instaladas: " + String.join(", ", Impresoras.nombres()));
                return;
            }

            byte[] pdf = api.pdf(trabajo.id());
            String detalle = impresor.imprimir(pdf, destino.get(), "VetControl ticket " + trabajo.id());

            if (dryRun) {
                // No mentimos: en dry-run el ticket NO salio, y asi se ve en VetControl.
                api.reportarResultado(trabajo.id(), "ERROR", "dry-run: no se envio a la impresora (" + detalle + ")");
            } else {
                api.reportarResultado(trabajo.id(), "OK", null);
            }
        } catch (ApiException ex) {
            bitacora.error("Trabajo " + trabajo.id() + ": fallo hablando con la API", ex);
        } catch (Exception ex) {
            bitacora.error("Trabajo " + trabajo.id() + ": no se pudo imprimir", ex);
            fallar(trabajo, ex.getMessage());
        }
    }

    private void fallar(Trabajo trabajo, String motivo) {
        bitacora.error("Trabajo " + trabajo.id() + " ERROR: " + motivo);
        try {
            api.reportarResultado(trabajo.id(), "ERROR", motivo);
        } catch (ApiException ex) {
            bitacora.error("Tampoco se pudo reportar el error del trabajo " + trabajo.id(), ex);
        }
    }

    private void reportarImpresoras() {
        try {
            List<String> nombres = Impresoras.nombres();
            api.reportarImpresoras(nombres, hostname(), VERSION);
            bitacora.info("Impresoras reportadas (" + nombres.size() + "): " + String.join(", ", nombres));
        } catch (ApiException ex) {
            bitacora.error("No se pudieron reportar las impresoras", ex);
        }
    }

    private void esperar(long ms, String motivo) {
        bitacora.error(motivo + " Reintentando en " + (ms / 1000) + "s.");
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return System.getenv().getOrDefault("COMPUTERNAME", "desconocido");
        }
    }

    private static void imprimirAyuda() {
        System.out.println("""
            Agente de impresion VetControl %s

            Uso:
              vetcontrol-agente                        Queda escuchando e imprime los tickets.
              vetcontrol-agente --emparejar CODIGO     Vincula este equipo a un puesto (una sola vez).
              vetcontrol-agente --listar-impresoras    Muestra las impresoras instaladas y sale.
              vetcontrol-agente --dry-run              Igual que sin argumentos, pero NO imprime:
                                                       registra que impresora y que medidas habria usado.
              vetcontrol-agente --url URL              Cambia la direccion de la API (default %s).
              vetcontrol-agente --version | --help

            Configuracion y bitacora: %s
            """.formatted(VERSION, AgenteConfig.URL_POR_DEFECTO, AgenteConfig.directorioDatos()));
    }
}
