package com.clinica.modelo;

import java.time.LocalTime;
import java.util.UUID;

/** Dominio puro: transporta datos entre capas. Sin conocimiento de archivos ni UI. */
public class Medico {

    /** UUID generado por el sistema. */
    private UUID id;

    private String nombres;
    private String apellidos;
    private String especialidad;
    private String telefono;

    /** Opcional, puede quedar vacío. */
    private String correo;

    private LocalTime horaInicio;
    private LocalTime horaFin;

    /** Activo/inactivo en la clínica. No confundir con el byte vivo/borrado de persistencia. */
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

    /** Nombre + apellidos para mostrar en tablas. */
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
