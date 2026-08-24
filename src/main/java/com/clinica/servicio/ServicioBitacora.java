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
 * Bitacora de operaciones del sistema.
 *
 * Todos los modulos escriben aqui cada vez que crean, actualizan o eliminan
 * algo. Es lo que el enunciado pide como "reporte de logs con todas las
 * interacciones realizadas por los usuarios en cada uno de los modulos".
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
     * Anota una operacion en la bitacora.
     *
     * Este metodo NO propaga IOException a proposito. La bitacora es un registro
     * secundario: si falla al escribirse, la operacion principal (que ya se
     * completo con exito) no deberia reportarse como fallida al usuario. El
     * problema se deja constar en la salida de error para no ocultarlo del todo.
     *
     * Es una decision consciente entre dos males: perder una linea de bitacora,
     * o mostrarle al usuario un error sobre algo que en realidad si funciono.
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
     * Entradas de la bitacora, de la mas reciente a la mas antigua.
     *
     * No se ordena por la marca de tiempo, sino invirtiendo el orden fisico del
     * archivo. La razon: la marca tiene precision de segundos, y varias
     * operaciones seguidas caen en el mismo segundo, con lo que su orden
     * relativo quedaria indefinido.
     *
     * Invertir el archivo funciona porque la bitacora NUNCA elimina entradas:
     * al no haber huecos que reutilizar, cada registro nuevo se agrega al final
     * y el orden fisico es exactamente el orden cronologico.
     */
    public List<EntradaBitacora> listar() throws IOException {
        List<EntradaBitacora> entradas = archivo.listarTodos();
        Collections.reverse(entradas);
        return entradas;
    }

    public int cantidad() {
        return archivo.cantidad();
    }

    /** Da formato legible a una marca de tiempo. */
    public static String formatearMomento(LocalDateTime momento) {
        return (momento == null) ? "" : momento.format(FORMATO_MOMENTO);
    }
}
