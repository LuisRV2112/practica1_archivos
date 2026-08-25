package com.clinica.modelo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Cita médica. Relaciona paciente (identificación) con medico (UUID).
 * Solo guarda referencias, no copias de datos.
 */
public class Cita {

    /** UUID generado por el sistema. */
    private UUID id;

    /** Identificación del paciente (referencia). */
    private String identificacionPaciente;

    /** UUID del medico (referencia). */
    private UUID idMedico;

    private LocalDate fecha;
    private LocalTime horaInicio;
    private String motivo;
    private EstadoCita estado;
    private String observaciones;

    public Cita() {
    }

    public Cita(UUID id, String identificacionPaciente, UUID idMedico, LocalDate fecha,
                LocalTime horaInicio, String motivo, EstadoCita estado, String observaciones) {
        this.id = id;
        this.identificacionPaciente = identificacionPaciente;
        this.idMedico = idMedico;
        this.fecha = fecha;
        this.horaInicio = horaInicio;
        this.motivo = motivo;
        this.estado = estado;
        this.observaciones = observaciones;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getIdentificacionPaciente() {
        return identificacionPaciente;
    }

    public void setIdentificacionPaciente(String identificacionPaciente) {
        this.identificacionPaciente = identificacionPaciente;
    }

    public UUID getIdMedico() {
        return idMedico;
    }

    public void setIdMedico(UUID idMedico) {
        this.idMedico = idMedico;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public EstadoCita getEstado() {
        return estado;
    }

    public void setEstado(EstadoCita estado) {
        this.estado = estado;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    /** Fecha + hora combinadas. Útil para comparar citas entre sí. */
    public LocalDateTime getInicio() {
        if (fecha == null || horaInicio == null) {
            return null;
        }
        return LocalDateTime.of(fecha, horaInicio);
    }

    @Override
    public String toString() {
        return fecha + " " + horaInicio + " - " + motivo + " [" + estado + "]";
    }
}
