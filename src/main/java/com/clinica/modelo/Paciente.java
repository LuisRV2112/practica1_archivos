package com.clinica.modelo;

import java.time.LocalDate;
import java.time.Period;

/**
 * Representa a un paciente de la clinica.
 *
 * A diferencia del medico, el identificador NO es un UUID generado por el
 * sistema: es el numero de identificacion personal que trae la persona, y el
 * enunciado exige que sea unico. Por eso es un String y lo escribe el usuario.
 */
public class Paciente {

    /** Numero de identificacion personal. Unico e inmutable una vez registrado. */
    private String identificacion;

    private String nombres;
    private String apellidos;
    private LocalDate fechaNacimiento;
    private Sexo sexo;
    private String telefono;

    /** Campo opcional segun el enunciado; puede quedar vacio. */
    private String correo;

    private TipoSangre tipoSangre;

    public Paciente() {
    }

    public Paciente(String identificacion, String nombres, String apellidos,
                    LocalDate fechaNacimiento, Sexo sexo, String telefono,
                    String correo, TipoSangre tipoSangre) {
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

    public String getNombreCompleto() {
        return nombres + " " + apellidos;
    }

    /**
     * Edad cumplida a dia de hoy. Se calcula, no se guarda: almacenarla
     * obligaria a recalcularla cada ano y quedaria desactualizada sola.
     *
     * @return la edad en anos, o -1 si no hay fecha de nacimiento
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
                + " (" + (tipoSangre == null ? "?" : tipoSangre.getEtiqueta()) + ")";
    }
}
