package com.clinica.servicio;

import com.clinica.modelo.Cita;
import com.clinica.modelo.EstadoCita;
import com.clinica.modelo.Paciente;
import com.clinica.modelo.TipoOperacion;
import com.clinica.modelo.TipoSangre;
import com.clinica.persistencia.ArchivoCitas;
import com.clinica.persistencia.ArchivoPacientes;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reglas de negocio de pacientes. La vista nunca toca el archivo directamente.
 *
 * Reglas del enunciado: identificación única; nombres y apellidos obligatorios;
 * correo opcional. Borrado lógico para conservar integridad referencial de las
 * citas históricas.
 */
public class ServicioPacientes {

    /**
     * Formato dd/MM/uuuu con STRICT para no "corregir" fechas como 31/02.
     * Se usa "uuuu" (no "yyyy") porque el modo estricto exige un año sin
     * ambigüedad de era.
     */
    public static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private final ArchivoPacientes archivo;

    /**
     * Archivo de citas. Se usa para impedir borrados que dejarían citas huérfanas.
     * Se recibe el archivo (no ServicioCitas) para evitar dependencias circulares.
     */
    private final ArchivoCitas archivoCitas;

    /** Bitacora donde se anota cada operacion del modulo. */
    private final ServicioBitacora bitacora;

    public ServicioPacientes(ArchivoPacientes archivo, ArchivoCitas archivoCitas,
                             ServicioBitacora bitacora) {
        this.archivo = archivo;
        this.archivoCitas = archivoCitas;
        this.bitacora = bitacora;
    }

    // Altas y cambios

    public void registrar(Paciente paciente) throws ExcepcionValidacion, IOException {
        validar(paciente);

        if (archivo.existe(paciente.getIdentificacion())) {
            throw new ExcepcionValidacion(
                    "Ya existe un paciente con la identificacion "
                            + paciente.getIdentificacion() + ".");
        }
        archivo.insertar(paciente);
        bitacora.registrar(ServicioBitacora.MODULO_PACIENTES, TipoOperacion.CREACION,
                "Se registro al paciente " + paciente.getNombreCompleto()
                        + " (" + paciente.getIdentificacion() + ")");
    }

    /**
     * Modifica un paciente existente. La identificación no se puede cambiar:
     * es la clave del registro y las citas la referencian.
     */
    public void modificar(Paciente paciente) throws ExcepcionValidacion, IOException {
        validar(paciente);

        // Se conserva el estado anterior para que una modificación no reactive
        // por accidente a un paciente dado de baja.
        Paciente anterior = archivo.buscarPorId(paciente.getIdentificacion());
        if (anterior != null) {
            paciente.setActivo(anterior.isActivo());
        }

        if (!archivo.actualizar(paciente)) {
            throw new ExcepcionValidacion("El paciente ya no existe en el archivo.");
        }

        bitacora.registrar(ServicioBitacora.MODULO_PACIENTES, TipoOperacion.ACTUALIZACION,
                "Se modificaron los datos del paciente " + paciente.getNombreCompleto()
                        + " (" + paciente.getIdentificacion() + ")");
    }

    /**
     * Borrado lógico: se marca como inactivo, no se borra del archivo.
     * Razones: (1) integridad referencial con citas históricas; (2) el
     * historial clínico no se tira. Se rechaza si tiene citas programadas.
     */
    public void darDeBaja(String identificacion) throws ExcepcionValidacion, IOException {
        String id = normalizar(identificacion);

        Paciente paciente = archivo.buscarPorId(id);
        if (paciente == null) {
            throw new ExcepcionValidacion("No se encontro el paciente indicado.");
        }
        if (!paciente.isActivo()) {
            throw new ExcepcionValidacion("El paciente ya estaba dado de baja.");
        }

        int programadas = 0;
        for (Cita cita : archivoCitas.listarTodos()) {
            if (id.equals(cita.getIdentificacionPaciente())
                    && cita.getEstado() == EstadoCita.PROGRAMADA) {
                programadas++;
            }
        }

        if (programadas > 0) {
            throw new ExcepcionValidacion(
                    "No se puede dar de baja al paciente porque tiene "
                            + programadas
                            + (programadas == 1 ? " cita programada." : " citas programadas.")
                            + "\nAtiendalas o cancelelas primero.");
        }

        paciente.setActivo(false);
        archivo.actualizar(paciente);

        bitacora.registrar(ServicioBitacora.MODULO_PACIENTES, TipoOperacion.CAMBIO_ESTADO,
                "Se dio de baja al paciente " + paciente.getNombreCompleto() + " (" + id + ")");
    }

    /** Reactiva a un paciente dado de baja. */
    public void reactivar(String identificacion) throws ExcepcionValidacion, IOException {
        String id = normalizar(identificacion);

        Paciente paciente = archivo.buscarPorId(id);
        if (paciente == null) {
            throw new ExcepcionValidacion("No se encontro el paciente indicado.");
        }
        if (paciente.isActivo()) {
            throw new ExcepcionValidacion("El paciente ya estaba activo.");
        }

        paciente.setActivo(true);
        archivo.actualizar(paciente);

        bitacora.registrar(ServicioBitacora.MODULO_PACIENTES, TipoOperacion.CAMBIO_ESTADO,
                "Se reactivo al paciente " + paciente.getNombreCompleto() + " (" + id + ")");
    }

    /** Pacientes activos o dados de baja, segun se pida. */
    public List<Paciente> listarPorEstado(boolean activo) throws IOException {
        List<Paciente> resultado = new ArrayList<>();
        for (Paciente p : listar()) {
            if (p.isActivo() == activo) {
                resultado.add(p);
            }
        }
        return resultado;
    }

    // Consultas

    /** Listado completo, ordenado por apellidos → nombres. */
    public List<Paciente> listar() throws IOException {
        List<Paciente> pacientes = archivo.listarTodos();
        pacientes.sort(Comparator
                .comparing(Paciente::getApellidos, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Paciente::getNombres, String.CASE_INSENSITIVE_ORDER));
        return pacientes;
    }

    public Paciente buscarPorIdentificacion(String identificacion) throws IOException {
        return archivo.buscarPorId(normalizar(identificacion));
    }

    public boolean existe(String identificacion) throws IOException {
        return archivo.existe(normalizar(identificacion));
    }

    /** Busca por identificación exacta (índice O(1)) o por texto libre. */
    public List<Paciente> buscar(String texto) throws IOException {
        String consulta = normalizar(texto);
        if (consulta.isEmpty()) {
            return listar();
        }

        Paciente exacto = archivo.buscarPorId(consulta);
        if (exacto != null) {
            List<Paciente> unico = new ArrayList<>();
            unico.add(exacto);
            return unico;
        }

        String aguja = consulta.toLowerCase();
        List<Paciente> resultado = new ArrayList<>();

        for (Paciente p : listar()) {
            boolean coincide =
                    p.getNombres().toLowerCase().contains(aguja)
                            || p.getApellidos().toLowerCase().contains(aguja)
                            || p.getIdentificacion().toLowerCase().contains(aguja);

            if (coincide) {
                resultado.add(p);
            }
        }
        return resultado;
    }

    /** Pacientes de un tipo de sangre determinado (para el reporte del enunciado). */
    public List<Paciente> listarPorTipoSangre(TipoSangre tipo) throws IOException {
        List<Paciente> resultado = new ArrayList<>();
        for (Paciente p : listar()) {
            if (p.getTipoSangre() == tipo) {
                resultado.add(p);
            }
        }
        return resultado;
    }

    public int cantidad() {
        return archivo.cantidad();
    }

    // Validaciones

    private void validar(Paciente paciente) throws ExcepcionValidacion {
        if (paciente == null) {
            throw new ExcepcionValidacion("No hay datos del paciente.");
        }

        paciente.setIdentificacion(normalizar(paciente.getIdentificacion()));
        paciente.setNombres(normalizar(paciente.getNombres()));
        paciente.setApellidos(normalizar(paciente.getApellidos()));
        paciente.setTelefono(normalizar(paciente.getTelefono()));
        paciente.setCorreo(normalizar(paciente.getCorreo()));

        // --- Campos obligatorios ---
        exigir(paciente.getIdentificacion(), "El numero de identificacion es obligatorio.");
        exigir(paciente.getNombres(), "Los nombres son obligatorios.");
        exigir(paciente.getApellidos(), "Los apellidos son obligatorios.");

        if (paciente.getSexo() == null) {
            throw new ExcepcionValidacion("Debe seleccionar el sexo del paciente.");
        }
        if (paciente.getTipoSangre() == null) {
            throw new ExcepcionValidacion("Debe seleccionar el tipo de sangre.");
        }

        // --- La identificacion debe ser solo digitos ---
        if (!paciente.getIdentificacion().matches("\\d+")) {
            throw new ExcepcionValidacion(
                    "El numero de identificacion solo puede contener digitos.");
        }

        // --- Longitudes máximas ---
        limitar(paciente.getIdentificacion(), ArchivoPacientes.LARGO_IDENTIFICACION,
                "Identificacion");
        limitar(paciente.getNombres(), ArchivoPacientes.LARGO_NOMBRES, "Nombres");
        limitar(paciente.getApellidos(), ArchivoPacientes.LARGO_APELLIDOS, "Apellidos");
        limitar(paciente.getTelefono(), ArchivoPacientes.LARGO_TELEFONO, "Telefono");
        limitar(paciente.getCorreo(), ArchivoPacientes.LARGO_CORREO, "Correo");

        // --- Correo: opcional pero con formato si viene ---
        if (!paciente.getCorreo().isEmpty() && !esCorreoValido(paciente.getCorreo())) {
            throw new ExcepcionValidacion("El correo electronico no tiene un formato valido.");
        }

        // --- Fecha de nacimiento ---
        if (paciente.getFechaNacimiento() == null) {
            throw new ExcepcionValidacion("La fecha de nacimiento es obligatoria.");
        }
        if (paciente.getFechaNacimiento().isAfter(LocalDate.now())) {
            throw new ExcepcionValidacion("La fecha de nacimiento no puede ser futura.");
        }
        if (paciente.getFechaNacimiento().isBefore(LocalDate.now().minusYears(130))) {
            throw new ExcepcionValidacion("La fecha de nacimiento no parece valida.");
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

    /** Convierte texto "dd/MM/uuuu" a LocalDate. */
    public static LocalDate interpretarFecha(String texto, String campo)
            throws ExcepcionValidacion {
        String valor = normalizar(texto);
        if (valor.isEmpty()) {
            throw new ExcepcionValidacion(campo + " es obligatoria.");
        }
        try {
            return LocalDate.parse(valor, FORMATO_FECHA);
        } catch (DateTimeParseException e) {
            throw new ExcepcionValidacion(
                    campo + " debe tener el formato dd/MM/aaaa (por ejemplo 15/03/1990).");
        }
    }

    /** Da formato "dd/MM/uuuu"; cadena vacía si es nula. */
    public static String formatearFecha(LocalDate fecha) {
        return (fecha == null) ? "" : fecha.format(FORMATO_FECHA);
    }
}