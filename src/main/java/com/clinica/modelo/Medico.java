package com.clinica.modelo;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Representa a un medico de la clinica.
 *
 * Esta clase es un objeto de dominio puro: no sabe nada de archivos ni de la
 * interfaz grafica. Esa separacion es intencional (ver objetivo especifico 5
 * del enunciado): el modelo solo transporta datos entre las capas.
 */
public class Medico {

    /** Identificador unico. Se genera automaticamente al registrar el medico. */
    private UUID id;

    private String nombres;
    private String apellidos;
    private String especialidad;
    private String telefono;

    /** Campo opcional segun el enunciado; puede quedar vacio. */
    private String correo;

    private LocalTime horaInicio;
    private LocalTime horaFin;

    /**
     * Estado del medico dentro de la clinica: activo o inactivo.
     * OJO: no confundir con el byte de "registro vivo / registro borrado" que
     * maneja la capa de persistencia. Son dos conceptos distintos.
     */
    private boolean activo;

    public Medico() {
    }

    public Medico(UUID id, String nombres, String apellidos, String especialidad,
                  String telefono, String correo, LocalTime horaInicio,
                  LocalTime horaFin, boolean activo) {
        this.id = id;
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.especialidad = especialidad;
        this.telefono = telefono;
        this.correo = correo;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.activo = activo;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public String getEspecialidad() {
        return especialidad;
    }

    public void setEspecialidad(String especialidad) {
        this.especialidad = especialidad;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public void setHoraFin(LocalTime horaFin) {
        this.horaFin = horaFin;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    /** Nombre completo, util para mostrarlo en las tablas de Swing. */
    public String getNombreCompleto() {
        return nombres + " " + apellidos;
    }

    @Override
    public String toString() {
        return getNombreCompleto() + " (" + especialidad + ") "
                + horaInicio + "-" + horaFin
                + (activo ? " [ACTIVO]" : " [INACTIVO]");
    }
}
