package py.com.vetcontrol.agente;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;

/**
 * Impresoras instaladas en Windows.
 *
 * <p>{@code PrintServiceLookup} ve por igual las USB y las de red, siempre que esten instaladas en
 * Windows con su driver. Eso es justamente lo que WebUSB no podia hacer, y lo que permite que este
 * agente funcione con cualquier marca de termica: imprimimos un PDF por el driver, no comandos
 * ESC/POS especificos de cada fabricante.
 */
public final class Impresoras {

    private Impresoras() {}

    public static List<String> nombres() {
        List<String> nombres = new ArrayList<>();
        for (PrintService servicio : PrintServiceLookup.lookupPrintServices(null, null)) {
            nombres.add(servicio.getName());
        }
        nombres.sort(String::compareToIgnoreCase);
        return nombres;
    }

    public static Optional<PrintService> buscar(String nombre) {
        return buscarEntre(Arrays.asList(PrintServiceLookup.lookupPrintServices(null, null)), nombre);
    }

    static Optional<PrintService> buscarEntre(List<PrintService> servicios, String nombre) {
        for (PrintService servicio : servicios) {
            if (coincide(servicio.getName(), nombre)) return Optional.of(servicio);
        }
        return Optional.empty();
    }

    /**
     * Criterio de matcheo, aislado para poder testearlo sin impresoras reales.
     *
     * <p>Ignora mayusculas y espacios de sobra: el nombre viaja del agente al servidor y vuelve, y
     * un driver puede reportarlo con distinto casing entre arranques de Windows. Un nombre vacio no
     * matchea con nada — si no, un puesto sin impresora configurada agarraria la primera de la lista.
     */
    static boolean coincide(String nombreServicio, String buscado) {
        if (nombreServicio == null || buscado == null || buscado.isBlank()) return false;
        return nombreServicio.trim().equalsIgnoreCase(buscado.trim());
    }

    public static Optional<String> nombrePorDefecto() {
        PrintService servicio = PrintServiceLookup.lookupDefaultPrintService();
        return servicio == null ? Optional.empty() : Optional.of(servicio.getName());
    }
}
