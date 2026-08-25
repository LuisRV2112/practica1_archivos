package com.clinica.servicio;

import com.clinica.modelo.Cita;
import com.clinica.modelo.EstadoCita;
import com.clinica.modelo.Medico;
import com.clinica.modelo.Paciente;
import com.clinica.modelo.TipoOperacion;
import com.clinica.persistencia.ArchivoCitas;
import com.clinica.persistencia.ArchivoMedicos;
import com.clinica.persistencia.ArchivoPacientes;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Reglas de negocio de citas. Recibe los tres archivos de persistencia (el suyo
 * y los de médicos y pacientes) para validar integridad referencial. Recibe
 * archivos en vez de servicios para evitar dependencias circulares.
 */
public class ServicioCitas {

    /** Duración estándar de una consulta en minutos. Se usa para validar traslapes. */
    public static final int DURACION_MINUTOS = 30;

    private final ArchivoCitas archivo;
    private final ArchivoMedicos archivoMedicos;
    private final ArchivoPacientes archivoPacientes;

    /** Bitacora donde se anota cada operacion del modulo. */
    private final ServicioBitacora bitacora;

    public ServicioCitas(ArchivoCitas archivo, ArchivoMedicos archivoMedicos,
                         ArchivoPacientes archivoPacientes, ServicioBitacora bitacora) {
        this.archivo = archivo;
        this.archivoMedicos = archivoMedicos;
        this.archivoPacientes = archivoPacientes;
        this.bitacora = bitacora;
    }

    // Programación y cambios de estado

    /**
     * Programa una cita nueva. Valida que el paciente y el medico existan, que
     * el medico este activo, que la hora caiga dentro de su horario de atencion
     * y que no choque con otra cita.
     *
     * @return el identificador asignado
     */
    public UUID programar(Cita cita) throws ExcepcionValidacion, IOException {
        cita.setId(null);              // el UUID lo genera el sistema
        cita.setEstado(EstadoCita.PROGRAMADA);

        validar(cita, true);
        UUID id = archivo.insertar(cita);

        bitacora.registrar(ServicioBitacora.MODULO_CITAS, TipoOperacion.CREACION,
                "Cita programada para el paciente " + cita.getIdentificacionPaciente()
                        + " el " + cita.getFecha() + " a las " + cita.getHoraInicio());
        return id;
    }

    /** Modifica solo motivo y observaciones. El enunciado no permite cambiar
     *  fecha/hora/paciente/médico (sería otra cita). */
    public void modificarMotivoYObservaciones(UUID idCita, String motivo, String observaciones)
            throws ExcepcionValidacion, IOException {

        Cita cita = obtenerObligatoria(idCita);

        String nuevoMotivo = normalizar(motivo);
        String nuevasObservaciones = normalizar(observaciones);

        exigir(nuevoMotivo, "El motivo de la consulta es obligatorio.");
        limitar(nuevoMotivo, ArchivoCitas.LARGO_MOTIVO, "Motivo");
        limitar(nuevasObservaciones, ArchivoCitas.LARGO_OBSERVACIONES, "Observaciones");

        cita.setMotivo(nuevoMotivo);
        cita.setObservaciones(nuevasObservaciones);
        archivo.actualizar(cita);

        bitacora.registrar(ServicioBitacora.MODULO_CITAS, TipoOperacion.ACTUALIZACION,
                "Se actualizo el motivo/observaciones de la cita del "
                        + cita.getFecha() + " a las " + cita.getHoraInicio());
    }

    /** Cancela una cita. Solo tiene sentido sobre una cita programada. */
    public void cancelar(UUID idCita) throws ExcepcionValidacion, IOException {
        Cita cita = obtenerObligatoria(idCita);

        if (cita.getEstado() == EstadoCita.CANCELADA) {
            throw new ExcepcionValidacion("La cita ya estaba cancelada.");
        }
        if (cita.getEstado() == EstadoCita.ATENDIDA) {
            throw new ExcepcionValidacion(
                    "No se puede cancelar una cita que ya fue atendida.");
        }

        cita.setEstado(EstadoCita.CANCELADA);
        archivo.actualizar(cita);

        bitacora.registrar(ServicioBitacora.MODULO_CITAS, TipoOperacion.CAMBIO_ESTADO,
                "Se cancelo la cita del " + cita.getFecha()
                        + " a las " + cita.getHoraInicio());
    }

    /** Marca una cita como atendida. */
    public void marcarAtendida(UUID idCita) throws ExcepcionValidacion, IOException {
        Cita cita = obtenerObligatoria(idCita);

        if (cita.getEstado() == EstadoCita.ATENDIDA) {
            throw new ExcepcionValidacion("La cita ya estaba marcada como atendida.");
        }
        if (cita.getEstado() == EstadoCita.CANCELADA) {
            throw new ExcepcionValidacion(
                    "No se puede atender una cita cancelada. Programe una nueva.");
        }

        cita.setEstado(EstadoCita.ATENDIDA);
        archivo.actualizar(cita);

        bitacora.registrar(ServicioBitacora.MODULO_CITAS, TipoOperacion.CAMBIO_ESTADO,
                "Se marco como atendida la cita del " + cita.getFecha()
                        + " a las " + cita.getHoraInicio());
    }

    /** Elimina una cita del archivo. */
    public void eliminar(UUID idCita) throws ExcepcionValidacion, IOException {
        Cita cita = archivo.buscarPorId(idCita);

        if (!archivo.eliminar(idCita)) {
            throw new ExcepcionValidacion("No se encontro la cita indicada.");
        }

        bitacora.registrar(ServicioBitacora.MODULO_CITAS, TipoOperacion.ELIMINACION,
                "Se elimino la cita "
                        + (cita == null ? idCita.toString()
                        : "del " + cita.getFecha() + " a las " + cita.getHoraInicio()));
    }

    // Consultas

    /** Listado completo, de la cita mas proxima a la mas lejana. */
    public List<Cita> listar() throws IOException {
        List<Cita> citas = archivo.listarTodos();
        citas.sort(Comparator
                .comparing(Cita::getFecha)
                .thenComparing(Cita::getHoraInicio));
        return citas;
    }

    public Cita buscarPorId(UUID id) throws IOException {
        return archivo.buscarPorId(id);
    }

    /**
     * Citas de un paciente: sigue el anillo del paciente, O(k) lecturas.
     */
    public List<Cita> listarPorPaciente(String identificacion) throws IOException {
        List<Cita> resultado = archivo.citasDelPaciente(normalizar(identificacion));
        ordenarPorFecha(resultado);
        return resultado;
    }

    /** Citas de un médico, siguiendo su anillo. */
    public List<Cita> listarPorMedico(UUID idMedico) throws IOException {
        List<Cita> resultado = archivo.citasDelMedico(idMedico);
        ordenarPorFecha(resultado);
        return resultado;
    }

    private static void ordenarPorFecha(List<Cita> citas) {
        citas.sort(Comparator
                .comparing(Cita::getFecha)
                .thenComparing(Cita::getHoraInicio));
    }

    public List<Cita> listarPorFecha(LocalDate fecha) throws IOException {
        List<Cita> resultado = new ArrayList<>();
        for (Cita c : listar()) {
            if (fecha.equals(c.getFecha())) {
                resultado.add(c);
            }
        }
        return resultado;
    }

    public List<Cita> listarPorEstado(EstadoCita estado) throws IOException {
        List<Cita> resultado = new ArrayList<>();
        for (Cita c : listar()) {
            if (c.getEstado() == estado) {
                resultado.add(c);
            }
        }
        return resultado;
    }

    /** Citas dentro de un rango de fechas, ambos extremos incluidos. */
    public List<Cita> listarPorRango(LocalDate desde, LocalDate hasta)
            throws ExcepcionValidacion, IOException {

        if (desde == null || hasta == null) {
            throw new ExcepcionValidacion("Debe indicar ambas fechas del rango.");
        }
        if (desde.isAfter(hasta)) {
            throw new ExcepcionValidacion(
                    "La fecha inicial no puede ser posterior a la final.");
        }

        List<Cita> resultado = new ArrayList<>();
        for (Cita c : listar()) {
            LocalDate f = c.getFecha();
            if (f != null && !f.isBefore(desde) && !f.isAfter(hasta)) {
                resultado.add(c);
            }
        }
        return resultado;
    }

    public int cantidad() {
        return archivo.cantidad();
    }

    // Consultas que usan otros módulos

    /** ¿El paciente tiene citas registradas? Lo usa ServicioPacientes para
     *  impedir borrados que dejarían citas huérfanas. */
    public boolean pacienteTieneCitas(String identificacion) throws IOException {
        return !listarPorPaciente(identificacion).isEmpty();
    }

    /** Citas fuera del horario nuevo de un médico; lo usa ServicioMedicos. */
    public List<Cita> citasFueraDeHorario(UUID idMedico, LocalTime nuevoInicio,
                                          LocalTime nuevoFin) throws IOException {
        List<Cita> conflictivas = new ArrayList<>();

        for (Cita c : listarPorMedico(idMedico)) {
            if (c.getEstado() != EstadoCita.PROGRAMADA || c.getHoraInicio() == null) {
                continue; // las canceladas y atendidas ya no estorban
            }

            LocalTime inicio = c.getHoraInicio();
            LocalTime fin = inicio.plusMinutes(DURACION_MINUTOS);

            if (inicio.isBefore(nuevoInicio) || fin.isAfter(nuevoFin)) {
                conflictivas.add(c);
            }
        }
        return conflictivas;
    }

    // Validaciones

    /** Aplica todas las reglas antes de escribir en disco. */
    private void validar(Cita cita, boolean esNueva) throws ExcepcionValidacion, IOException {
        if (cita == null) {
            throw new ExcepcionValidacion("No hay datos de la cita.");
        }

        cita.setIdentificacionPaciente(normalizar(cita.getIdentificacionPaciente()));
        cita.setMotivo(normalizar(cita.getMotivo()));
        cita.setObservaciones(normalizar(cita.getObservaciones()));

        // --- Campos propios ---
        exigir(cita.getIdentificacionPaciente(), "Debe seleccionar un paciente.");
        exigir(cita.getMotivo(), "El motivo de la consulta es obligatorio.");
        limitar(cita.getMotivo(), ArchivoCitas.LARGO_MOTIVO, "Motivo");
        limitar(cita.getObservaciones(), ArchivoCitas.LARGO_OBSERVACIONES, "Observaciones");

        if (cita.getIdMedico() == null) {
            throw new ExcepcionValidacion("Debe seleccionar un medico.");
        }
        if (cita.getFecha() == null) {
            throw new ExcepcionValidacion("La fecha de la cita es obligatoria.");
        }
        if (cita.getHoraInicio() == null) {
            throw new ExcepcionValidacion("La hora de inicio es obligatoria.");
        }

        // --- Paciente debe existir ---
        Paciente paciente = archivoPacientes.buscarPorId(cita.getIdentificacionPaciente());
        if (paciente == null) {
            throw new ExcepcionValidacion(
                    "No existe un paciente con la identificacion "
                            + cita.getIdentificacionPaciente() + ".");
        }
        if (esNueva && !paciente.isActivo()) {
            throw new ExcepcionValidacion(
                    "El paciente " + paciente.getNombreCompleto()
                            + " esta dado de baja y no puede recibir citas nuevas.");
        }

        // --- Médico debe existir y estar activo ---
        Medico medico = archivoMedicos.buscarPorId(cita.getIdMedico());
        if (medico == null) {
            throw new ExcepcionValidacion("El medico seleccionado ya no existe.");
        }
        if (!medico.isActivo()) {
            throw new ExcepcionValidacion(
                    "El doctor " + medico.getNombreCompleto()
                            + " esta inactivo y no puede recibir citas.");
        }

        // --- Sin citas hacia atrás en el tiempo ---
        if (esNueva && cita.getFecha().isBefore(LocalDate.now())) {
            throw new ExcepcionValidacion("No se puede programar una cita en una fecha pasada.");
        }

        // --- La cita debe caber en el horario del médico ---
        LocalTime inicioCita = cita.getHoraInicio();
        LocalTime finCita = inicioCita.plusMinutes(DURACION_MINUTOS);

        if (inicioCita.isBefore(medico.getHoraInicio()) || finCita.isAfter(medico.getHoraFin())) {
            throw new ExcepcionValidacion(
                    "El doctor " + medico.getNombreCompleto() + " atiende de "
                            + ServicioMedicos.formatearHora(medico.getHoraInicio()) + " a "
                            + ServicioMedicos.formatearHora(medico.getHoraFin())
                            + ". Una cita de " + DURACION_MINUTOS
                            + " minutos no cabe en el horario indicado.");
        }

        // --- Sin traslapes del médico ---
        for (Cita otra : listarPorMedico(cita.getIdMedico())) {
            if (seTraslapan(cita, otra)) {
                throw new ExcepcionValidacion(
                        "El doctor " + medico.getNombreCompleto()
                                + " ya tiene una cita a las "
                                + ServicioMedicos.formatearHora(otra.getHoraInicio())
                                + " ese dia.");
            }
        }

        // --- Sin traslapes del paciente ---
        for (Cita otra : listarPorPaciente(cita.getIdentificacionPaciente())) {
            if (seTraslapan(cita, otra)) {
                throw new ExcepcionValidacion(
                        "El paciente " + paciente.getNombreCompleto()
                                + " ya tiene una cita a esa hora.");
            }
        }
    }

    /** Compara intervalos: [a1,a2) y [b1,b2) se solapan si a1 < b2 y b1 < a2. */
    private boolean seTraslapan(Cita nueva, Cita existente) {
        // Una cita no choca consigo misma (importa al reprogramar).
        if (existente.getId() != null && existente.getId().equals(nueva.getId())) {
            return false;
        }
        // Las canceladas y las ya atendidas liberan el espacio.
        if (existente.getEstado() != EstadoCita.PROGRAMADA) {
            return false;
        }
        if (!nueva.getFecha().equals(existente.getFecha())) {
            return false;
        }

        LocalTime inicioA = nueva.getHoraInicio();
        LocalTime finA = inicioA.plusMinutes(DURACION_MINUTOS);
        LocalTime inicioB = existente.getHoraInicio();
        LocalTime finB = inicioB.plusMinutes(DURACION_MINUTOS);

        return inicioA.isBefore(finB) && inicioB.isBefore(finA);
    }

    private Cita obtenerObligatoria(UUID idCita) throws ExcepcionValidacion, IOException {
        Cita cita = archivo.buscarPorId(idCita);
        if (cita == null) {
            throw new ExcepcionValidacion("No se encontro la cita indicada.");
        }
        return cita;
    }

    private static String normalizar(String valor) {
        return (valor == null) ? "" : valor.trim();
    }

    private static void exigir(String valor, String mensaje) throws ExcepcionValidacion {
        if (valor.isEmpty()) {
            throw new ExcepcionValidacion(mensaje);
        }
    }

    private static void limitar(String valor, int maximo, String campo)
            throws ExcepcionValidacion {
        if (valor.length() > maximo) {
            throw new ExcepcionValidacion(
                    "El campo " + campo + " no puede exceder " + maximo + " caracteres.");
        }
    }
}