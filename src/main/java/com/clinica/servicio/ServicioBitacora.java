package com.clinica.servicio;

import com.clinica.modelo.EntradaBitacora;
import com.clinica.modelo.TipoOperacion;
import com.clinica.persistencia.ArchivoBitacora;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Bitácora de operaciones: todos los módulos escriben aquí cada alta,
 * modificación o eliminación. Equivale al "reporte de logs" del enunciado.
 */
public class ServicioBitacora {

    public static final DateTimeFormatter FORMATO_MOMENTO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public static final String MODULO_MEDICOS = "Medicos";
    public static final String MODULO_PACIENTES = "Pacientes";
    public static final String MODULO_CITAS = "Citas";
    public static final String MODULO_REPORTES = "Reportes";

    private final ArchivoBitacora archivo;

    public ServicioBitacora(ArchivoBitacora archivo) {
        this.archivo = archivo;
    }

    /**
     * Anota una operación. No propaga IOException: la bitácora es secundaria;
     * si falla, la operación principal ya se completó con éxito. Se reporta en
     * stderr para no ocultarlo del todo.
     */
    public void registrar(String modulo, TipoOperacion operacion, String detalle) {
        try {
            String texto = (detalle == null) ? "" : detalle.trim();
            if (texto.length() > ArchivoBitacora.LARGO_DETALLE) {
                texto = texto.substring(0, ArchivoBitacora.LARGO_DETALLE);
            }

            archivo.insertar(new EntradaBitacora(
                    null, LocalDateTime.now(), modulo, operacion, texto));

        } catch (IOException e) {
            System.err.println("No se pudo escribir en la bitacora: " + e.getMessage());
        }
    }

    /**
     * Entradas de más reciente a más antigua. Se invierte el orden físico del
     * archivo (no por timestamp) porque varias operaciones seguidas caen en el
     * mismo segundo y su orden quedaría indefinido. Funciona porque la bitácora
     * nunca elimina entradas: el orden físico = cronológico.
     */
    public List<EntradaBitacora> listar() throws IOException {
        List<EntradaBitacora> entradas = archivo.listarTodos();
        Collections.reverse(entradas);
        return entradas;
    }

    public int cantidad() {
        return archivo.cantidad();
    }

    /** Da formato legible dd/MM/yyyy HH:mm:ss. */
    public static String formatearMomento(LocalDateTime momento) {
        return (momento == null) ? "" : momento.format(FORMATO_MOMENTO);
    }
}
