package com.clinica.servicio;

import java.util.ArrayList;
import java.util.List;

/**
 * Resumen de una carga masiva: insertados, rechazados y errores por línea.
 * La carga no se detiene ante el primer error: cada fila se procesa por
 * separado; las válidas entran y las inválidas se reportan con su número de
 * línea para que el usuario sepa exactamente qué corregir.
 */
public class ResultadoCarga {

    private final String entidad;
    private int insertados;
    private final List<String> errores = new ArrayList<>();

    public ResultadoCarga(String entidad) {
        this.entidad = entidad;
    }

    public void contarInsertado() {
        insertados++;
    }

    /**
     * Registra un error con número de línea (desde 1) para que el usuario vaya
     * directo a corregirlo.
     */
    public void agregarError(int numeroLinea, String motivo) {
        errores.add("Linea " + numeroLinea + ": " + motivo);
    }

    public String getEntidad() {
        return entidad;
    }

    public int getInsertados() {
        return insertados;
    }

    public int getRechazados() {
        return errores.size();
    }

    public List<String> getErrores() {
        return errores;
    }

    public boolean huboErrores() {
        return !errores.isEmpty();
    }

    /** Resumen legible para mostrar al usuario. */
    public String resumen() {
        StringBuilder texto = new StringBuilder();
        texto.append("Carga de ").append(entidad).append(":\n\n")
             .append("  Registros cargados:   ").append(insertados).append('\n')
             .append("  Registros rechazados: ").append(getRechazados()).append('\n');

        if (huboErrores()) {
            texto.append("\nDetalle de los rechazos:\n");
            for (String error : errores) {
                texto.append("  - ").append(error).append('\n');
            }
        }
        return texto.toString();
    }
}
