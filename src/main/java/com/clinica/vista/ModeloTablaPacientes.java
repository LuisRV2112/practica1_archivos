package com.clinica.vista;

import com.clinica.modelo.Paciente;
import com.clinica.servicio.ServicioPacientes;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapta una lista de pacientes al formato que espera JTable.
 * Mismo enfoque que ModeloTablaMedicos: se trabaja sobre los objetos reales,
 * de modo que al seleccionar una fila se recupera el paciente completo.
 */
public class ModeloTablaPacientes extends AbstractTableModel {

    private static final String[] COLUMNAS = {
            "Identificacion", "Nombres", "Apellidos", "Nacimiento",
            "Edad", "Sexo", "Telefono", "Correo", "Sangre"
    };

    private List<Paciente> pacientes = new ArrayList<>();

    public void establecerDatos(List<Paciente> nuevos) {
        this.pacientes = (nuevos == null) ? new ArrayList<>() : nuevos;
        fireTableDataChanged();
    }

    public Paciente obtenerEn(int fila) {
        if (fila < 0 || fila >= pacientes.size()) {
            return null;
        }
        return pacientes.get(fila);
    }

    @Override
    public int getRowCount() {
        return pacientes.size();
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
        return false;
    }

    @Override
    public Object getValueAt(int fila, int columna) {
        Paciente p = pacientes.get(fila);

        return switch (columna) {
            case 0 -> p.getIdentificacion();
            case 1 -> p.getNombres();
            case 2 -> p.getApellidos();
            case 3 -> ServicioPacientes.formatearFecha(p.getFechaNacimiento());
            case 4 -> p.getEdad() < 0 ? "" : String.valueOf(p.getEdad());
            case 5 -> p.getSexo() == null ? "" : p.getSexo().getEtiqueta();
            case 6 -> p.getTelefono();
            case 7 -> p.getCorreo();
            case 8 -> p.getTipoSangre() == null ? "" : p.getTipoSangre().getEtiqueta();
            default -> "";
        };
    }
}
