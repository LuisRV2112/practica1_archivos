package com.clinica.vista;

import com.clinica.modelo.Reporte;

import javax.swing.table.AbstractTableModel;

/**
 * Muestra cualquier {@link Reporte} en un JTable.
 *
 * Como el reporte ya trae sus columnas y sus filas convertidas a texto, este
 * modelo no sabe ni le importa de que reporte se trata: sirve para los quince.
 */
public class ModeloTablaReporte extends AbstractTableModel {

    private Reporte reporte = new Reporte("(sin datos)", "Columna");

    public void establecerReporte(Reporte reporte) {
        this.reporte = (reporte == null) ? new Reporte("(sin datos)", "Columna") : reporte;
        // Cambia la cantidad de columnas, no solo los datos: hay que avisar de
        // un cambio de ESTRUCTURA para que la tabla se reconstruya entera.
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
