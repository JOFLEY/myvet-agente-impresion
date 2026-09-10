package py.com.vetcontrol.agente;

import java.awt.Window;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

/**
 * Hace que las ventanas del agente se VEAN.
 *
 * <p>Existe por un sintoma que se diagnostico mal dos veces: "instalo el agente, le doy doble clic
 * y no se abre nada; si lo ejecuto como administrador si". No era el antivirus ni eran los
 * permisos. Todas las ventanas del agente eran {@code JDialog} sin duenio, y en Windows eso
 * significa dos cosas:
 *
 * <ul>
 *   <li>no reciben boton en la barra de tareas, asi que si quedan detras de otra ventana no hay
 *       forma de encontrarlas;
 *   <li>no pueden traerse al frente solas: Windows le niega el primer plano a un proceso que no es
 *       el que el usuario esta mirando.
 * </ul>
 *
 * <p>Y el primer arranque abria el navegador ANTES de mostrar su ventana, con lo que el navegador
 * se quedaba con el foco y la ventana del agente nacia enterrada detras. Ejecutar como
 * administrador "lo arreglaba" de casualidad: despues del cartel de UAC, Windows le concede el
 * primer plano al proceso recien elevado.
 */
public final class Ventanas {

    private Ventanas() {}

    /**
     * Trae una ventana al frente de verdad.
     *
     * <p>{@code setAlwaysOnTop} es lo que gana la pelea de z-order; {@code toFront} y
     * {@code requestFocus} solos no alcanzan porque Windows los ignora si el proceso no tiene
     * derecho de primer plano.
     */
    public static void alFrente(Window ventana) {
        try {
            ventana.setAlwaysOnTop(true);
        } catch (SecurityException ex) {
            // Algunos entornos restringidos lo prohiben: se sigue igual, la ventana existe.
        }
        ventana.toFront();
        ventana.requestFocus();
    }

    /**
     * Cartel de aviso que aparece SIEMPRE encima.
     *
     * <p>{@code JOptionPane.showMessageDialog(null, ...)} arma un dialogo sin duenio, que es
     * exactamente el que se perdia detras del navegador.
     */
    public static void aviso(String mensaje) {
        JDialog dialogo = new JOptionPane(mensaje, JOptionPane.INFORMATION_MESSAGE)
            .createDialog("MyVet");
        dialogo.setAlwaysOnTop(true);
        dialogo.setModal(true);
        try {
            dialogo.setVisible(true);
        } finally {
            dialogo.dispose();
        }
    }
}
