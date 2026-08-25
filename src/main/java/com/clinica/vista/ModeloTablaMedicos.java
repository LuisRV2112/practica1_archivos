package com.clinica.vista;

import com.clinica.modelo.Medico;
import com.clinica.servicio.ServicioMedicos;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Tabla de médicos sobre AbstractTableModel. Trabaja con objetos Medico reales
 * (no con Strings), de modo que seleccionar una fila devuelve el objeto
 * completo sin reconstruirlo.
 */
public class ModeloTablaMedicos extends AbstractTableModel {

    private static final String[] COLUMNAS = {
            "Codigo", "Nombres", "Apellidos", "Especialidad",
            "Telefono", "Correo", "Horario", "Estado"
    };

    private List<Medico> medicos = new ArrayList<>();

    /** Reemplaza el contenido de la tabla y avisa a Swing que se redibuje. */
    public void establecerDatos(List<Medico> nuevos) {
        this.medicos = (nuevos == null) ? new ArrayList<>() : nuevos;
        fireTableDataChanged();
    }

    /** Devuelve el medico de una fila, para editarlo o cambiarle el estado. */
    public Medico obtenerEn(int fila) {
        if (fila < 0 || fila >= medicos.size()) {
            return null;
        }
        return medicos.get(fila);
    }

    @Override
    public int getRowCount() {
        return medicos.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNAS.length;
    }

    @Override
    public String getColumnName(int columna) {
        return COLUMNAS[columna];
    }

    @Override
    public boolean isCellEditable(int fila, int columna) {
        return false; // los cambios se hacen por el formulario, no sobre la tabla
    }

    @Override
    public Object getValueAt(int fila, int columna) {
        Medico m = medicos.get(fila);

        return switch (columna) {
            // Solo los primeros 8 caracteres del UUID: suficiente para
            // identificarlo a simple vista sin ocupar media pantalla.
            case 0 -> m.getId().toString().substring(0, 8);
            case 1 -> m.getNombres();
            case 2 -> m.getApellidos();
            case 3 -> m.getEspecialidad();
            case 4 -> m.getTelefono();
            case 5 -> m.getCorreo();
            case 6 -> ServicioMedicos.formatearHora(m.getHoraInicio())
                    + " - " + ServicioMedicos.formatearHora(m.getHoraFin());
            case 7 -> m.isActivo() ? "Activo" : "Inactivo";
            default -> "";
        };
    }
}
