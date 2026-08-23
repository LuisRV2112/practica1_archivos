package com.clinica.servicio;

import com.clinica.modelo.Paciente;
import com.clinica.modelo.TipoSangre;
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
 * Reglas de negocio del modulo de pacientes.
 *
 * Mismo esquema que ServicioMedicos: la vista nunca toca el archivo, solo pide
 * cosas a esta clase.
 *
 * Reglas que exige el enunciado:
 *   - el numero de identificacion no puede repetirse
 *   - nombres y apellidos son obligatorios
 *   - el correo es opcional
 */
public class ServicioPacientes {

    /**
     * Formato de fecha usado en toda la interfaz.
     *
     * Se configura con ResolverStyle.STRICT porque, por omision, el formateador
     * trabaja en modo SMART: ante una fecha inexistente como 31/02/1990 no falla,
     * sino que la "corrige" en silencio al 28/02. Eso guardaria en el archivo una
     * fecha que el usuario nunca escribio.
     *
     * El patron usa "uuuu" en lugar de "yyyy" porque el modo estricto exige un
     * ano sin ambiguedad de era (uuuu admite anos negativos; yyyy necesita saber
     * si es antes o despues de Cristo y falla sin ese dato).
     */
    public static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private final ArchivoPacientes archivo;

    public ServicioPacientes(ArchivoPacientes archivo) {
        this.archivo = archivo;
    }

    // =======================================================================
    // ALTAS Y CAMBIOS
    // =======================================================================

    public void registrar(Paciente paciente) throws ExcepcionValidacion, IOException {
        validar(paciente);

        if (archivo.existe(paciente.getIdentificacion())) {
            throw new ExcepcionValidacion(
                    "Ya existe un paciente con la identificacion "
                            + paciente.getIdentificacion() + ".");
        }
        archivo.insertar(paciente);
    }

    /**
     * Guarda los cambios de un paciente. La identificacion no se puede cambiar:
     * es la llave del registro y las citas la referencian. Para "cambiarla"
     * habria que eliminar y volver a crear, con las citas que eso implica.
     */
    public void modificar(Paciente paciente) throws ExcepcionValidacion, IOException {
        validar(paciente);

        if (!archivo.actualizar(paciente)) {
            throw new ExcepcionValidacion("El paciente ya no existe en el archivo.");
        }
    }

    /**
     * Elimina un paciente.
     *
     * NOTA PENDIENTE: cuando exista el modulo de citas habra que impedir borrar
     * a un paciente que tenga citas registradas, o el archivo de citas quedaria
     * apuntando a alguien que ya no existe.
     */
    public void eliminar(String identificacion) throws ExcepcionValidacion, IOException {
        if (!archivo.eliminar(normalizar(identificacion))) {
            throw new ExcepcionValidacion("No se encontro el paciente indicado.");
        }
    }

    // =======================================================================
    // CONSULTAS
    // =======================================================================

    /** Listado completo, ordenado por apellidos y luego nombres. */
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

    public boolean existe(String identificacion) {
        return archivo.existe(normalizar(identificacion));
    }

    /**
     * Busqueda por numero de identificacion, nombre o apellido, tal como pide
     * el enunciado. Si el texto coincide exactamente con una identificacion se
     * resuelve por el indice, en una sola lectura.
     */
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

    // =======================================================================
    // VALIDACIONES
    // =======================================================================

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

        // --- Longitudes, para que nada se recorte al escribirlo ---
        limitar(paciente.getIdentificacion(), ArchivoPacientes.LARGO_IDENTIFICACION,
                "Identificacion");
        limitar(paciente.getNombres(), ArchivoPacientes.LARGO_NOMBRES, "Nombres");
        limitar(paciente.getApellidos(), ArchivoPacientes.LARGO_APELLIDOS, "Apellidos");
        limitar(paciente.getTelefono(), ArchivoPacientes.LARGO_TELEFONO, "Telefono");
        limitar(paciente.getCorreo(), ArchivoPacientes.LARGO_CORREO, "Correo");

        // --- Correo: opcional, pero si viene debe tener forma de correo ---
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

    /** Convierte un texto "dd/MM/yyyy" en LocalDate. */
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

    /** Da formato "dd/MM/yyyy" a una fecha, o cadena vacia si es nula. */
    public static String formatearFecha(LocalDate fecha) {
        return (fecha == null) ? "" : fecha.format(FORMATO_FECHA);
    }
}
