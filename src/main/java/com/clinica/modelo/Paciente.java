package com.clinica.modelo;

import java.time.LocalDate;
import java.time.Period;

/**
 * Dominio puro: transporta datos entre capas. El identificador es el número
 * de identidad personal (String), no un UUID generado.
 */
public class Paciente {

    /** Número de identidad personal. Único e inmutable. */
    private String identificacion;

    private String nombres;
    private String apellidos;
    private LocalDate fechaNacimiento;
    private Sexo sexo;
    private String telefono;

    /** Opcional, puede quedar vacío. */
    private String correo;

    private TipoSangre tipoSangre;

    /**
     * Activo o dado de baja. Borrado lógico de dominio: el expediente se conserva
     * para que sus citas históricas no queden huérfanas.
     */
    private boolean activo = true;

    public Paciente() {
    }

    /** Constructor para paciente nuevo (nace activo). */
    public Paciente(String identificacion, String nombres, String apellidos,
                    LocalDate fechaNacimiento, Sexo sexo, String telefono,
                    String correo, TipoSangre tipoSangre) {
        this(identificacion, nombres, apellidos, fechaNacimiento, sexo,
                telefono, correo, tipoSangre, true);
    }

    public Paciente(String identificacion, String nombres, String apellidos,
                    LocalDate fechaNacimiento, Sexo sexo, String telefono,
                    String correo, TipoSangre tipoSangre, boolean activo) {
        this.activo = activo;
        this.identificacion = identificacion;
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.fechaNacimiento = fechaNacimiento;
        this.sexo = sexo;
        this.telefono = telefono;
        this.correo = correo;
        this.tipoSangre = tipoSangre;
    }

    public String getIdentificacion() {
        return identificacion;
    }

    public void setIdentificacion(String identificacion) {
        this.identificacion = identificacion;
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

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(LocalDate fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }

    public Sexo getSexo() {
        return sexo;
    }

    public void setSexo(Sexo sexo) {
        this.sexo = sexo;
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

    public TipoSangre getTipoSangre() {
        return tipoSangre;
    }

    public void setTipoSangre(TipoSangre tipoSangre) {
        this.tipoSangre = tipoSangre;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public String getNombreCompleto() {
        return nombres + " " + apellidos;
    }

    /**
     * Edad calculada a partir de la fecha de nacimiento. No se almacena para
     * evitar tener que recalcularla cada año.
     * @return edad en años, o -1 si no hay fecha
     */
    public int getEdad() {
        if (fechaNacimiento == null) {
            return -1;
        }
        return Period.between(fechaNacimiento, LocalDate.now()).getYears();
    }

    @Override
    public String toString() {
        return identificacion + " - " + getNombreCompleto()
                + " (" + (tipoSangre == null ? "?" : tipoSangre.getEtiqueta()) + ")"
                + (activo ? "" : " [DE BAJA]");
    }
}