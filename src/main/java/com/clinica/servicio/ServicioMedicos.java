package com.clinica.servicio;

import com.clinica.modelo.Medico;
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
 * Reglas de negocio del modulo de medicos.
 *
 * Esta capa se interpone entre la vista y el archivo: la interfaz NUNCA toca
 * ArchivoMedicos directamente. Asi, si manana cambia el formato del archivo, la
 * vista no se entera; y si cambia una validacion, la persistencia tampoco.
 *
 * Aqui viven las validaciones que exige el enunciado:
 *   - nombres, apellidos y especialidad son obligatorios
 *   - el correo es opcional
 *   - el UUID debe ser unico
 * mas las que hacen falta para que el archivo no se corrompa (longitudes) y
 * para que los datos tengan sentido (horario coherente).
 */
public class ServicioMedicos {

    /** Formato usado para leer y mostrar horas en la interfaz. */
    public static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ArchivoMedicos archivo;

    public ServicioMedicos(ArchivoMedicos archivo) {
        this.archivo = archivo;
    }

    // =======================================================================
    // ALTAS Y CAMBIOS
    // =======================================================================

    /**
     * Registra un medico nuevo. El UUID lo genera la capa de persistencia.
     *
     * @return el id asignado
     */
    public UUID registrar(Medico medico) throws ExcepcionValidacion, IOException {
        validar(medico);
        medico.setId(null); // se fuerza a que el id lo genere el sistema
        return archivo.insertar(medico);
    }

    /**
     * Guarda los cambios de un medico existente.
     *
     * NOTA PENDIENTE: cuando exista el modulo de citas, aqui habra que
     * verificar que el nuevo horario no deje fuera de rango a citas ya
     * programadas, tal como pide el enunciado.
     */
    public void modificar(Medico medico) throws ExcepcionValidacion, IOException {
        if (medico.getId() == null) {
            throw new ExcepcionValidacion("El medico que intenta modificar no tiene identificador.");
        }
        validar(medico);

        if (!archivo.actualizar(medico)) {
            throw new ExcepcionValidacion("El medico ya no existe en el archivo.");
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
    }

    // =======================================================================
    // CONSULTAS
    // =======================================================================

    /** Listado completo, ordenado por apellidos y luego nombres. */
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

    /**
     * Busqueda libre por UUID, nombre, apellido o especialidad.
     * Si el texto es un UUID valido se resuelve con el indice en memoria
     * (una sola lectura); si no, se recorre el listado comparando sin importar
     * mayusculas ni acentos de capitalizacion.
     */
    public List<Medico> buscar(String texto) throws IOException {
        String consulta = (texto == null) ? "" : texto.trim();
        if (consulta.isEmpty()) {
            return listar();
        }

        // Camino rapido: el usuario pego un UUID completo.
        try {
            Medico porId = archivo.buscarPorId(UUID.fromString(consulta));
            List<Medico> unico = new ArrayList<>();
            if (porId != null) {
                unico.add(porId);
            }
            return unico;
        } catch (IllegalArgumentException noEsUuid) {
            // No era un UUID; se sigue con la busqueda por texto.
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

    /**
     * Especialidades distintas registradas, en orden alfabetico.
     * Sirve para llenar el combo de filtro de la interfaz sin quemar una lista
     * fija en el codigo.
     */
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

    // =======================================================================
    // VALIDACIONES
    // =======================================================================

    /**
     * Aplica todas las reglas sobre un medico antes de guardarlo.
     * Se valida ANTES de tocar el archivo: si algo falla, el archivo queda
     * intacto y no hay escrituras a medias.
     */
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

        // --- Longitudes, para que el texto no se recorte al escribirlo ---
        limitar(medico.getNombres(), ArchivoMedicos.LARGO_NOMBRES, "Nombres");
        limitar(medico.getApellidos(), ArchivoMedicos.LARGO_APELLIDOS, "Apellidos");
        limitar(medico.getEspecialidad(), ArchivoMedicos.LARGO_ESPECIALIDAD, "Especialidad");
        limitar(medico.getTelefono(), ArchivoMedicos.LARGO_TELEFONO, "Telefono");
        limitar(medico.getCorreo(), ArchivoMedicos.LARGO_CORREO, "Correo");

        // --- Correo: opcional, pero si viene debe tener forma de correo ---
        if (!medico.getCorreo().isEmpty() && !esCorreoValido(medico.getCorreo())) {
            throw new ExcepcionValidacion("El correo electronico no tiene un formato valido.");
        }

        // --- Horario ---
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

    /**
     * Validacion deliberadamente sencilla: un arroba, algo antes, algo despues
     * y un punto en el dominio. No se pretende cubrir el estandar completo de
     * direcciones de correo, solo atajar errores de captura.
     */
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
     * Convierte un texto "HH:mm" en LocalTime.
     * Vive en el servicio y no en la vista para que la regla de formato sea una
     * sola en todo el sistema.
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

    /** Da formato "HH:mm" a una hora, o cadena vacia si es nula. */
    public static String formatearHora(LocalTime hora) {
        return (hora == null) ? "" : hora.format(FORMATO_HORA);
    }
}
