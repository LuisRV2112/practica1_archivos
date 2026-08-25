package com.clinica.servicio;

import java.util.ArrayList;
import java.util.List;

/**
 * Resumen de una carga masiva: cuantos registros entraron, cuantos se
 * rechazaron y por que.
 *
 * La carga NO se detiene ante el primer error ni se deshace por completo: cada
 * fila se procesa por separado, las validas entran y las invalidas se reportan
 * con su numero de linea. Un archivo de 200 pacientes con una fecha mal escrita
 * en la fila 87 carga 199 y le dice al usuario exactamente que corregir, en
 * lugar de rechazarlo entero sin explicacion.
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
     * @param numeroLinea numero de linea del archivo, contando desde 1, para
     *                    que el usuario pueda ir directo a corregirla
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
