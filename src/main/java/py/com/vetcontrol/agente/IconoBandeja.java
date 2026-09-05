package py.com.vetcontrol.agente;

import java.awt.CheckboxMenuItem;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ItemEvent;
import java.awt.image.BufferedImage;
import java.awt.Desktop;

/**
 * Icono junto al reloj mientras el agente corre.
 *
 * <p>Sin esto el agente seria un proceso invisible: nadie sabria si esta vivo, y la unica forma de
 * cerrarlo seria el Administrador de tareas. Con el icono la cajera ve el estado y puede salir.
 */
public final class IconoBandeja {

    private TrayIcon icono;

    /** @return true si quedo instalado. Sin bandeja el agente igual funciona, solo que invisible. */
    public boolean instalar(String puesto, Runnable alSalir, java.nio.file.Path bitacora) {
        if (!SystemTray.isSupported()) return false;
        try {
            PopupMenu menu = new PopupMenu();

            MenuItem estado = new MenuItem("Puesto: " + puesto);
            estado.setEnabled(false);
            menu.add(estado);
            menu.addSeparator();

            MenuItem verLog = new MenuItem("Ver bitacora");
            verLog.addActionListener(e -> abrir(bitacora));
            menu.add(verLog);

            // Visible y reversible desde aca: la vinculacion lo activa sola, y sin esto la unica
            // forma de desactivarlo seria editar el registro a mano.
            if (ArranqueAutomatico.disponible()) {
                CheckboxMenuItem arranque =
                    new CheckboxMenuItem("Arrancar con Windows", ArranqueAutomatico.activado());
                arranque.addItemListener(e -> {
                    boolean encender = e.getStateChange() == ItemEvent.SELECTED;
                    boolean ok = encender
                        ? ArranqueAutomatico.activar()
                        : ArranqueAutomatico.desactivar();
                    if (!ok) arranque.setState(!encender);
                });
                menu.add(arranque);
            }

            MenuItem salir = new MenuItem("Salir");
            salir.addActionListener(e -> alSalir.run());
            menu.add(salir);

            icono = new TrayIcon(dibujar(), "MyVet - " + puesto, menu);
            icono.setImageAutoSize(true);
            SystemTray.getSystemTray().add(icono);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public void notificar(String titulo, String mensaje) {
        if (icono != null) icono.displayMessage(titulo, mensaje, TrayIcon.MessageType.INFO);
    }

    public void quitar() {
        if (icono != null) SystemTray.getSystemTray().remove(icono);
    }

    private void abrir(java.nio.file.Path archivo) {
        try {
            if (Desktop.isDesktopSupported() && java.nio.file.Files.exists(archivo)) {
                Desktop.getDesktop().open(archivo.toFile());
            }
        } catch (Exception ex) {
            VentanaVinculacion.aviso("La bitacora esta en:\n" + archivo);
        }
    }

    /** Icono dibujado en memoria: evita arrastrar un .png y que se pierda en el empaquetado. */
    private BufferedImage dibujar() {
        BufferedImage imagen = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = imagen.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x1F6FEB));
        g.fillRoundRect(0, 3, 16, 11, 4, 4);
        g.setColor(Color.WHITE);
        g.fillRect(4, 0, 8, 5);
        g.fillRect(4, 10, 8, 6);
        g.setColor(new Color(0x1F6FEB));
        g.setFont(new Font("SansSerif", Font.BOLD, 7));
        g.drawString("V", 6, 15);
        g.dispose();
        return imagen;
    }
}
