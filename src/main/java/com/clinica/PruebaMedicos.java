package com.clinica;

import com.clinica.modelo.Medico;
import com.clinica.persistencia.ArchivoMedicos;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Prueba temporal de la capa de archivos (no es parte de la entrega). Ejecutar
 * dos veces seguidas: la segunda debe leer lo que dejó la primera.
 */
public class PruebaMedicos {

    private static final String RUTA = "datos/medicos.dat";

    public static void main(String[] args) throws Exception {

        try (ArchivoMedicos archivo = new ArchivoMedicos(RUTA)) {

            System.out.println("== Estado al abrir ==");
            System.out.println("Medicos vivos: " + archivo.cantidad());
            System.out.println("Posiciones totales: " + archivo.totalRegistros());
            System.out.println();

            // Solo siembra datos la primera vez, para poder verificar la persistencia.
            if (archivo.cantidad() == 0) {
                System.out.println("Archivo vacio, insertando medicos de prueba...");

                archivo.insertar(new Medico(null, "Ana Lucia", "Perez Gomez",
                        "Cardiologia", "5512-3344", "ana.perez@clinica.gt",
                        LocalTime.of(8, 0), LocalTime.of(16, 0), true));

                archivo.insertar(new Medico(null, "Jose Ramon", "Xitumul Chay",
                        "Pediatria", "4477-8899", "",
                        LocalTime.of(7, 30), LocalTime.of(13, 30), true));

                archivo.insertar(new Medico(null, "Maria Jose", "Ordonez Nunez",
                        "Traumatologia", "3322-1100", "mj.ordonez@clinica.gt",
                        LocalTime.of(13, 0), LocalTime.of(20, 0), false));

                System.out.println("Insertados 3 medicos.\n");
            }

            System.out.println("== Listado completo ==");
            List<Medico> medicos = archivo.listarTodos();
            for (Medico m : medicos) {
                System.out.println("  " + m);
            }
            System.out.println();

            // --- Busqueda directa por id (usa el indice en memoria) ---
            UUID idBuscado = medicos.get(0).getId();
            System.out.println("== Busqueda por id ==");
            System.out.println("  Buscando " + idBuscado);
            System.out.println("  Encontrado: " + archivo.buscarPorId(idBuscado));
            System.out.println();

            // --- Modificacion ---
            System.out.println("== Modificacion ==");
            Medico aModificar = archivo.buscarPorId(idBuscado);
            aModificar.setTelefono("5599-0011");
            aModificar.setEspecialidad("Cardiologia Pediatrica");
            archivo.actualizar(aModificar);
            System.out.println("  Ahora: " + archivo.buscarPorId(idBuscado));
            System.out.println();

            System.out.println("Bytes desperdiciados en huecos: " + archivo.bytesDesperdiciados());
        }

        // Segunda apertura: verifica persistencia y reutilización de huecos.
        System.out.println("\n=== Reabriendo el archivo (simula cerrar y volver a abrir la app) ===");

        try (ArchivoMedicos archivo = new ArchivoMedicos(RUTA)) {
            List<Medico> medicos = archivo.listarTodos();
            System.out.println("Medicos recuperados: " + medicos.size());
            for (Medico m : medicos) {
                System.out.println("  " + m);
            }

            System.out.println("\n== Prueba de borrado logico y reutilizacion de hueco ==");
            UUID idBorrar = medicos.get(1).getId();
            archivo.eliminar(idBorrar);
            System.out.println("  Borrado el segundo medico.");
            System.out.println("  Vivos: " + archivo.cantidad()
                    + " | Posiciones totales: " + archivo.totalRegistros()
                    + " | Desperdiciado: " + archivo.bytesDesperdiciados() + " bytes");

            archivo.insertar(new Medico(null, "Carlos Enrique", "Barrios Lopez",
                    "Dermatologia", "5566-7788", "c.barrios@clinica.gt",
                    LocalTime.of(9, 0), LocalTime.of(17, 0), true));

            System.out.println("  Insertado un medico nuevo.");
            System.out.println("  Vivos: " + archivo.cantidad()
                    + " | Posiciones totales: " + archivo.totalRegistros()
                    + "  <- no crecio: reutilizo el hueco");

            System.out.println("\n== Listado final ==");
            for (Medico m : archivo.listarTodos()) {
                System.out.println("  " + m);
            }
        }
    }
}
