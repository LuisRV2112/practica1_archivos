package com.clinica.vista;

import com.clinica.modelo.Cita;
import com.clinica.modelo.Medico;
import com.clinica.modelo.Paciente;
import com.clinica.servicio.ServicioMedicos;
import com.clinica.servicio.ServicioPacientes;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapta una lista de citas al formato que espera JTable.
 *
 * La cita guarda solo referencias (identificacion del paciente, UUID del
 * medico), asi que para mostrarla hay que resolver los nombres. Esos nombres
 * llegan en dos mapas que arma el panel ANTES de refrescar la tabla: si se
 * consultara el archivo dentro de getValueAt() se releeria el disco una vez por
 * celda visible, que es justo lo que no se debe hacer.
 */
public class ModeloTablaCitas extends AbstractTableModel {

    private static final String[] COLUMNAS = {
            "Codigo", "Fecha", "Hora", "Paciente", "Medico",
            "Especialidad", "Motivo", "Estado", "Observaciones"
    };

    private List<Cita> citas = new ArrayList<>();
    private Map<String, Paciente> pacientes = Map.of();
    private Map<java.util.UUID, Medico> medicos = Map.of();

    /**
     * Reemplaza el contenido de la tabla.
     *
     * @param nuevas    citas a mostrar
     * @param pacientes mapa identificacion -> paciente, para resolver nombres
     * @param medicos   mapa UUID -> medico, para resolver nombres
     */
    public void establecerDatos(List<Cita> nuevas,
                               Map<String, Paciente> pacientes,
                               Map<java.util.UUID, Medico> medicos) {
        this.citas = (nuevas == null) ? new ArrayList<>() : nuevas;
        this.pacientes = (pacientes == null) ? Map.of() : pacientes;
        this.medicos = (medicos == null) ? Map.of() : medicos;
        fireTableDataChanged();
    }

    public Cita obtenerEn(int fila) {
        if (fila < 0 || fila >= citas.size()) {
            return null;
        }
        return citas.get(fila);
    }

    @Override
    public int getRowCount() {
        return citas.size();
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
        Cita c = citas.get(fila);
        Paciente paciente = pacientes.get(c.getIdentificacionPaciente());
        Medico medico = medicos.get(c.getIdMedico());

        return switch (columna) {
            case 0 -> c.getId().toString().substring(0, 8);
            case 1 -> ServicioPacientes.formatearFecha(c.getFecha());
            case 2 -> ServicioMedicos.formatearHora(c.getHoraInicio());
            // Si la referencia no resuelve, se muestra el identificador crudo:
            // es una senal visible de que algo quedo inconsistente.
            case 3 -> (paciente == null)
                    ? "(?) " + c.getIdentificacionPaciente()
                    : paciente.getNombreCompleto();
            case 4 -> (medico == null) ? "(?)" : medico.getNombreCompleto();
            case 5 -> (medico == null) ? "" : medico.getEspecialidad();
            case 6 -> c.getMotivo();
            case 7 -> c.getEstado() == null ? "" : c.getEstado().getEtiqueta();
            case 8 -> c.getObservaciones();
            default -> "";
        };
    }
}
