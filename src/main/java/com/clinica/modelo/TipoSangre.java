package com.clinica.modelo;

/**
 * Tipo de sangre (8 grupos ABO/Rh). Código numérico fijo para no depender de ordinal().
 */
public enum TipoSangre {

    O_NEGATIVO((byte) 1, "O-"),
    O_POSITIVO((byte) 2, "O+"),
    A_NEGATIVO((byte) 3, "A-"),
    A_POSITIVO((byte) 4, "A+"),
    B_NEGATIVO((byte) 5, "B-"),
    B_POSITIVO((byte) 6, "B+"),
    AB_NEGATIVO((byte) 7, "AB-"),
    AB_POSITIVO((byte) 8, "AB+");

    private final byte codigo;
    private final String etiqueta;

    TipoSangre(byte codigo, String etiqueta) {
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
    public static TipoSangre porCodigo(byte codigo) {
        for (TipoSangre valor : values()) {
            if (valor.codigo == codigo) {
                return valor;
            }
        }
        throw new IllegalArgumentException("Codigo de tipo de sangre desconocido: " + codigo);
    }

    /** Busca por etiqueta visible ("O+", "AB-"), como llega desde la UI. */
    public static TipoSangre porEtiqueta(String etiqueta) {
        for (TipoSangre valor : values()) {
            if (valor.etiqueta.equalsIgnoreCase(etiqueta)) {
                return valor;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
