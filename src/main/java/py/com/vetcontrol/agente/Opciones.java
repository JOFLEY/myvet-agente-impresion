package py.com.vetcontrol.agente;

/** Argumentos de linea de comandos. Parseo separado del main para poder testearlo. */
public record Opciones(
    String url,
    String codigo,
    boolean dryRun,
    boolean listarImpresoras,
    boolean ayuda,
    boolean version
) {

    public static Opciones parse(String[] args) {
        String url = null;
        String codigo = null;
        boolean dryRun = false;
        boolean listar = false;
        boolean ayuda = false;
        boolean version = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i].trim();
            switch (arg) {
                case "--url" -> url = valorDe(args, ++i, "--url");
                case "--emparejar" -> codigo = valorDe(args, ++i, "--emparejar");
                case "--dry-run" -> dryRun = true;
                case "--listar-impresoras" -> listar = true;
                case "--version" -> version = true;
                case "--help", "-h", "--ayuda" -> ayuda = true;
                default -> throw new IllegalArgumentException("Opcion desconocida: " + arg);
            }
        }
        return new Opciones(url, codigo, dryRun, listar, ayuda, version);
    }

    private static String valorDe(String[] args, int indice, String opcion) {
        if (indice >= args.length) throw new IllegalArgumentException(opcion + " necesita un valor");
        return args[indice].trim();
    }
}
