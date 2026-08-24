package com.clinica.modelo;

/**
 * Tipo de operacion que se registra en la bitacora.
 *
 * Igual que los demas enums del sistema, cada constante guarda un codigo
 * numerico fijo en lugar de depender de ordinal().
 */
public enum TipoOperacion {

    CREACION((byte) 1, "Creacion"),
    ACTUALIZACION((byte) 2, "Actualizacion"),
    ELIMINACION((byte) 3, "Eliminacion"),
    CAMBIO_ESTADO((byte) 4, "Cambio de estado"),
    EXPORTACION((byte) 5, "Exportacion");

    private final byte codigo;
    private final String etiqueta;

    TipoOperacion(byte codigo, String etiqueta) {
        this.codigo = codigo;
        this.etiqueta = etiqueta;
    }

    public byte getCodigo() {
        return codigo;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public static TipoOperacion porCodigo(byte codigo) {
        for (TipoOperacion valor : values()) {
            if (valor.codigo == codigo) {
                return valor;
            }
        }
        throw new IllegalArgumentException("Codigo de operacion desconocido: " + codigo);
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
