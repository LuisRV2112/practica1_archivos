package com.clinica.modelo;

/**
 * Tipo de sangre del paciente: los ocho grupos del sistema ABO/Rh.
 *
 * Igual que {@link Sexo}, cada constante guarda un codigo numerico fijo en vez
 * de depender de ordinal(), para que reordenar el enum no invalide los archivos
 * ya escritos.
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

    /** Reconstruye el valor a partir del codigo guardado en el archivo. */
    public static TipoSangre porCodigo(byte codigo) {
        for (TipoSangre valor : values()) {
            if (valor.codigo == codigo) {
                return valor;
            }
        }
        throw new IllegalArgumentException("Codigo de tipo de sangre desconocido: " + codigo);
    }

    /** Busca por la etiqueta visible ("O+", "AB-"), como llega desde la interfaz. */
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
