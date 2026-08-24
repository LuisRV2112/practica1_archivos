package com.clinica.modelo;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Una linea de la bitacora: quedo constancia de que alguien hizo algo, cuando y
 * sobre que modulo.
 *
 * La bitacora es de solo anexar: una entrada nunca se modifica ni se borra. Un
 * registro de auditoria que se puede editar no sirve como registro de auditoria.
 */
public class EntradaBitacora {

    private UUID id;
    private LocalDateTime momento;

    /** Modulo afectado: "Medicos", "Pacientes", "Citas", "Reportes". */
    private String modulo;

    private TipoOperacion operacion;

    /** Descripcion legible de lo que ocurrio. */
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
