package com.clinica.servicio;

import com.clinica.modelo.Cita;
import com.clinica.modelo.TipoOperacion;
import com.clinica.modelo.EstadoCita;
import com.clinica.modelo.Medico;
import com.clinica.persistencia.ArchivoCitas;
import com.clinica.persistencia.ArchivoMedicos;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Reglas de negocio de médicos. La vista nunca toca el archivo directamente.
 *
 * Validaciones del enunciado: nombres, apellidos y especialidad obligatorios;
 * correo opcional; UUID único. Plus longitudes para que el texto no se recorte
 * y horario coherente.
 */
public class ServicioMedicos {

    /** Formato usado para leer y mostrar horas en la interfaz. */
    public static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ArchivoMedicos archivo;

    /**
     * Archivo de citas. Se usa para validar que un cambio de horario no deje
     * citas programadas fuera del nuevo rango. Se recibe el archivo (no
     * ServicioCitas) para evitar dependencias circulares.
     */
    private final ArchivoCitas archivoCitas;

    /** Bitacora donde se anota cada operacion del modulo. */
    private final ServicioBitacora bitacora;

    public ServicioMedicos(ArchivoMedicos archivo, ArchivoCitas archivoCitas,
                           ServicioBitacora bitacora) {
        this.archivo = archivo;
        this.archivoCitas = archivoCitas;
        this.bitacora = bitacora;
    }

    // Altas y cambios

    /**
     * Registra un médico nuevo. El UUID lo genera la capa de persistencia.
     */
    public UUID registrar(Medico medico) throws ExcepcionValidacion, IOException {
        validar(medico);
        medico.setId(null); // se fuerza a que el id lo genere el sistema

        UUID id = archivo.insertar(medico);
        bitacora.registrar(ServicioBitacora.MODULO_MEDICOS, TipoOperacion.CREACION,
                "Se registro al medico " + medico.getNombreCompleto()
                        + " (" + medico.getEspecialidad() + ")");
        return id;
    }

    /**
     * Modifica un médico existente. Si cambia el horario, verifica que ninguna
     * cita programada quede fuera del nuevo rango (pide el enunciado).
     */
    public void modificar(Medico medico) throws ExcepcionValidacion, IOException {
        if (medico.getId() == null) {
            throw new ExcepcionValidacion("El medico que intenta modificar no tiene identificador.");
        }
        validar(medico);

        Medico anterior = archivo.buscarPorId(medico.getId());
        if (anterior == null) {
            throw new ExcepcionValidacion("El medico ya no existe en el archivo.");
        }

        boolean cambioHorario =
                !medico.getHoraInicio().equals(anterior.getHoraInicio())
                        || !medico.getHoraFin().equals(anterior.getHoraFin());

        if (cambioHorario) {
            verificarHorarioContraCitas(medico);
        }

        archivo.actualizar(medico);
        bitacora.registrar(ServicioBitacora.MODULO_MEDICOS, TipoOperacion.ACTUALIZACION,
                "Se modificaron los datos del medico " + medico.getNombreCompleto());
    }

    /** Rechaza el horario si alguna cita PROGRAMADA queda fuera. */
    private void verificarHorarioContraCitas(Medico medico)
            throws ExcepcionValidacion, IOException {

        List<Cita> conflictivas = new ArrayList<>();

        for (Cita cita : archivoCitas.listarTodos()) {
            if (!medico.getId().equals(cita.getIdMedico())) {
                continue;
            }
            if (cita.getEstado() != EstadoCita.PROGRAMADA || cita.getHoraInicio() == null) {
                continue;
            }

            LocalTime inicio = cita.getHoraInicio();
            LocalTime fin = inicio.plusMinutes(ServicioCitas.DURACION_MINUTOS);

            if (inicio.isBefore(medico.getHoraInicio()) || fin.isAfter(medico.getHoraFin())) {
                conflictivas.add(cita);
            }
        }

        if (!conflictivas.isEmpty()) {
            StringBuilder detalle = new StringBuilder();
            detalle.append("El horario nuevo dejaria fuera ")
                    .append(conflictivas.size())
                    .append(conflictivas.size() == 1 ? " cita ya programada:" : " citas ya programadas:");

            for (Cita c : conflictivas) {
                detalle.append("\n  - ")
                        .append(c.getFecha())
                        .append(" a las ")
                        .append(formatearHora(c.getHoraInicio()));
            }
            detalle.append("\n\nCancele o reprograme esas citas antes de cambiar el horario.");

            throw new ExcepcionValidacion(detalle.toString());
        }
    }

    /** Activa o desactiva un medico sin tocar el resto de sus datos. */
    public void cambiarEstado(UUID id, boolean activo) throws ExcepcionValidacion, IOException {
        Medico medico = archivo.buscarPorId(id);
        if (medico == null) {
            throw new ExcepcionValidacion("No se encontro el medico indicado.");
        }
        medico.setActivo(activo);
        archivo.actualizar(medico);

        bitacora.registrar(ServicioBitacora.MODULO_MEDICOS, TipoOperacion.CAMBIO_ESTADO,
                "El medico " + medico.getNombreCompleto() + " quedo "
                        + (activo ? "activo" : "inactivo"));
    }

    // Consultas

    /** Listado completo, ordenado por apellidos → nombres. */
    public List<Medico> listar() throws IOException {
        List<Medico> medicos = archivo.listarTodos();
        medicos.sort(Comparator
                .comparing(Medico::getApellidos, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Medico::getNombres, String.CASE_INSENSITIVE_ORDER));
        return medicos;
    }

    public Medico buscarPorId(UUID id) throws IOException {
        return archivo.buscarPorId(id);
    }

    /** Busca por UUID (una sola lectura) o libremente por texto. */
    public List<Medico> buscar(String texto) throws IOException {
        String consulta = (texto == null) ? "" : texto.trim();
        if (consulta.isEmpty()) {
            return listar();
        }

        // Camino rápido: UUID completo → búsqueda directa.
        try {
            Medico porId = archivo.buscarPorId(UUID.fromString(consulta));
            List<Medico> unico = new ArrayList<>();
            if (porId != null) {
                unico.add(porId);
            }
            return unico;
        } catch (IllegalArgumentException noEsUuid) {
            // No era UUID; se sigue con búsqueda por texto.
        }

        String aguja = consulta.toLowerCase();
        List<Medico> resultado = new ArrayList<>();

        for (Medico m : listar()) {
            boolean coincide =
                    m.getNombres().toLowerCase().contains(aguja)
                            || m.getApellidos().toLowerCase().contains(aguja)
                            || m.getEspecialidad().toLowerCase().contains(aguja)
                            || m.getId().toString().toLowerCase().startsWith(aguja);

            if (coincide) {
                resultado.add(m);
            }
        }
        return resultado;
    }

    /** Medicos activos o inactivos, segun se pida. */
    public List<Medico> listarPorEstado(boolean activo) throws IOException {
        List<Medico> resultado = new ArrayList<>();
        for (Medico m : listar()) {
            if (m.isActivo() == activo) {
                resultado.add(m);
            }
        }
        return resultado;
    }

    /** Medicos de una especialidad determinada. */
    public List<Medico> listarPorEspecialidad(String especialidad) throws IOException {
        String buscada = (especialidad == null) ? "" : especialidad.trim();
        List<Medico> resultado = new ArrayList<>();

        for (Medico m : listar()) {
            if (m.getEspecialidad().equalsIgnoreCase(buscada)) {
                resultado.add(m);
            }
        }
        return resultado;
    }

    /** Especialidades distintas, en orden alfabético; para combos de la interfaz. */
    public List<String> especialidades() throws IOException {
        TreeSet<String> conjunto = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Medico m : archivo.listarTodos()) {
            if (!m.getEspecialidad().isEmpty()) {
                conjunto.add(m.getEspecialidad());
            }
        }
        return new ArrayList<>(conjunto);
    }

    public int cantidad() {
        return archivo.cantidad();
    }

    // Validaciones

    /** Aplica todas las reglas antes de tocar el archivo. */
    private void validar(Medico medico) throws ExcepcionValidacion {
        if (medico == null) {
            throw new ExcepcionValidacion("No hay datos del medico.");
        }

        medico.setNombres(normalizar(medico.getNombres()));
        medico.setApellidos(normalizar(medico.getApellidos()));
        medico.setEspecialidad(normalizar(medico.getEspecialidad()));
        medico.setTelefono(normalizar(medico.getTelefono()));
        medico.setCorreo(normalizar(medico.getCorreo()));

        // --- Campos obligatorios ---
        exigir(medico.getNombres(), "Los nombres son obligatorios.");
        exigir(medico.getApellidos(), "Los apellidos son obligatorios.");
        exigir(medico.getEspecialidad(), "La especialidad es obligatoria.");

        // --- Longitudes máximas ---
        limitar(medico.getNombres(), ArchivoMedicos.LARGO_NOMBRES, "Nombres");
        limitar(medico.getApellidos(), ArchivoMedicos.LARGO_APELLIDOS, "Apellidos");
        limitar(medico.getEspecialidad(), ArchivoMedicos.LARGO_ESPECIALIDAD, "Especialidad");
        limitar(medico.getTelefono(), ArchivoMedicos.LARGO_TELEFONO, "Telefono");
        limitar(medico.getCorreo(), ArchivoMedicos.LARGO_CORREO, "Correo");

        // --- Correo: opcional pero con formato si viene ---
        if (!medico.getCorreo().isEmpty() && !esCorreoValido(medico.getCorreo())) {
            throw new ExcepcionValidacion("El correo electronico no tiene un formato valido.");
        }

        // --- Horario coherente ---
        if (medico.getHoraInicio() == null || medico.getHoraFin() == null) {
            throw new ExcepcionValidacion("Debe indicar el horario de inicio y de finalizacion.");
        }
        if (!medico.getHoraInicio().isBefore(medico.getHoraFin())) {
            throw new ExcepcionValidacion(
                    "La hora de inicio debe ser anterior a la hora de finalizacion.");
        }
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

    /** Validación básica: arroba, algo antes/después, punto en dominio. No es
     *  RFC completo, solo para atajar errores de captura. */
    private static boolean esCorreoValido(String correo) {
        int arroba = correo.indexOf('@');
        if (arroba <= 0 || arroba != correo.lastIndexOf('@')) {
            return false;
        }
        String dominio = correo.substring(arroba + 1);
        return dominio.contains(".")
                && !dominio.startsWith(".")
                && !dominio.endsWith(".")
                && !correo.contains(" ");
    }

    /**
     * Convierte texto "HH:mm" a LocalTime. Vive aquí para que la regla de
     * formato sea única en todo el sistema.
     */
    public static LocalTime interpretarHora(String texto, String campo)
            throws ExcepcionValidacion {
        String valor = normalizar(texto);
        if (valor.isEmpty()) {
            throw new ExcepcionValidacion(campo + " es obligatoria.");
        }
        try {
            return LocalTime.parse(valor, FORMATO_HORA);
        } catch (DateTimeParseException e) {
            throw new ExcepcionValidacion(
                    campo + " debe tener el formato HH:mm (por ejemplo 08:30).");
        }
    }

    /** Da formato "HH:mm"; cadena vacía si es nula. */
    public static String formatearHora(LocalTime hora) {
        return (hora == null) ? "" : hora.format(FORMATO_HORA);
    }
}