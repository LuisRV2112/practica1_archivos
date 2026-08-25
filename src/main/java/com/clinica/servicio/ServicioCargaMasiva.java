package com.clinica.servicio;

import com.clinica.modelo.Cita;
import com.clinica.modelo.Medico;
import com.clinica.modelo.Paciente;
import com.clinica.modelo.Sexo;
import com.clinica.modelo.TipoOperacion;
import com.clinica.modelo.TipoSangre;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Carga masiva desde CSV. Arma objetos y los pasa a los servicios de siempre,
 * de modo que cada fila pasa por las mismas validaciones que si se tecleara a
 * mano (identificaciones únicas, horarios coherentes, sin traslapes, etc.).
 */
public class ServicioCargaMasiva {

    private final ServicioMedicos servicioMedicos;
    private final ServicioPacientes servicioPacientes;
    private final ServicioCitas servicioCitas;
    private final ServicioBitacora bitacora;

    public ServicioCargaMasiva(ServicioMedicos servicioMedicos,
                               ServicioPacientes servicioPacientes,
                               ServicioCitas servicioCitas,
                               ServicioBitacora bitacora) {
        this.servicioMedicos = servicioMedicos;
        this.servicioPacientes = servicioPacientes;
        this.servicioCitas = servicioCitas;
        this.bitacora = bitacora;
    }

    // Médicos

    /**
     * Columnas esperadas:
     * nombres, apellidos, especialidad, telefono, correo, horaInicio, horaFin, activo
     */
    public ResultadoCarga cargarMedicos(File archivo) throws IOException {
        ResultadoCarga resultado = new ResultadoCarga("medicos");
        List<String[]> filas = LectorCsv.leer(archivo);

        for (int i = 0; i < filas.size(); i++) {
            String[] fila = filas.get(i);
            int numeroLinea = i + 1;

            if (i == 0 && LectorCsv.pareceEncabezado(fila, "nombres")) {
                continue;
            }

            try {
                if (fila.length < 7) {
                    throw new ExcepcionValidacion(
                            "se esperaban 8 columnas y llegaron " + fila.length);
                }

                LocalTime inicio = ServicioMedicos.interpretarHora(
                        LectorCsv.campo(fila, 5), "La hora de inicio");
                LocalTime fin = ServicioMedicos.interpretarHora(
                        LectorCsv.campo(fila, 6), "La hora de finalizacion");

                Medico medico = new Medico(
                        null,
                        LectorCsv.campo(fila, 0),
                        LectorCsv.campo(fila, 1),
                        LectorCsv.campo(fila, 2),
                        LectorCsv.campo(fila, 3),
                        LectorCsv.campo(fila, 4),
                        inicio,
                        fin,
                        interpretarSiNo(LectorCsv.campo(fila, 7), true));

                servicioMedicos.registrar(medico);
                resultado.contarInsertado();

            } catch (ExcepcionValidacion e) {
                resultado.agregarError(numeroLinea, e.getMessage());
            } catch (RuntimeException e) {
                resultado.agregarError(numeroLinea, "dato mal formado (" + e.getMessage() + ")");
            }
        }

        anotarEnBitacora(resultado);
        return resultado;
    }

    // Pacientes

    /**
     * Columnas esperadas:
     * identificacion, nombres, apellidos, nacimiento, sexo, telefono, correo, tipoSangre
     */
    public ResultadoCarga cargarPacientes(File archivo) throws IOException {
        ResultadoCarga resultado = new ResultadoCarga("pacientes");
        List<String[]> filas = LectorCsv.leer(archivo);

        for (int i = 0; i < filas.size(); i++) {
            String[] fila = filas.get(i);
            int numeroLinea = i + 1;

            if (i == 0 && LectorCsv.pareceEncabezado(fila, "identificacion")) {
                continue;
            }

            try {
                if (fila.length < 8) {
                    throw new ExcepcionValidacion(
                            "se esperaban 8 columnas y llegaron " + fila.length);
                }

                LocalDate nacimiento = ServicioPacientes.interpretarFecha(
                        LectorCsv.campo(fila, 3), "La fecha de nacimiento");

                Paciente paciente = new Paciente(
                        LectorCsv.campo(fila, 0),
                        LectorCsv.campo(fila, 1),
                        LectorCsv.campo(fila, 2),
                        nacimiento,
                        interpretarSexo(LectorCsv.campo(fila, 4)),
                        LectorCsv.campo(fila, 5),
                        LectorCsv.campo(fila, 6),
                        interpretarTipoSangre(LectorCsv.campo(fila, 7)));

                servicioPacientes.registrar(paciente);
                resultado.contarInsertado();

            } catch (ExcepcionValidacion e) {
                resultado.agregarError(numeroLinea, e.getMessage());
            } catch (RuntimeException e) {
                resultado.agregarError(numeroLinea, "dato mal formado (" + e.getMessage() + ")");
            }
        }

        anotarEnBitacora(resultado);
        return resultado;
    }

    // Citas

    /** La columna "medico" admite UUID o nombre completo (debe ser inequívoco). */
    public ResultadoCarga cargarCitas(File archivo) throws IOException {
        ResultadoCarga resultado = new ResultadoCarga("citas");
        List<String[]> filas = LectorCsv.leer(archivo);
        List<Medico> medicos = servicioMedicos.listar();

        for (int i = 0; i < filas.size(); i++) {
            String[] fila = filas.get(i);
            int numeroLinea = i + 1;

            if (i == 0 && LectorCsv.pareceEncabezado(fila, "identificacionPaciente")) {
                continue;
            }

            try {
                if (fila.length < 5) {
                    throw new ExcepcionValidacion(
                            "se esperaban 6 columnas y llegaron " + fila.length);
                }

                UUID idMedico = resolverMedico(LectorCsv.campo(fila, 1), medicos);

                LocalDate fecha = ServicioPacientes.interpretarFecha(
                        LectorCsv.campo(fila, 2), "La fecha de la cita");
                LocalTime hora = ServicioMedicos.interpretarHora(
                        LectorCsv.campo(fila, 3), "La hora de la cita");

                Cita cita = new Cita(
                        null,
                        LectorCsv.campo(fila, 0),
                        idMedico,
                        fecha,
                        hora,
                        LectorCsv.campo(fila, 4),
                        null,
                        LectorCsv.campo(fila, 5));

                servicioCitas.programar(cita);
                resultado.contarInsertado();

            } catch (ExcepcionValidacion e) {
                resultado.agregarError(numeroLinea, e.getMessage());
            } catch (RuntimeException e) {
                resultado.agregarError(numeroLinea, "dato mal formado (" + e.getMessage() + ")");
            }
        }

        anotarEnBitacora(resultado);
        return resultado;
    }

    // Plantillas

    /** Escribe tres plantillas de ejemplo con encabezado y una fila de muestra. */
    public void generarPlantillas(File carpeta) throws IOException {
        escribir(new File(carpeta, "plantilla_medicos.csv"), """
                nombres,apellidos,especialidad,telefono,correo,horaInicio,horaFin,activo
                Ana Lucia,Perez Gomez,Cardiologia,5512-3344,ana.perez@clinica.gt,08:00,16:00,si
                Jose Ramon,Xitumul Chay,Pediatria,4477-8899,,07:30,13:30,si
                """);

        escribir(new File(carpeta, "plantilla_pacientes.csv"), """
                identificacion,nombres,apellidos,nacimiento,sexo,telefono,correo,tipoSangre
                2547891230101,Carlos,Mendez Ruiz,15/03/1990,M,5511-2233,c.mendez@mail.gt,O+
                1985633440902,Sofia,Alvarez Tzoc,02/11/1985,F,4422-1100,,AB-
                """);

        escribir(new File(carpeta, "plantilla_citas.csv"), """
                identificacionPaciente,medico,fecha,hora,motivo,observaciones
                2547891230101,Ana Lucia Perez Gomez,15/09/2026,09:00,"Control de presion, rutina",Primera visita
                1985633440902,Jose Ramon Xitumul Chay,15/09/2026,10:00,Chequeo general,
                """);

        bitacora.registrar(ServicioBitacora.MODULO_REPORTES, TipoOperacion.EXPORTACION,
                "Se generaron las plantillas CSV en " + carpeta.getName());
    }

    private static void escribir(File destino, String contenido) throws IOException {
        try (Writer salida = Files.newBufferedWriter(destino.toPath(), StandardCharsets.UTF_8)) {
            salida.write(contenido);
        }
    }

    // Interpretación de campos

    /** Busca médico por UUID o por nombre completo; lanza si es ambiguo. */
    private UUID resolverMedico(String valor, List<Medico> medicos)
            throws ExcepcionValidacion {

        String buscado = (valor == null) ? "" : valor.trim();
        if (buscado.isEmpty()) {
            throw new ExcepcionValidacion("falta el medico");
        }

        // Primero intenta como UUID (inequívoco).
        try {
            UUID id = UUID.fromString(buscado);
            for (Medico m : medicos) {
                if (m.getId().equals(id)) {
                    return id;
                }
            }
            throw new ExcepcionValidacion("no existe un medico con el codigo " + buscado);

        } catch (IllegalArgumentException noEsCodigo) {
            // No era un UUID: se busca por nombre.
        }

        Medico encontrado = null;
        for (Medico m : medicos) {
            if (m.getNombreCompleto().equalsIgnoreCase(buscado)) {
                if (encontrado != null) {
                    throw new ExcepcionValidacion(
                            "hay mas de un medico llamado '" + buscado
                                    + "'; use el codigo para distinguirlos");
                }
                encontrado = m;
            }
        }

        if (encontrado == null) {
            throw new ExcepcionValidacion("no se encontro el medico '" + buscado + "'");
        }
        return encontrado.getId();
    }

    /** Acepta M/F, masculino/femenino, hombre/mujer. */
    private static Sexo interpretarSexo(String valor) throws ExcepcionValidacion {
        String texto = valor.trim().toLowerCase();

        if (texto.startsWith("m") && !texto.startsWith("mu")) {
            return Sexo.MASCULINO;
        }
        if (texto.startsWith("f") || texto.startsWith("mu")) {
            return Sexo.FEMENINO;
        }
        throw new ExcepcionValidacion(
                "sexo no reconocido: '" + valor + "' (use M o F)");
    }

    private static TipoSangre interpretarTipoSangre(String valor) throws ExcepcionValidacion {
        TipoSangre tipo = TipoSangre.porEtiqueta(valor.trim());
        if (tipo == null) {
            throw new ExcepcionValidacion(
                    "tipo de sangre no reconocido: '" + valor + "' (use O+, A-, AB+, etc.)");
        }
        return tipo;
    }

    /** Acepta si/no, true/false, 1/0, activo/inactivo. */
    private static boolean interpretarSiNo(String valor, boolean porOmision) {
        String texto = valor.trim().toLowerCase();
        if (texto.isEmpty()) {
            return porOmision;
        }
        return texto.startsWith("s") || texto.startsWith("t")
                || texto.startsWith("1") || texto.startsWith("a");
    }

    private void anotarEnBitacora(ResultadoCarga resultado) {
        bitacora.registrar(ServicioBitacora.MODULO_REPORTES, TipoOperacion.CREACION,
                "Carga masiva de " + resultado.getEntidad() + ": "
                        + resultado.getInsertados() + " cargados, "
                        + resultado.getRechazados() + " rechazados");
    }
}
