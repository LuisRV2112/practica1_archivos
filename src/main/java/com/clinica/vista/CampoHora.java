package com.clinica.vista;

import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.Date;

/**
 * Campo de hora con flechas subir/bajar. Encapsula la conversión Date↔LocalTime
 * que JSpinner requiere (SpinnerDateModel es anterior a java.time).
 */
public class CampoHora extends JSpinner {

    public CampoHora() {
        this(LocalTime.of(8, 0));
    }

    public CampoHora(LocalTime inicial) {
        super(new SpinnerDateModel());

        // El editor cambia minutos por flechas, no horas.
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
