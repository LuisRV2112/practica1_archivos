package com.clinica.vista;

import com.clinica.servicio.ServicioPacientes;

import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JPanel;
import javax.swing.text.MaskFormatter;
import java.awt.BorderLayout;
import java.awt.Insets;
import java.text.ParseException;
import java.time.LocalDate;

/**
 * Campo para capturar una fecha, con dos formas de llenarlo:
 *
 *   1. Escribiendola. El campo trae una MASCARA fija __/__/____ , asi que las
 *      barras ya estan puestas y el usuario solo teclea los ocho digitos. La
 *      mascara ademas impide escribir letras.
 *
 *   2. Eligiendola del calendario que abre el boton de la derecha.
 *
 * El componente NO valida la fecha: devuelve el texto tal cual para que lo
 * revise la capa de servicio. Asi la regla de que 31/02 no existe vive en un
 * solo lugar del sistema y no se duplica en cada pantalla.
 */
public class CampoFecha extends JPanel {

    private final JFormattedTextField campo;
    private final JButton btnCalendario = new JButton("...");

    public CampoFecha() {
        super(new BorderLayout(2, 0));

        campo = crearCampoConMascara();
        campo.setToolTipText("Formato dd/mm/aaaa. Use el boton para abrir el calendario.");

        btnCalendario.setMargin(new Insets(1, 6, 1, 6));
        btnCalendario.setToolTipText("Abrir calendario");
        btnCalendario.setFocusable(false);
        btnCalendario.addActionListener(e -> abrirCalendario());

        add(campo, BorderLayout.CENTER);
        add(btnCalendario, BorderLayout.EAST);

        setOpaque(false);
    }

    /**
     * Crea el campo con la mascara de fecha.
     *
     * MaskFormatter puede lanzar ParseException si el patron esta mal escrito.
     * Como el patron es una constante del codigo y no algo que venga de fuera,
     * un fallo aqui seria un error de programacion, no del usuario: por eso se
     * cae de inmediato en lugar de disimularlo.
     */
    private static JFormattedTextField crearCampoConMascara() {
        try {
            MaskFormatter mascara = new MaskFormatter("##/##/####");
            mascara.setPlaceholderCharacter('_');
            // Deja escribir aunque el campo quede incompleto; de lo contrario
            // el texto se borra solo al salir y confunde al usuario.
            mascara.setAllowsInvalid(true);
            mascara.setCommitsOnValidEdit(false);

            JFormattedTextField f = new JFormattedTextField(mascara);
            f.setColumns(9);
            return f;

        } catch (ParseException e) {
            throw new IllegalStateException("Mascara de fecha mal definida", e);
        }
    }

    private void abrirCalendario() {
        LocalDate elegida = SelectorFecha.mostrar(this, getFecha());
        if (elegida != null) {
            setFecha(elegida);
        }
    }

    // =======================================================================
    // API DEL COMPONENTE
    // =======================================================================

    /**
     * Texto capturado, listo para que lo interprete la capa de servicio.
     *
     * Si el usuario no lleno nada, la mascara deja "__/__/____". Se devuelve
     * cadena vacia en ese caso para que el servicio responda "la fecha es
     * obligatoria" en lugar de "el formato es invalido", que confundiria.
     */
    public String getTexto() {
        String texto = campo.getText();
        return (texto == null || texto.contains("_")) ? "" : texto.trim();
    }

    /** Fecha ya interpretada, o null si el campo esta vacio o mal escrito. */
    public LocalDate getFecha() {
        try {
            return LocalDate.parse(getTexto(), ServicioPacientes.FORMATO_FECHA);
        } catch (Exception noEsFechaValida) {
            return null;
        }
    }

    public void setFecha(LocalDate fecha) {
        campo.setText(ServicioPacientes.formatearFecha(fecha));
    }

    public void limpiar() {
        campo.setValue(null);
        campo.setText("");
    }

    @Override
    public void setEnabled(boolean habilitado) {
        super.setEnabled(habilitado);
        campo.setEnabled(habilitado);
        btnCalendario.setEnabled(habilitado);
    }

    /** Permite reaccionar cuando el usuario presiona Enter en el campo. */
    public void alPresionarEnter(java.awt.event.ActionListener accion) {
        campo.addActionListener(accion);
    }
}
