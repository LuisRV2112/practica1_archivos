package com.clinica.servicio;

import com.clinica.modelo.Cita;
import com.clinica.modelo.EntradaBitacora;
import com.clinica.modelo.EstadoCita;
import com.clinica.modelo.Medico;
import com.clinica.modelo.Paciente;
import com.clinica.modelo.Reporte;
import com.clinica.modelo.TipoSangre;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Genera los reportes que pide el enunciado.
 *
 * Todos devuelven un {@link Reporte}, una tabla generica de texto. Gracias a eso
 * la pantalla de reportes y el exportador a CSV son uno solo para los quince.
 *
 * Este servicio no lee archivos por su cuenta: le pide los datos a los otros
 * servicios, que ya aplican su propio orden y sus reglas.
 */
public class ServicioReportes {

    private final ServicioMedicos servicioMedicos;
    private final ServicioPacientes servicioPacientes;
    private final ServicioCitas servicioCitas;
    private final ServicioBitacora servicioBitacora;

    public ServicioReportes(ServicioMedicos servicioMedicos,
                            ServicioPacientes servicioPacientes,
                            ServicioCitas servicioCitas,
                            ServicioBitacora servicioBitacora) {
        this.servicioMedicos = servicioMedicos;
        this.servicioPacientes = servicioPacientes;
        this.servicioCitas = servicioCitas;
        this.servicioBitacora = servicioBitacora;
    }

    // =======================================================================
    // REPORTES DE PACIENTES
    // =======================================================================

    public Reporte pacientesCompleto() throws IOException {
        Reporte reporte = new Reporte("Reporte completo de pacientes",
                "Identificacion", "Nombres", "Apellidos", "Nacimiento",
                "Edad", "Sexo", "Telefono", "Correo", "Tipo de sangre");

        for (Paciente p : servicioPacientes.listar()) {
            reporte.agregarFila(
                    p.getIdentificacion(),
                    p.getNombres(),
                    p.getApellidos(),
                    ServicioPacientes.formatearFecha(p.getFechaNacimiento()),
                    String.valueOf(p.getEdad()),
                    texto(p.getSexo()),
                    p.getTelefono(),
                    p.getCorreo(),
                    texto(p.getTipoSangre()));
        }
        return reporte;
    }

    public Reporte pacientesPorTipoSangre(TipoSangre tipo) throws IOException {
        Reporte reporte = new Reporte(
                "Pacientes con tipo de sangre " + texto(tipo),
                "Identificacion", "Nombres", "Apellidos", "Edad", "Telefono");

        for (Paciente p : servicioPacientes.listarPorTipoSangre(tipo)) {
            reporte.agregarFila(
                    p.getIdentificacion(),
                    p.getNombres(),
                    p.getApellidos(),
                    String.valueOf(p.getEdad()),
                    p.getTelefono());
        }
        return reporte;
    }

    /**
     * Pacientes ordenados por cantidad de citas, de mayor a menor.
     * Solo aparecen quienes tienen al menos una.
     */
    public Reporte pacientesConMasCitas() throws IOException {
        Map<String, Integer> conteo = new HashMap<>();
        for (Cita c : servicioCitas.listar()) {
            conteo.merge(c.getIdentificacionPaciente(), 1, Integer::sum);
        }

        List<Paciente> conCitas = new ArrayList<>();
        for (Paciente p : servicioPacientes.listar()) {
            if (conteo.containsKey(p.getIdentificacion())) {
                conCitas.add(p);
            }
        }
        conCitas.sort(Comparator
                .comparingInt((Paciente p) -> conteo.get(p.getIdentificacion()))
                .reversed()
                .thenComparing(Paciente::getApellidos, String.CASE_INSENSITIVE_ORDER));

        Reporte reporte = new Reporte("Pacientes con mayor cantidad de citas",
                "Identificacion", "Paciente", "Cantidad de citas");

        for (Paciente p : conCitas) {
            reporte.agregarFila(
                    p.getIdentificacion(),
                    p.getNombreCompleto(),
                    String.valueOf(conteo.get(p.getIdentificacion())));
        }
        return reporte;
    }

    public Reporte pacientesSinCitas() throws IOException {
        List<Cita> citas = servicioCitas.listar();

        Reporte reporte = new Reporte("Pacientes que nunca han tenido una cita",
                "Identificacion", "Nombres", "Apellidos", "Telefono", "Correo");

        for (Paciente p : servicioPacientes.listar()) {
            boolean tieneCitas = citas.stream()
                    .anyMatch(c -> c.getIdentificacionPaciente().equals(p.getIdentificacion()));

            if (!tieneCitas) {
                reporte.agregarFila(
                        p.getIdentificacion(),
                        p.getNombres(),
                        p.getApellidos(),
                        p.getTelefono(),
                        p.getCorreo());
            }
        }
        return reporte;
    }

    // =======================================================================
    // REPORTES DE MEDICOS
    // =======================================================================

    public Reporte medicosCompleto() throws IOException {
        Reporte reporte = new Reporte("Reporte completo de medicos",
                "Codigo", "Nombres", "Apellidos", "Especialidad",
                "Telefono", "Correo", "Horario", "Estado");

        for (Medico m : servicioMedicos.listar()) {
            reporte.agregarFila(
                    m.getId().toString(),
                    m.getNombres(),
                    m.getApellidos(),
                    m.getEspecialidad(),
                    m.getTelefono(),
                    m.getCorreo(),
                    ServicioMedicos.formatearHora(m.getHoraInicio())
                            + " - " + ServicioMedicos.formatearHora(m.getHoraFin()),
                    m.isActivo() ? "Activo" : "Inactivo");
        }
        return reporte;
    }

    public Reporte medicosPorEspecialidad(String especialidad) throws IOException {
        Reporte reporte = new Reporte("Medicos de la especialidad " + especialidad,
                "Nombres", "Apellidos", "Telefono", "Horario", "Estado");

        for (Medico m : servicioMedicos.listarPorEspecialidad(especialidad)) {
            reporte.agregarFila(
                    m.getNombres(),
                    m.getApellidos(),
                    m.getTelefono(),
                    ServicioMedicos.formatearHora(m.getHoraInicio())
                            + " - " + ServicioMedicos.formatearHora(m.getHoraFin()),
                    m.isActivo() ? "Activo" : "Inactivo");
        }
        return reporte;
    }

    public Reporte medicosConMasCitas() throws IOException {
        Map<UUID, Integer> conteo = new HashMap<>();
        for (Cita c : servicioCitas.listar()) {
            conteo.merge(c.getIdMedico(), 1, Integer::sum);
        }

        List<Medico> conCitas = new ArrayList<>();
        for (Medico m : servicioMedicos.listar()) {
            if (conteo.containsKey(m.getId())) {
                conCitas.add(m);
            }
        }
        conCitas.sort(Comparator
                .comparingInt((Medico m) -> conteo.get(m.getId()))
                .reversed()
                .thenComparing(Medico::getApellidos, String.CASE_INSENSITIVE_ORDER));

        Reporte reporte = new Reporte("Medicos con mayor cantidad de citas",
                "Medico", "Especialidad", "Cantidad de citas");

        for (Medico m : conCitas) {
            reporte.agregarFila(
                    m.getNombreCompleto(),
                    m.getEspecialidad(),
                    String.valueOf(conteo.get(m.getId())));
        }
        return reporte;
    }

    public Reporte medicosConCitasEnFecha(LocalDate fecha) throws IOException {
        Map<UUID, Medico> medicos = mapaMedicos();
        Map<UUID, Integer> conteo = new HashMap<>();

        for (Cita c : servicioCitas.listarPorFecha(fecha)) {
            if (c.getEstado() == EstadoCita.PROGRAMADA) {
                conteo.merge(c.getIdMedico(), 1, Integer::sum);
            }
        }

        Reporte reporte = new Reporte(
                "Medicos con citas programadas para el "
                        + ServicioPacientes.formatearFecha(fecha),
                "Medico", "Especialidad", "Horario de atencion", "Citas ese dia");

        for (Map.Entry<UUID, Integer> entrada : conteo.entrySet()) {
            Medico m = medicos.get(entrada.getKey());
            if (m == null) {
                continue;
            }
            reporte.agregarFila(
                    m.getNombreCompleto(),
                    m.getEspecialidad(),
                    ServicioMedicos.formatearHora(m.getHoraInicio())
                            + " - " + ServicioMedicos.formatearHora(m.getHoraFin()),
                    String.valueOf(entrada.getValue()));
        }
        return reporte;
    }

    // =======================================================================
    // REPORTES DE CITAS
    // =======================================================================

    public Reporte citasCompleto() throws IOException {
        return construirReporteDeCitas("Reporte completo de citas", servicioCitas.listar());
    }

    public Reporte citasPorRango(LocalDate desde, LocalDate hasta)
            throws ExcepcionValidacion, IOException {
        return construirReporteDeCitas(
                "Citas del " + ServicioPacientes.formatearFecha(desde)
                        + " al " + ServicioPacientes.formatearFecha(hasta),
                servicioCitas.listarPorRango(desde, hasta));
    }

    public Reporte citasPorMedico(Medico medico) throws IOException {
        return construirReporteDeCitas(
                "Citas del doctor " + medico.getNombreCompleto(),
                servicioCitas.listarPorMedico(medico.getId()));
    }

    public Reporte citasPorPaciente(Paciente paciente) throws IOException {
        return construirReporteDeCitas(
                "Citas del paciente " + paciente.getNombreCompleto(),
                servicioCitas.listarPorPaciente(paciente.getIdentificacion()));
    }

    public Reporte citasPorEstado(EstadoCita estado) throws IOException {
        return construirReporteDeCitas("Citas en estado " + texto(estado),
                servicioCitas.listarPorEstado(estado));
    }

    /** Cuantas citas ha recibido cada especialidad. */
    public Reporte citasPorEspecialidad() throws IOException {
        Map<UUID, Medico> medicos = mapaMedicos();
        Map<String, Integer> conteo = new HashMap<>();

        for (Cita c : servicioCitas.listar()) {
            Medico m = medicos.get(c.getIdMedico());
            String especialidad = (m == null) ? "(medico no encontrado)" : m.getEspecialidad();
            conteo.merge(especialidad, 1, Integer::sum);
        }

        List<Map.Entry<String, Integer>> ordenado = new ArrayList<>(conteo.entrySet());
        ordenado.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

        Reporte reporte = new Reporte("Cantidad de citas por especialidad",
                "Especialidad", "Cantidad de citas");

        for (Map.Entry<String, Integer> entrada : ordenado) {
            reporte.agregarFila(entrada.getKey(), String.valueOf(entrada.getValue()));
        }
        return reporte;
    }

    // =======================================================================
    // REPORTE DE BITACORA
    // =======================================================================

    public Reporte bitacora() throws IOException {
        Reporte reporte = new Reporte("Bitacora de operaciones",
                "Fecha y hora", "Modulo", "Operacion", "Detalle");

        for (EntradaBitacora e : servicioBitacora.listar()) {
            reporte.agregarFila(
                    ServicioBitacora.formatearMomento(e.getMomento()),
                    e.getModulo(),
                    texto(e.getOperacion()),
                    e.getDetalle());
        }
        return reporte;
    }

    // =======================================================================
    // AUXILIARES
    // =======================================================================

    /**
     * Arma la tabla de un listado de citas resolviendo los nombres de paciente y
     * medico. Los diccionarios se construyen UNA vez y no una por fila.
     */
    private Reporte construirReporteDeCitas(String titulo, List<Cita> citas) throws IOException {
        Map<String, Paciente> pacientes = mapaPacientes();
        Map<UUID, Medico> medicos = mapaMedicos();

        Reporte reporte = new Reporte(titulo,
                "Codigo", "Fecha", "Hora", "Paciente", "Medico",
                "Especialidad", "Motivo", "Estado", "Observaciones");

        for (Cita c : citas) {
            Paciente p = pacientes.get(c.getIdentificacionPaciente());
            Medico m = medicos.get(c.getIdMedico());

            reporte.agregarFila(
                    c.getId().toString(),
                    ServicioPacientes.formatearFecha(c.getFecha()),
                    ServicioMedicos.formatearHora(c.getHoraInicio()),
                    (p == null) ? c.getIdentificacionPaciente() : p.getNombreCompleto(),
                    (m == null) ? "(no encontrado)" : m.getNombreCompleto(),
                    (m == null) ? "" : m.getEspecialidad(),
                    c.getMotivo(),
                    texto(c.getEstado()),
                    c.getObservaciones());
        }
        return reporte;
    }

    private Map<String, Paciente> mapaPacientes() throws IOException {
        Map<String, Paciente> mapa = new HashMap<>();
        for (Paciente p : servicioPacientes.listar()) {
            mapa.put(p.getIdentificacion(), p);
        }
        return mapa;
    }

    private Map<UUID, Medico> mapaMedicos() throws IOException {
        Map<UUID, Medico> mapa = new HashMap<>();
        for (Medico m : servicioMedicos.listar()) {
            mapa.put(m.getId(), m);
        }
        return mapa;
    }

    /** Convierte a texto cualquier valor que pueda ser nulo. */
    private static String texto(Object valor) {
        return (valor == null) ? "" : valor.toString();
    }
}
