package py.com.vetcontrol.agente;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

/**
 * Lo que ve el usuario mientras el agente espera que le digan a que puesto pertenece.
 *
 * <p>No es modal a proposito: el hilo principal sigue preguntandole al servidor y cierra esta
 * ventana solo cuando la vinculacion se completa. Una ventana modal bloquearia justamente al hilo
 * que tiene que hacer el trabajo -- ya nos paso con el dialogo de arrastrar el archivo.
 *
 * <p>Es un {@link JFrame} y no un {@code JDialog} sin duenio: solo un frame recibe boton en la
 * barra de tareas, y sin ese boton la ventana quedaba enterrada detras del navegador sin ninguna
 * forma de recuperarla. Ver {@link Ventanas}.
 */
public final class VentanaEsperando {

    public enum Accion { ESPERANDO, CANCELADO, USAR_ARCHIVO }

    private final JFrame dialogo;
    private final JLabel estado;
    private final AtomicReference<Accion> accion = new AtomicReference<>(Accion.ESPERANDO);

    private VentanaEsperando(JFrame dialogo, JLabel estado) {
        this.dialogo = dialogo;
        this.estado = estado;
    }

    public static VentanaEsperando mostrar(String host, String url, Bitacora bitacora) {
        JFrame dialogo = new JFrame("Agente de impresion MyVet");
        dialogo.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        JLabel titulo = new JLabel("Vinculando esta PC con MyVet");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 16f));

        JLabel ayuda = new JLabel(
            "<html><body style='width:420px'>Se abrio MyVet en tu navegador. Ahi elegi a que"
                + " <b>puesto</b> pertenece esta PC y listo.<br><br>Si el navegador no se abrio o"
                + " todavia no iniciaste sesion, usa el boton de abajo.</body></html>");

        JLabel equipo = new JLabel("Esta PC se llama: " + host);
        equipo.setForeground(new Color(0x52606D));

        JProgressBar barra = new JProgressBar();
        barra.setIndeterminate(true);

        JLabel estado = new JLabel("Esperando que elijas el puesto...");
        estado.setForeground(new Color(0x52606D));

        JButton reabrir = new JButton("Abrir MyVet otra vez");
        reabrir.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JButton archivo = new JButton("Vincular con un archivo");
        JButton salir = new JButton("Salir");

        JPanel centro = new JPanel();
        centro.setLayout(new BoxLayout(centro, BoxLayout.Y_AXIS));
        centro.setBorder(BorderFactory.createEmptyBorder(18, 20, 12, 20));
        for (Component c : new Component[] {
            titulo, Box.createVerticalStrut(10), ayuda, Box.createVerticalStrut(14),
            equipo, Box.createVerticalStrut(14), barra, Box.createVerticalStrut(8), estado,
            Box.createVerticalStrut(14), reabrir
        }) {
            if (c instanceof javax.swing.JComponent jc) jc.setAlignmentX(0f);
            centro.add(c);
        }

        JPanel pie = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pie.add(archivo);
        pie.add(salir);

        dialogo.setLayout(new BorderLayout());
        dialogo.add(centro, BorderLayout.CENTER);
        dialogo.add(pie, BorderLayout.SOUTH);
        dialogo.pack();
        dialogo.setLocationRelativeTo(null);

        VentanaEsperando ventana = new VentanaEsperando(dialogo, estado);
        reabrir.addActionListener(e -> VinculacionNavegador.abrir(url, bitacora));
        archivo.addActionListener(e -> ventana.terminar(Accion.USAR_ARCHIVO));
        salir.addActionListener(e -> ventana.terminar(Accion.CANCELADO));

        dialogo.setVisible(true);
        // Sin esto la ventana nace detras del navegador, que acaba de quedarse con el foco.
        Ventanas.alFrente(dialogo);
        return ventana;
    }

    private void terminar(Accion nueva) {
        accion.set(nueva);
        cerrar();
    }

    public Accion accion() {
        return accion.get();
    }

    public void mensaje(String texto) {
        SwingUtilities.invokeLater(() -> estado.setText(texto));
    }

    public void cerrar() {
        SwingUtilities.invokeLater(dialogo::dispose);
    }
}
