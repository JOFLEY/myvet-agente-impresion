package py.com.vetcontrol.agente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Se prueba contra un servidor HTTP de verdad (el {@code HttpServer} del JDK, sin dependencias
 * nuevas) en vez de mockear el cliente: lo que interesa verificar es justamente el contrato HTTP
 * (204 vs 200, header de autorizacion, forma del error), y un mock del propio HttpClient no probaria
 * nada de eso.
 */
class ApiClienteTest {

    private HttpServer servidor;
    private String baseUrl;
    private final List<String> rutasRecibidas = new ArrayList<>();
    private final AtomicReference<String> ultimaAutorizacion = new AtomicReference<>();

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @AfterEach
    void bajarServidor() {
        if (servidor != null) servidor.stop(0);
    }

    private void responder(String ruta, int status, String cuerpo) {
        servidor.createContext(ruta, intercambio -> {
            rutasRecibidas.add(intercambio.getRequestMethod() + " " + intercambio.getRequestURI().getPath());
            ultimaAutorizacion.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            enviar(intercambio, status, cuerpo);
        });
        servidor.start();
    }

    private static void enviar(HttpExchange intercambio, int status, String cuerpo) throws IOException {
        byte[] bytes = cuerpo == null ? new byte[0] : cuerpo.getBytes(StandardCharsets.UTF_8);
        if (status == 204) {
            intercambio.sendResponseHeaders(204, -1);
        } else {
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(status, bytes.length);
            intercambio.getResponseBody().write(bytes);
        }
        intercambio.close();
    }

    @Test
    void unLongPollVacioDevuelveNullYNoUnError() {
        // 204 es el caso normal: pasaron 25 s sin que nadie mandara a imprimir.
        responder("/api/impresion/agente/trabajos", 204, null);

        assertThat(new ApiCliente(baseUrl, "tok").siguienteTrabajo()).isNull();
    }

    @Test
    void unTrabajoSeParseaCompleto() {
        responder("/api/impresion/agente/trabajos", 200,
            "{\"id\":33,\"impresora\":\"XP-80C\",\"formato\":\"TICKET_80MM\",\"anchoMm\":80}");

        Trabajo trabajo = new ApiCliente(baseUrl, "tok").siguienteTrabajo();

        assertThat(trabajo).isNotNull();
        assertThat(trabajo.id()).isEqualTo(33L);
        assertThat(trabajo.impresora()).isEqualTo("XP-80C");
        assertThat(trabajo.formato()).isEqualTo("TICKET_80MM");
        assertThat(trabajo.anchoMm()).isEqualTo(80);
    }

    @Test
    void unAnchoNuloNoRompeElParseo() {
        responder("/api/impresion/agente/trabajos", 200,
            "{\"id\":1,\"impresora\":\"X\",\"formato\":\"A4\",\"anchoMm\":null}");

        assertThat(new ApiCliente(baseUrl, "tok").siguienteTrabajo().anchoMm()).isNull();
    }

    @Test
    void elTokenViajaEnElHeaderDeAutorizacion() {
        responder("/api/impresion/agente/trabajos", 204, null);

        new ApiCliente(baseUrl, "mi-token-secreto").siguienteTrabajo();

        assertThat(ultimaAutorizacion.get()).isEqualTo("Bearer mi-token-secreto");
    }

    @Test
    void elMensajeDeErrorDeLaApiLlegaAlAgente() {
        // Sin esto la clinica veria "HTTP 400" pelado en vez de saber que pasa.
        responder("/api/impresion/agente/trabajos", 400, "{\"message\":\"anchoMm: valor invalido\"}");

        assertThatThrownBy(() -> new ApiCliente(baseUrl, "tok").siguienteTrabajo())
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("anchoMm: valor invalido");
    }

    @Test
    void unCuerpoQueNoEsJsonNoTumbaAlAgente() {
        // Caso real: nginx contesta una pagina HTML de error, no el JSON de la API.
        responder("/api/impresion/agente/trabajos", 502, "<html>502 Bad Gateway</html>");

        assertThatThrownBy(() -> new ApiCliente(baseUrl, "tok").siguienteTrabajo())
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("502");
    }

    @Test
    void un403SeReconoceComoTokenRechazado() {
        responder("/api/impresion/agente/trabajos", 403, "");

        assertThatThrownBy(() -> new ApiCliente(baseUrl, "tok").siguienteTrabajo())
            .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.tokenRechazado()).isTrue());
    }

    @Test
    void siLaApiNoRespondeSeReportaComoFalloDeRed() {
        // Puerto sin nada escuchando: internet caido o VPS abajo.
        assertThatThrownBy(() -> new ApiCliente("http://127.0.0.1:1", "tok").siguienteTrabajo())
            .isInstanceOfSatisfying(ApiException.class, ex -> {
                assertThat(ex.status()).isZero();
                assertThat(ex.tokenRechazado()).isFalse();
            });
    }

    @Test
    void elPdfSeBajaComoBytesCrudos() {
        servidor.createContext("/api/impresion/agente/trabajos/7/pdf", intercambio -> {
            byte[] pdf = "%PDF-1.4 contenido".getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/pdf");
            intercambio.sendResponseHeaders(200, pdf.length);
            intercambio.getResponseBody().write(pdf);
            intercambio.close();
        });
        servidor.start();

        byte[] pdf = new ApiCliente(baseUrl, "tok").pdf(7L);

        assertThat(new String(pdf, StandardCharsets.UTF_8)).startsWith("%PDF");
    }

    @Test
    void elEmparejamientoDevuelveElTokenDelPuesto() {
        responder("/api/impresion/agente/emparejar", 200,
            "{\"puestoId\":5,\"nombre\":\"Mostrador 1\",\"token\":\"tok-nuevo\"}");

        ApiCliente.Emparejamiento par = new ApiCliente(baseUrl, null)
            .emparejar("ABCD2345", "CAJA-01", "0.1.0");

        assertThat(par.puestoId()).isEqualTo(5L);
        assertThat(par.nombre()).isEqualTo("Mostrador 1");
        assertThat(par.token()).isEqualTo("tok-nuevo");
    }

    @Test
    void reportarResultadoLlegaAlEndpointDelTrabajo() {
        responder("/api/impresion/agente/trabajos/9/resultado", 204, null);

        new ApiCliente(baseUrl, "tok").reportarResultado(9L, "OK", null);

        assertThat(rutasRecibidas).containsExactly("POST /api/impresion/agente/trabajos/9/resultado");
    }
}
