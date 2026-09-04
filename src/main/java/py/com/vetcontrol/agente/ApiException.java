package py.com.vetcontrol.agente;

/** Respuesta de error de la API. {@code status} 401/403 significa que el token ya no sirve. */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String mensaje) {
        super(mensaje);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public boolean tokenRechazado() {
        return status == 401 || status == 403;
    }
}
