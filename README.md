# Sistema de Gestión de Clínica Médica

Práctica 1 — Manejo e Implementación de Archivos
Ingeniería en Ciencias y Sistemas, USAC — Centro Universitario de Occidente

Aplicación de escritorio en Java Swing para administrar pacientes, médicos y
citas médicas. La persistencia se realiza sobre archivos binarios manipulados
con `RandomAccessFile`, sin gestores de bases de datos ni librerías externas.

## Requisitos

- JDK 21
- Maven 3.8 o superior

## Estructura del proyecto

```
clinica-medica/
├── pom.xml
├── datos/                          Archivos .dat generados en tiempo de ejecución
└── src/main/java/com/clinica/
    ├── modelo/                     Objetos de dominio (POJOs, sin lógica de archivos)
    ├── persistencia/               Lectura y escritura binaria con RandomAccessFile
    ├── servicio/                   Validaciones y reglas de negocio
    └── vista/                      Interfaz gráfica en Swing
```

La separación en capas es intencional: la vista nunca accede al archivo
directamente, siempre pasa por la capa de servicio.

## Formato del archivo de médicos

Registros de longitud fija, lo que permite llegar a cualquier registro con un
único `seek()`.

```
[ CABECERA 12 bytes ][ registro 0 ][ registro 1 ][ registro 2 ] ...
```

**Cabecera**

| Campo            | Bytes | Descripción                                   |
|------------------|-------|-----------------------------------------------|
| version          | 4     | Versión del formato del archivo               |
| cantidadActivos  | 4     | Registros vivos                               |
| primerLibre      | 4     | Índice del primer hueco reutilizable, o -1    |

**Registro de médico — 380 bytes**

| Campo          | Bytes | Descripción                                      |
|----------------|-------|--------------------------------------------------|
| estadoRegistro | 1     | 1 = ocupado, 0 = borrado                         |
| siguienteLibre | 4     | Encadena la lista de huecos (solo si está libre) |
| uuidMsb        | 8     | Bits altos del UUID                              |
| uuidLsb        | 8     | Bits bajos del UUID                              |
| nombres        | 80    | 40 caracteres                                    |
| apellidos      | 80    | 40 caracteres                                    |
| especialidad   | 60    | 30 caracteres                                    |
| telefono       | 30    | 15 caracteres                                    |
| correo         | 100   | 50 caracteres                                    |
| horaInicio     | 4     | Segundos desde medianoche                        |
| horaFin        | 4     | Segundos desde medianoche                        |
| activo         | 1     | Estado del médico en la clínica                  |

### Decisiones de diseño

**Borrado lógico.** Eliminar un registro solo cambia su byte de estado. Un
borrado físico obligaría a desplazar todos los registros posteriores e
invalidaría las posiciones ya calculadas.

**Lista de espacios libres.** Los huecos se encadenan entre sí: la cabecera
apunta al primero y cada hueco guarda el índice del siguiente. Al insertar se
reutiliza el hueco más reciente, de modo que el archivo no crece
indefinidamente.

**Índice en memoria.** Al abrir el archivo se recorre una sola vez leyendo
únicamente el estado y el UUID de cada registro, y se arma un
`HashMap<UUID, Integer>`. Buscar por identificador cuesta un solo `seek`.

**Cadenas de longitud fija.** Se usa `writeChars` (2 bytes por carácter) en
lugar de `writeUTF`, porque este último antepone la longitud y produce
registros de tamaño variable, lo que rompería el cálculo de posiciones.

## Compilación y ejecución

```bash
mvn compile

# Prueba de la capa de persistencia en consola
mvn compile exec:java -Dexec.mainClass=com.clinica.PruebaMedicos
```

Generar el JAR ejecutable:

```bash
mvn clean package
java -jar target/clinica-medica.jar
```

## Estado actual

- [x] Capa base de persistencia con registros de longitud fija
- [x] Módulo de médicos — persistencia
- [ ] Módulo de médicos — servicio e interfaz
- [ ] Módulo de pacientes
- [ ] Módulo de citas
- [ ] Módulo de reportes
- [ ] Bitácora de operaciones
