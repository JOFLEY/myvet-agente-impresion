package py.com.vetcontrol.agente;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Ventana que se abre cuando el agente todavia no esta vinculado a ningun puesto.
 *
 * <p>Existe porque la version anterior obligaba a abrir una consola y tipear
 * {@code --emparejar CODIGO}, cosa que nadie que atiende un mostrador va a hacer. El camino
 * principal es arrastrar el archivo que se descarga desde VetControl; el codigo a mano queda como
 * alternativa.
 */
public final class VentanaVinculacion {

    private VentanaVinculacion() {}

    /** @return la vinculacion elegida y si hay que activar el arranque automatico, o null si cancelo. */
    public static Resultado pedir() {
        AtomicReference<Vinculacion> elegida = new AtomicReference<>();

        JDialog dialogo = new JDialog((java.awt.Frame) null, "Agente de impresion VetControl", true);
        dialogo.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JLabel titulo = new JLabel("Vincular esta PC con VetControl");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 16f));

        JLabel ayuda = new JLabel(
            "<html>En VetControl, entra a <b>Configuraciones &gt; Impresion</b>, agrega un puesto"
                + " y descarga su archivo de vinculacion.</html>");

        JLabel zona = new JLabel(
            "<html><center>Arrastra aca el archivo<br><b>.vcagente</b></center></html>",
            SwingConstants.CENTER);
        zona.setPreferredSize(new Dimension(420, 110));
        zona.setOpaque(true);
        zona.setBackground(new Color(0xF3F5F9));
        zona.setBorder(BorderFactory.createDashedBorder(new Color(0x9AA5B1), 2f, 6f, 4f, true));
        zona.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport soporte) {
                return soporte.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport soporte) {
                if (!canImport(soporte)) return false;
                try {
                    @SuppressWarnings("unchecked")
                    List<File> archivos = (List<File>) soporte.getTransferable()
                        .getTransferData(DataFlavor.javaFileListFlavor);
                    if (archivos.isEmpty()) return false;
                    return aplicar(dialogo, elegida, archivos.get(0).toPath());
                } catch (Exception ex) {
                    error(dialogo, "No se pudo leer el archivo arrastrado.");
                    return false;
                }
            }
        });

        JButton buscar = new JButton("Buscar el archivo...");
        buscar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        buscar.addActionListener(e -> {
            JFileChooser selector = new JFileChooser();
            selector.setDialogTitle("Elegi el archivo de vinculacion");
            selector.setFileFilter(
                new FileNameExtensionFilter("Vinculacion de VetControl (*.vcagente)", "vcagente"));
            if (selector.showOpenDialog(dialogo) == JFileChooser.APPROVE_OPTION) {
                aplicar(dialogo, elegida, selector.getSelectedFile().toPath());
            }
        });

        JTextField codigo = new JTextField(12);
        JButton vincularCodigo = new JButton("Vincular");
        JLabel etiquetaCodigo = new JLabel("O pega el codigo a mano:");

        JCheckBox arranque = new JCheckBox("Arrancar junto con Windows", true);
        arranque.setEnabled(ArranqueAutomatico.disponible());

        JPanel panelCodigo = new JPanel();
        panelCodigo.setLayout(new BoxLayout(panelCodigo, BoxLayout.X_AXIS));
        panelCodigo.add(etiquetaCodigo);
        panelCodigo.add(Box.createHorizontalStrut(8));
        panelCodigo.add(codigo);
        panelCodigo.add(Box.createHorizontalStrut(8));
        panelCodigo.add(vincularCodigo);

        JPanel centro = new JPanel();
        centro.setLayout(new BoxLayout(centro, BoxLayout.Y_AXIS));
        centro.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        for (java.awt.Component c : new java.awt.Component[] {
            titulo, Box.createVerticalStrut(8), ayuda, Box.createVerticalStrut(14),
            zona, Box.createVerticalStrut(8), buscar, Box.createVerticalStrut(16),
            panelCodigo, Box.createVerticalStrut(14), arranque
        }) {
            if (c instanceof javax.swing.JComponent jc) jc.setAlignmentX(0f);
            centro.add(c);
        }

        JPanel pie = new JPanel(new GridLayout(1, 1));
        JButton salir = new JButton("Salir");
        salir.addActionListener(e -> dialogo.dispose());
        pie.add(salir);

        vincularCodigo.addActionListener(e -> {
            String valor = codigo.getText().trim();
            if (valor.isEmpty()) {
                error(dialogo, "Escribi el codigo o arrastra el archivo.");
                return;
            }
            // Sin archivo no sabemos la URL: se usa la de la configuracion vigente.
            elegida.set(new Vinculacion(null, null, valor));
            dialogo.dispose();
        });

        dialogo.setLayout(new BorderLayout());
        dialogo.add(centro, BorderLayout.CENTER);
        dialogo.add(pie, BorderLayout.SOUTH);
        dialogo.pack();
        dialogo.setLocationRelativeTo(null);
        dialogo.setVisible(true);

        Vinculacion resultado = elegida.get();
        return resultado == null ? null : new Resultado(resultado, arranque.isSelected());
    }

    private static boolean aplicar(
        JDialog dialogo,
        AtomicReference<Vinculacion> destino,
        Path archivo
    ) {
        try {
            destino.set(Vinculacion.leer(archivo));
            dialogo.dispose();
            return true;
        } catch (IllegalArgumentException ex) {
            error(dialogo, ex.getMessage());
            return false;
        }
    }

    static void error(java.awt.Component padre, String mensaje) {
        JOptionPane.showMessageDialog(padre, mensaje, "VetControl", JOptionPane.WARNING_MESSAGE);
    }

    public static void aviso(String mensaje) {
        JOptionPane.showMessageDialog(null, mensaje, "VetControl", JOptionPane.INFORMATION_MESSAGE);
    }

    public record Resultado(Vinculacion vinculacion, boolean arranqueAutomatico) {}
}
