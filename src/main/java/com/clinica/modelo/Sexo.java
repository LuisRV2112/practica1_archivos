package com.clinica.modelo;

/**
 * Sexo del paciente. Cada constante guarda un código numérico fijo para no
 * depender de ordinal() — reordenar el enum no invalidaría los archivos escritos.
 */
public enum Sexo {

    MASCULINO((byte) 1, "Masculino"),
    FEMENINO((byte) 2, "Femenino");

    private final byte codigo;
    private final String etiqueta;

    Sexo(byte codigo, String etiqueta) {
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
    public static Sexo porCodigo(byte codigo) {
        for (Sexo valor : values()) {
            if (valor.codigo == codigo) {
                return valor;
            }
        }
        throw new IllegalArgumentException("Codigo de sexo desconocido: " + codigo);
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
