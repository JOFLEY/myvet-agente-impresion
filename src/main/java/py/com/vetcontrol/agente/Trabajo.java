package py.com.vetcontrol.agente;

/** Un ticket que el servidor mando a imprimir. El PDF se baja aparte. */
public record Trabajo(long id, String impresora, String formato, Integer anchoMm) {}
