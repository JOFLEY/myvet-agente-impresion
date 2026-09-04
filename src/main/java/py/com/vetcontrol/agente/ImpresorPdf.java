package py.com.vetcontrol.agente;

import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterJob;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.print.PrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.OrientationRequested;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.printing.PDFPrintable;
import org.apache.pdfbox.printing.Scaling;

/**
 * Imprime el PDF del ticket por el driver de Windows.
 *
 * <p><b>Este es el punto mas delicado de toda la funcionalidad.</b> Los drivers termicos suelen
 * venir configurados en "ajustar a la pagina", y si dejamos que el driver decida el escalado el
 * ticket sale deformado o recortado. Por eso:
 *
 * <ul>
 *   <li>{@link Scaling#ACTUAL_SIZE}: cero escalado, 1 punto del PDF = 1 punto en el papel.</li>
 *   <li>El {@link Paper} se construye con las medidas del {@code MediaBox} del propio PDF, que ya
 *       viene exacto del servidor (227x348 pt para 80 mm, 164x321 pt para 58 mm, creciendo con la
 *       cantidad de items).</li>
 *   <li>Area imprimible sin margenes: en un rollo continuo no hay margen que respetar.</li>
 *   <li>Un {@link Book} con el formato de cada pagina, para que una pagina no herede el tamano de
 *       otra.</li>
 * </ul>
 *
 * <p>Tanto el MediaBox del PDF como el {@link Paper} de AWT se miden en puntos (1/72 de pulgada),
 * asi que la conversion es directa, sin factores.
 */
public final class ImpresorPdf {

    private final boolean dryRun;
    private final Bitacora bitacora;

    public ImpresorPdf(boolean dryRun, Bitacora bitacora) {
        this.dryRun = dryRun;
        this.bitacora = bitacora;
    }

    /** @return descripcion de lo que se imprimio (o de lo que se habria impreso en dry-run). */
    public String imprimir(byte[] pdf, PrintService destino, String nombreTrabajo) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            List<String> medidas = describirPaginas(documento);
            String detalle = "impresora='" + destino.getName() + "' paginas=" + documento.getNumberOfPages()
                + " tamanos=[" + String.join(", ", medidas) + "] escalado=ACTUAL_SIZE margenes=0";

            if (dryRun) {
                bitacora.info("DRY-RUN: no se envia nada a la impresora. Habria impreso: " + detalle);
                return detalle;
            }

            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintService(destino);
            job.setJobName(nombreTrabajo);
            job.setPageable(construirLibro(documento));
            job.print(atributos(nombreTrabajo));

            bitacora.info("Impreso: " + detalle);
            return detalle;
        } catch (java.awt.print.PrinterException ex) {
            throw new IOException("El driver rechazo el trabajo: " + ex.getMessage(), ex);
        }
    }

    static Book construirLibro(PDDocument documento) {
        Book libro = new Book();
        PDFPrintable imprimible = new PDFPrintable(documento, Scaling.ACTUAL_SIZE);
        for (PDPage pagina : documento.getPages()) {
            libro.append(imprimible, formatoDe(pagina.getMediaBox()));
        }
        return libro;
    }

    /**
     * Papel del tamano exacto del PDF y sin margenes. Es la pieza que evita que el driver reescale.
     */
    static PageFormat formatoDe(PDRectangle mediaBox) {
        double ancho = mediaBox.getWidth();
        double alto = mediaBox.getHeight();

        Paper papel = new Paper();
        papel.setSize(ancho, alto);
        papel.setImageableArea(0, 0, ancho, alto);

        PageFormat formato = new PageFormat();
        formato.setPaper(papel);
        formato.setOrientation(PageFormat.PORTRAIT);
        return formato;
    }

    private PrintRequestAttributeSet atributos(String nombreTrabajo) {
        PrintRequestAttributeSet atributos = new HashPrintRequestAttributeSet();
        atributos.add(new JobName(nombreTrabajo, null));
        atributos.add(new Copies(1));
        atributos.add(OrientationRequested.PORTRAIT);
        return atributos;
    }

    /**
     * Medidas en mm para que el log sea legible por una persona que mira un rollo de 80 mm.
     *
     * <p>{@code Locale.ROOT} explicito: sin el, el separador decimal sale coma o punto segun el
     * idioma de la PC de la clinica y el mismo log se lee distinto en cada maquina.
     */
    static List<String> describirPaginas(PDDocument documento) {
        List<String> medidas = new ArrayList<>();
        for (PDPage pagina : documento.getPages()) {
            PDRectangle caja = pagina.getMediaBox();
            medidas.add(String.format(java.util.Locale.ROOT, "%.1fx%.1fmm",
                aMilimetros(caja.getWidth()), aMilimetros(caja.getHeight())));
        }
        return medidas;
    }

    static double aMilimetros(double puntos) {
        return puntos / 72.0 * 25.4;
    }
}
