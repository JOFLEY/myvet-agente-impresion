package py.com.vetcontrol.agente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

/**
 * El escalado del driver es el riesgo #1 de toda la funcionalidad: si el papel no mide exactamente
 * lo que mide el PDF, el ticket sale deformado o recortado en la termica. Estos tests fijan esa
 * geometria.
 *
 * <p>Las medidas de referencia salen de los PDF reales que genera el servidor: 227x348 pt para un
 * ticket de 80 mm y 164x321 pt para uno de 58 mm.
 */
class ImpresorPdfTest {

    private static final PDRectangle TICKET_80MM = new PDRectangle(227f, 348f);
    private static final PDRectangle TICKET_58MM = new PDRectangle(164f, 815f);

    @Test
    void elPapelMideExactamenteLoQueMideElPdf() {
        Paper papel = ImpresorPdf.formatoDe(TICKET_80MM).getPaper();

        assertThat(papel.getWidth()).isEqualTo(227.0);
        assertThat(papel.getHeight()).isEqualTo(348.0);
    }

    @Test
    void noQuedaNingunMargen() {
        // En un rollo continuo no hay margen que respetar, y cualquier margen desplaza el contenido.
        Paper papel = ImpresorPdf.formatoDe(TICKET_58MM).getPaper();

        assertThat(papel.getImageableX()).isZero();
        assertThat(papel.getImageableY()).isZero();
        assertThat(papel.getImageableWidth()).isEqualTo(papel.getWidth());
        assertThat(papel.getImageableHeight()).isEqualTo(papel.getHeight());
    }

    @Test
    void laOrientacionSeFuerzaAVertical() {
        // Un ticket largo y angosto puede hacer que el driver "adivine" horizontal.
        assertThat(ImpresorPdf.formatoDe(TICKET_80MM).getOrientation()).isEqualTo(PageFormat.PORTRAIT);
        assertThat(ImpresorPdf.formatoDe(TICKET_58MM).getOrientation()).isEqualTo(PageFormat.PORTRAIT);
    }

    @Test
    void unTicketLargoConservaSuAlturaReal() {
        // 815 pt son ~288 mm: el ticket de 20 items no se puede recortar a un A4 ni a un alto fijo.
        Paper papel = ImpresorPdf.formatoDe(TICKET_58MM).getPaper();

        assertThat(papel.getHeight()).isEqualTo(815.0);
        assertThat(ImpresorPdf.aMilimetros(papel.getHeight())).isCloseTo(287.6, within(0.5));
    }

    @Test
    void losPuntosSeConviertenBienAMilimetros() {
        // 72 puntos = 1 pulgada = 25.4 mm. Los anchos nominales tienen que dar ~80 y ~58.
        assertThat(ImpresorPdf.aMilimetros(227)).isCloseTo(80.1, within(0.2));
        assertThat(ImpresorPdf.aMilimetros(164)).isCloseTo(57.9, within(0.2));
    }

    @Test
    void cadaPaginaLlevaSuPropioFormato() throws Exception {
        // Si el Book reusara un solo formato, una pagina heredaria el tamano de otra.
        try (PDDocument documento = new PDDocument()) {
            documento.addPage(new PDPage(TICKET_80MM));
            documento.addPage(new PDPage(PDRectangle.A4));

            Book libro = ImpresorPdf.construirLibro(documento);

            assertThat(libro.getNumberOfPages()).isEqualTo(2);
            assertThat(libro.getPageFormat(0).getPaper().getWidth()).isEqualTo(227.0);
            assertThat(libro.getPageFormat(1).getPaper().getWidth())
                .isCloseTo(PDRectangle.A4.getWidth(), within(0.01));
        }
    }

    @Test
    void elLogDescribeLasMedidasEnMilimetros() throws Exception {
        // Lo lee una persona que tiene un rollo de 80 mm en la mano, no puntos PostScript.
        try (PDDocument documento = new PDDocument()) {
            documento.addPage(new PDPage(TICKET_80MM));

            // Locale.ROOT: el separador decimal no depende del idioma de la PC de la clinica.
            assertThat(ImpresorPdf.describirPaginas(documento)).containsExactly("80.1x122.8mm");
        }
    }
}
