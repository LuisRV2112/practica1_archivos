package com.clinica.servicio;

/**
 * Se lanza cuando los datos que llegan desde la interfaz no cumplen una regla
 * de negocio (un campo obligatorio vacio, un horario invertido, etc.).
 *
 * Es una excepcion revisada (checked) a proposito: obliga a que la vista la
 * atrape y le muestre el mensaje al usuario, en lugar de dejar que el programa
 * truene con un error sin explicacion.
 *
 * Se distingue de IOException, que representa una falla al leer o escribir el
 * archivo. Son dos problemas distintos y la vista los reporta distinto.
 */
public class ExcepcionValidacion extends Exception {

    public ExcepcionValidacion(String mensaje) {
        super(mensaje);
    }
}
