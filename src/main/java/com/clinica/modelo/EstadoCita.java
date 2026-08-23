package com.clinica.modelo;

/**
 * Estados permitidos para una cita, segun el enunciado.
 *
 * Igual que {@link Sexo} y {@link TipoSangre}, cada constante guarda un codigo
 * numerico fijo en lugar de depender de ordinal(), para que reordenar el enum
 * no invalide los archivos ya escritos.
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

    /** Reconstruye el valor a partir del codigo guardado en el archivo. */
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
