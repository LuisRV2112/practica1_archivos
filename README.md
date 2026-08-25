# Sistema de Gestión de Clínica Médica

**Práctica 1 — Manejo e Implementación de Archivos**
Universidad de San Carlos de Guatemala · Centro Universitario de Occidente
Ingeniería en Ciencias y Sistemas

Aplicación de escritorio en **Java 21 con Swing** para administrar pacientes,
médicos y citas médicas. La persistencia se realiza íntegramente sobre archivos
binarios manipulados con `RandomAccessFile`, sin gestores de bases de datos ni
librerías externas: todos los índices y estructuras de acceso son código propio.

---

## Ejecución rápida

```bash
mvn clean package
java -jar target/clinica-medica.jar
```

Requiere **JDK 21** y **Maven 3.8** o superior.

> Los archivos de datos se crean en una carpeta `datos` **relativa al directorio
> desde donde se ejecuta el JAR**. Se recomienda ejecutarlo desde la terminal,
> en la carpeta donde se desee guardar la información.

---

## Documentación

| Documento | Contenido |
|---|---|
| [`docs/MANUAL-USUARIO.md`](docs/MANUAL-USUARIO.md) | Cómo usar el sistema, pantalla por pantalla |
| [`docs/MANUAL-TECNICO.md`](docs/MANUAL-TECNICO.md) | Arquitectura, formatos de archivo y decisiones de diseño |
| [`docs/uml/`](docs/uml/) | Diagramas de clases, secuencia y casos de uso |

---

## Módulos

| Módulo | Funcionalidad |
|---|---|
| **Médicos** | Alta, modificación, activación/desactivación, búsqueda y filtros |
| **Pacientes** | Alta, modificación, baja lógica/reactivación, búsqueda y filtros |
| **Citas** | Programación con validación de horarios y traslapes, cambios de estado |
| **Reportes** | 15 reportes con exportación a CSV y TXT |
| **Bitácora** | Registro de todas las operaciones realizadas |
| **Carga masiva** | Importación desde CSV con plantillas y reporte de errores por línea |

---

## Organización de archivos

La decisión de diseño central del proyecto: **cada entidad usa la organización
que corresponde a cómo se la consulta**, no una organización uniforme.

| Entidad | Organización | Búsqueda | Justificación |
|---|---|---|---|
| Pacientes | **Directo (hash)** | O(1) | Se busca por identificación exacta; no se requiere orden |
| Médicos | **Secuencial indexado** | O(log n) | Pocas altas, muchas consultas, reportes que recorren todo |
| Citas | **Multianillo** | O(k) | Casi siempre se piden "las citas de este médico / paciente" |
| Bitácora | **Secuencial** | — | Solo se anexa y se lee completa |

Además, el espacio libre dentro de cada archivo se administra con un **apilo
(pila LIFO)**: los huecos que deja un borrado se encadenan entre sí y se
reutilizan con *push* y *pop* en O(1).

Con esto, las cinco estructuras del curso están implementadas donde tienen
sentido. El detalle de cada una —función de dispersión, lápidas, redispersión,
búsqueda binaria, enlaces de anillo— está en el
[manual técnico](docs/MANUAL-TECNICO.md#3-organización-de-archivos-por-entidad).

---

## Arquitectura

Cuatro capas con una regla de dependencia estricta:

```
vista  ──►  servicio  ──►  persistencia  ──►  modelo
```

La vista **nunca** abre un archivo; la persistencia **nunca** valida una regla
de negocio.

```
src/main/java/com/clinica/
├── Main.java
├── modelo/          Objetos de dominio y enumeraciones
├── persistencia/    Conversión a bytes, organizaciones de archivo, índices
├── servicio/        Validaciones, reglas de negocio, reportes, bitácora
└── vista/           Interfaz Swing y componentes propios
```

`ArchivoBase<ID, T>` concentra la mecánica común —cabecera, registros de
longitud fija, borrado lógico, apilo de huecos y cálculo de posiciones— y deja
abierto un único punto: cómo se localiza un registro a partir de su clave. Cada
subclase resuelve eso a su manera, y una que no redefina nada obtiene un archivo
secuencial funcional.

Ningún servicio de entidad depende de otro servicio de entidad: todos dependen
de **archivos**. Si `ServicioCitas` dependiera de `ServicioMedicos` y este de
aquel, habría una dependencia circular imposible de construir.

---

## Integridad de la información

No hay motor de base de datos que la garantice, así que la aplica la capa de
servicio antes de escribir:

- El paciente y el médico de una cita deben existir y estar activos
- La cita debe caber en el horario de atención del médico
- Ni el médico ni el paciente pueden tener dos citas traslapadas
- No se da de baja a un paciente con citas programadas
- Cambiar el horario de un médico se rechaza si dejaría fuera citas ya programadas
- La identificación de paciente es única

Todas las validaciones se ejecutan **antes** de tocar el archivo: si alguna
falla, el archivo queda intacto y no hay escrituras a medias.

---


## Carga masiva desde CSV

El menú **Datos** permite importar médicos, pacientes y citas, y generar
plantillas de ejemplo con el formato correcto.

La carga no escribe en los archivos por su cuenta: arma los objetos y se los
pasa a los servicios de siempre, así que un registro importado pasa por
exactamente las mismas validaciones que uno tecleado a mano. Cada fila se
procesa por separado — las válidas entran y las inválidas se reportan con su
número de línea y el motivo, en lugar de rechazar el archivo entero.

El lector de CSV es simétrico con el exportador: recorre el texto carácter por
carácter llevando cuenta de si está dentro de comillas, de modo que un campo con
comas, comillas o saltos de línea sobrevive el ida y vuelta.

---

## Archivos generados

Dentro de `datos/`:

| Archivo | Contenido |
|---|---|
| `medicos.dat` · `medicos.idx` | Médicos y su índice ordenado |
| `pacientes.dat` · `pacientes.hash` | Pacientes y su tabla de dispersión |
| `citas.dat` | Citas |
| `citas_medico.idx` · `citas_paciente.hash` | Cabezas de los anillos |
| `bitacora.dat` | Registro de operaciones |

Los archivos de índice son información **derivada**: si se pierden o quedan a
medias por un cierre abrupto, el sistema los reconstruye al abrir recorriendo el
archivo de datos una sola vez.

---

## Estado del proyecto

- [x] Capa base de persistencia con registros de longitud fija
- [x] Módulo de médicos — archivo secuencial indexado
- [x] Módulo de pacientes — archivo directo con hash
- [x] Módulo de citas — archivo multianillo
- [x] Módulo de reportes — 15 reportes con exportación a CSV y TXT
- [x] Bitácora de operaciones
- [x] Carga masiva desde CSV
- [x] Documentación técnica y manuales