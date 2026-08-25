package com.clinica.modelo;

/**
 * Estados de una cita. Código numérico fijo para no depender de ordinal().
 */
public enum EstadoCita {

    PROGRAMADA((byte) 1, "Programada"),
    ATENDIDA((byte) 2, "Atendida"),
    CANCELADA((byte) 3, "Cancelada");

    private final byte codigo;
    private final String etiqueta;

    EstadoCita(byte codigo, String etiqueta) {
        this.codigo = codigo;
        this.etiqueta = etiqueta;
    }

    public byte getCodigo() {
        return codigo;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Reconstruye el enum desde el código guardado en archivo. */
    public static EstadoCita porCodigo(byte codigo) {
        for (EstadoCita valor : values()) {
            if (valor.codigo == codigo) {
                return valor;
            }
        }
        throw new IllegalArgumentException("Codigo de estado de cita desconocido: " + codigo);
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
