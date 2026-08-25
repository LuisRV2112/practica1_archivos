package com.clinica.vista;

import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.Date;

/**
 * Campo para capturar una hora, con flechas para subir y bajar.
 *
 * Se apoya en JSpinner con un editor de fecha limitado a "HH:mm". La ventaja
 * frente a un campo de texto libre es que el componente NO PERMITE construir
 * una hora invalida: al llegar a 23:59 vuelve a 00:00 y no acepta letras. Toda
 * una familia de errores de captura desaparece de raiz.
 *
 * Internamente JSpinner trabaja con java.util.Date porque SpinnerDateModel es
 * anterior a java.time. Esta clase encapsula esa conversion para que el resto
 * del sistema siga hablando unicamente de LocalTime.
 */
public class CampoHora extends JSpinner {

    public CampoHora() {
        this(LocalTime.of(8, 0));
    }

    public CampoHora(LocalTime inicial) {
        super(new SpinnerDateModel());

        // El campo de minutos es el que cambian las flechas: subir de 30 pasa a
        // 31, no a la hora siguiente. Es lo que se espera al agendar.
        setEditor(new JSpinner.DateEditor(this, "HH:mm"));
        setHora(inicial);

        setToolTipText("Use las flechas o escriba la hora en formato 24 horas");
    }

    /** Hora seleccionada. Nunca es invalida: el componente no lo permite. */
    public LocalTime getHora() {
        Calendar calendario = Calendar.getInstance();
        calendario.setTime((Date) getValue());

        return LocalTime.of(
                calendario.get(Calendar.HOUR_OF_DAY),
                calendario.get(Calendar.MINUTE));
    }

    public void setHora(LocalTime hora) {
        LocalTime valor = (hora == null) ? LocalTime.of(8, 0) : hora;

        Calendar calendario = Calendar.getInstance();
        calendario.set(Calendar.HOUR_OF_DAY, valor.getHour());
        calendario.set(Calendar.MINUTE, valor.getMinute());
        calendario.set(Calendar.SECOND, 0);
        calendario.set(Calendar.MILLISECOND, 0);

        setValue(calendario.getTime());
    }

    /** Texto "HH:mm", para pasarlo a la capa de servicio sin cambiar su firma. */
    public String getTexto() {
        return com.clinica.servicio.ServicioMedicos.formatearHora(getHora());
    }
}
