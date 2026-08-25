package com.clinica.servicio;

/**
 * Excepción revisada para errores de negocio (campo obligatorio vacío, horario
 * invertido, etc.). Checked a propósito para que la vista la atrape y le muestre
 * el mensaje al usuario. Se distingue de IOException (falla de archivo).
 */
public class ExcepcionValidacion extends Exception {

    public ExcepcionValidacion(String mensaje) {
        super(mensaje);
    }
}
