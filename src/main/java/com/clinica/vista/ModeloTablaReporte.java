package com.clinica.vista;

import com.clinica.modelo.Reporte;

import javax.swing.table.AbstractTableModel;

/**
 * Tabla genérica para cualquier {@link Reporte}. El reporte ya trae columnas y
 * filas como texto; este modelo no necesita saber de qué reporte se trata.
 */
public class ModeloTablaReporte extends AbstractTableModel {

    private Reporte reporte = new Reporte("(sin datos)", "Columna");

    public void establecerReporte(Reporte reporte) {
        this.reporte = (reporte == null) ? new Reporte("(sin datos)", "Columna") : reporte;
        // fireTableStructureChanged: cambia el número de columnas, no solo datos.
        fireTableStructureChanged();
    }

    public Reporte getReporte() {
        return reporte;
    }

    @Override
    public int getRowCount() {
        return reporte.cantidadFilas();
    }

    @Override
    public int getColumnCount() {
        return reporte.getColumnas().length;
    }

    @Override
    public String getColumnName(int columna) {
        return reporte.getColumnas()[columna];
    }

    @Override
    public boolean isCellEditable(int fila, int columna) {
        return false;
    }

    @Override
    public Object getValueAt(int fila, int columna) {
        return reporte.getFilas().get(fila)[columna];
    }
}
