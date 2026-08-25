package com.clinica.modelo;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Línea de bitácora: registro append-only de quién hizo qué, cuándo y en qué módulo.
 */
public class EntradaBitacora {

    private UUID id;
    private LocalDateTime momento;

    /** Módulo afectado: "Medicos", "Pacientes", "Citas", "Reportes". */
    private String modulo;

    private TipoOperacion operacion;

    /** Descripción legible de lo ocurrido. */
    private String detalle;

    public EntradaBitacora() {
    }

    public EntradaBitacora(UUID id, LocalDateTime momento, String modulo,
                           TipoOperacion operacion, String detalle) {
        this.id = id;
        this.momento = momento;
        this.modulo = modulo;
        this.operacion = operacion;
        this.detalle = detalle;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDateTime getMomento() {
        return momento;
    }

    public void setMomento(LocalDateTime momento) {
        this.momento = momento;
    }

    public String getModulo() {
        return modulo;
    }

    public void setModulo(String modulo) {
        this.modulo = modulo;
    }

    public TipoOperacion getOperacion() {
        return operacion;
    }

    public void setOperacion(TipoOperacion operacion) {
        this.operacion = operacion;
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }

    @Override
    public String toString() {
        return momento + " [" + modulo + "] " + operacion + ": " + detalle;
    }
}
