package com.clinica.modelo;

/**
 * Sexo del paciente.
 *
 * Cada constante lleva un CODIGO NUMERICO EXPLICITO, y es ese codigo el que se
 * guarda en el archivo, no la posicion de la constante (ordinal()).
 *
 * La diferencia importa: si manana alguien reordena las constantes o inserta
 * una nueva en medio, los ordinales cambian y todos los archivos ya escritos
 * quedarian mal interpretados. Con un codigo fijo eso no puede pasar.
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

    /**
     * Reconstruye el valor a partir del codigo guardado en el archivo.
     *
     * @throws IllegalArgumentException si el codigo no corresponde a ninguno,
     *         lo que indicaria un archivo corrupto
     */
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
