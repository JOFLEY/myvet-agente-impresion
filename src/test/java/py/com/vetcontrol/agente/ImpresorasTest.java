package py.com.vetcontrol.agente;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImpresorasTest {

    @Test
    void elNombreMatcheaIgnorandoMayusculasYEspacios() {
        // El nombre viaja al servidor y vuelve, y un driver puede reportarlo con distinto casing
        // entre arranques de Windows.
        assertThat(Impresoras.coincide("XP-80C", "XP-80C")).isTrue();
        assertThat(Impresoras.coincide("XP-80C", "xp-80c")).isTrue();
        assertThat(Impresoras.coincide("  XP-80C  ", "XP-80C")).isTrue();
        assertThat(Impresoras.coincide("XP-80C", "  XP-80C ")).isTrue();
    }

    @Test
    void impresorasDistintasNoSeConfunden() {
        assertThat(Impresoras.coincide("XP-80C", "XP-58C")).isFalse();
        assertThat(Impresoras.coincide("Microsoft Print to PDF", "Microsoft XPS Document Writer")).isFalse();
    }

    @Test
    void unNombreVacioNoMatcheaConNada() {
        // Si matcheara, un puesto sin impresora configurada agarraria la primera de la lista y
        // el ticket saldria por una impresora cualquiera.
        assertThat(Impresoras.coincide("XP-80C", null)).isFalse();
        assertThat(Impresoras.coincide("XP-80C", "")).isFalse();
        assertThat(Impresoras.coincide("XP-80C", "   ")).isFalse();
        assertThat(Impresoras.coincide(null, "XP-80C")).isFalse();
    }

    @Test
    void enEstaMaquinaSeEnumeranLasImpresorasInstaladas() {
        // No se puede afirmar cuales hay, pero si que la enumeracion no explota y no trae nulos:
        // es la llamada de la que depende todo el reporte al servidor.
        assertThat(Impresoras.nombres()).allSatisfy(nombre -> assertThat(nombre).isNotNull());
    }
}
