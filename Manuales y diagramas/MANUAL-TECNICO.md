# Manual Técnico
## Sistema de Gestión de Clínica Médica

Universidad de San Carlos de Guatemala — Centro Universitario de Occidente
Ingeniería en Ciencias y Sistemas — Manejo e Implementación de Archivos  
Luis Alejandro Regalado - 202131920

---

## Índice

1. [Descripción general](#1-descripción-general)
2. [Arquitectura](#2-arquitectura)
3. [Organización de archivos por entidad](#3-organización-de-archivos-por-entidad)
4. [Formato físico de los registros](#4-formato-físico-de-los-registros)
5. [Estructuras de datos implementadas](#5-estructuras-de-datos-implementadas)
6. [Integridad de la información](#6-integridad-de-la-información)
7. [Manejo de excepciones](#7-manejo-de-excepciones)
8. [Compilación y despliegue](#8-compilación-y-despliegue)
9. [Cómo extender el sistema](#9-cómo-extender-el-sistema)

---

## 1. Descripción general

Aplicación de escritorio en **Java 21 con Swing** para administrar pacientes,
médicos y citas de una clínica médica.

La persistencia se realiza íntegramente sobre **archivos binarios manipulados
con `RandomAccessFile`**. No se utiliza ningún gestor de bases de datos ni
librería externa de manejo de archivos: toda la lectura y escritura, los índices
y las estructuras de acceso son código propio.

### Restricciones técnicas respetadas

| Restricción del enunciado | Cómo se cumple |
|---|---|
| Java 21 con Swing | `maven.compiler.release = 21`; interfaz en Swing puro |
| `RandomAccessFile` | Única API de acceso a archivos usada |
| Sin gestores de base de datos | No hay ninguna dependencia de BD |
| Sin librerías externas de archivos | El `pom.xml` no declara **ninguna** dependencia |
| Código claro y documentado | Comentarios explicando el *porqué* de cada decisión |

---

## 2. Arquitectura

El sistema está organizado en cuatro capas con una regla de dependencia
estricta:

```
vista  ──►  servicio  ──►  persistencia  ──►  modelo
```

Cada capa conoce únicamente a la de abajo. La vista **nunca** abre un archivo;
la persistencia **nunca** valida una regla de negocio.

| Paquete | Responsabilidad |
|---|---|
| `com.clinica.modelo` | Objetos de dominio y enumeraciones. Sin lógica. |
| `com.clinica.persistencia` | Conversión a bytes, organización de archivos, índices. |
| `com.clinica.servicio` | Validaciones, reglas de negocio, reportes, bitácora. |
| `com.clinica.vista` | Interfaz gráfica y componentes propios. |

### Por qué los servicios dependen de archivos y no de otros servicios

`ServicioCitas` necesita consultar médicos y pacientes. `ServicioMedicos`
necesita consultar citas para validar cambios de horario. Si cada uno dependiera
del servicio del otro habría una **dependencia circular** imposible de
construir.

La solución es que todos los servicios dependan únicamente de **archivos**, que
no dependen de nadie:

```
ServicioCitas     ──►  ArchivoCitas, ArchivoMedicos, ArchivoPacientes
ServicioMedicos   ──►  ArchivoMedicos, ArchivoCitas
ServicioPacientes ──►  ArchivoPacientes, ArchivoCitas
```

`ServicioReportes` y `ServicioCargaMasiva` sí dependen de los otros servicios,
pero nadie depende de ellos: son hojas del grafo y no cierran ningún ciclo.

---

## 3. Organización de archivos por entidad

Esta es la decisión de diseño central del proyecto. **Cada entidad usa la
organización que corresponde a cómo se la consulta**, no una organización
uniforme.

| Entidad | Organización | Búsqueda | Justificación |
|---|---|---|---|
| Pacientes | **Directo (hash)** | O(1) | Se busca por identificación exacta; no se requiere orden |
| Médicos | **Secuencial indexado** | O(log n) | Pocas altas, muchas consultas, reportes que recorren todo |
| Citas | **Multianillo** | O(k) | Casi siempre se piden "las citas de este médico / paciente" |
| Bitácora | **Secuencial** | — | Solo se anexa y se lee completa |

### 3.1 Pacientes — Archivo directo

Al paciente se le busca por su número de identificación, y se le busca **exacto**:
llega a la clínica, da su número y hay que traer su expediente. No se consultan
rangos ni se requiere recorrerlos en orden de identificación.

Ese patrón —clave exacta, muchas consultas, sin necesidad de orden— es
precisamente para lo que sirve un archivo directo. La posición del registro se
**calcula** a partir de la clave mediante una función de dispersión.

**Archivo de índice:** `pacientes.hash`

```
[ CABECERA 8 bytes ][ cubeta 0 ][ cubeta 1 ] ... [ cubeta capacidad-1 ]

Cabecera:  int capacidad, int cantidad
Cubeta:    byte estado (0 libre, 1 ocupada, 2 borrada) + char[15] clave + int registro
```

**Función de dispersión.** Acumula los caracteres multiplicando por 31 en cada
paso. El 31 es primo e impar, lo que evita que caracteres distintos se anulen
entre sí y reparte mejor las claves.

**Colisiones: sondeo lineal.** Si la cubeta natural está ocupada por otra clave,
se prueba la siguiente.

**Borrados: lápidas.** Una cubeta borrada **no** se marca como libre, sino con
una marca propia. Si se marcara libre, una búsqueda que llegara ahí se detendría
y no encontraría claves guardadas más adelante por sondeo. La lápida significa
"aquí no está, pero sigue buscando".

**Redispersión.** Al superar el 70 % de ocupación las colisiones se disparan y
el O(1) deja de cumplirse. La tabla duplica su capacidad al siguiente número
primo y recoloca todas las claves.

> **Verificado:** con 200 pacientes la tabla creció de 61 a 521 cubetas y los
> 200 se siguen localizando. Tras borrar 50 del medio, los 200 resultados
> siguen siendo correctos: las lápidas no rompen el sondeo.

### 3.2 Médicos — Archivo secuencial indexado

Los médicos son pocos, casi nunca se dan de alta y se consultan constantemente:
cada cita programada obliga a verificar que el médico exista, esté activo y que
la hora caiga en su horario. Además varios reportes recorren la lista completa.

El archivo de **datos** se mantiene secuencial —óptimo para recorrerlo entero— y
un archivo de **índice** aparte se mantiene ordenado por clave, permitiendo
**búsqueda binaria**.

**Archivo de índice:** `medicos.idx`

```
[ CABECERA 4 bytes ][ entrada 0 ][ entrada 1 ] ... ordenadas por clave

Cabecera:  int cantidad
Entrada:   long claveMsb + long claveLsb + int registro     (20 bytes)
```

**Compromiso.** Se paga al insertar (hay que abrir hueco desplazando las
entradas posteriores para no perder el orden) y se cobra al buscar. En una
clínica se consulta mucho más de lo que se contrata personal.

> **Verificado:** con 100 médicos, **7 comparaciones máximas** por búsqueda
> frente a 100 de un barrido secuencial.

**Detalle de implementación.** El punto medio se calcula con `(inicio + fin) >>> 1`
y no con `(inicio + fin) / 2`. Si la suma desborda a negativo, la división con
signo produce un índice inválido. Es un error que estuvo presente durante años
en la librería estándar de Java.

### 3.3 Citas — Archivo multianillo

A una cita casi nunca se le busca por su propio identificador. Lo que el sistema
pregunta todo el tiempo es *"todas las citas de este médico"* y *"todas las
citas de este paciente"*: la agenda del día, el historial del expediente, la
validación de traslapes y varios reportes.

Con un archivo secuencial cada una de esas consultas obliga a leer el archivo
completo. El multianillo **encadena** los registros del mismo grupo: cada cita
guarda la posición de la siguiente cita de su mismo médico y la de su mismo
paciente.

Se llama *multi*anillo porque cada registro pertenece a **varias cadenas a la
vez**, sin duplicar un solo byte de información.

**Cabezas de cadena:**

```
citas_medico.idx     índice ordenado:  UUID del médico  -> primera cita
citas_paciente.hash  índice hash:      identificación   -> primera cita
```

Las citas nuevas se enlazan **al frente** del anillo: enlazar cuesta O(1) sin
recorrer la cadena hasta el final.

**Punto delicado.** `actualizar()` está sobrescrito en `ArchivoCitas`, porque
`escribirCampos` deja los enlaces en −1: el objeto `Cita` del dominio no los
conoce ni debe conocerlos. Al reescribir hay que leer los enlaces antes y
restaurarlos después; de lo contrario, modificar el motivo de una cita rompería
los dos anillos.

> **Verificado:** con 48 citas, 4 médicos y 10 pacientes, los 14 anillos
> devuelven exactamente lo mismo que filtrar el archivo completo. Se probó
> desenlazar la cabeza, desenlazar del medio, vaciar un anillo completo,
> reutilizar huecos y reconstruir tras reabrir.

### 3.4 Bitácora — Archivo secuencial

Caso de libro del archivo secuencial: solo se anexa al final, nunca se modifica
ni se elimina, y se lee entera. No necesita índice porque nunca se busca una
entrada suelta.

Como consecuencia, el apilo de espacios libres de este archivo siempre está
vacío y **el orden físico coincide exactamente con el cronológico**. Por eso
`listar()` invierte el orden del archivo en lugar de ordenar por marca de
tiempo: la marca tiene precisión de segundos y varias operaciones seguidas caen
en el mismo segundo.

---

## 4. Formato físico de los registros

### Estructura común

```
[ CABECERA: 12 bytes ][ registro 0 ][ registro 1 ][ registro 2 ] ...

Cabecera:
    int version          (4)   versión del formato
    int cantidadActivos  (4)   registros vivos
    int primerLibre      (4)   cima del apilo de huecos, o -1
```

Todo registro empieza igual:

| Campo | Bytes | Descripción |
|---|---|---|
| estadoRegistro | 1 | 1 = ocupado, 0 = borrado |
| siguienteLibre | 4 | Enlaza el apilo cuando el registro está libre |

A continuación van los campos propios, **empezando siempre por el
identificador**. Esa convención permite leer solo los primeros bytes de un
registro para saber a quién pertenece, sin cargarlo completo — de lo que se
aprovechan el barrido secuencial y la reconstrucción de los índices.

### Médico — 380 bytes

| Campo | Bytes |
|---|---|
| uuid (dos `long`) | 16 |
| nombres | 80 (40 caracteres) |
| apellidos | 80 (40 caracteres) |
| especialidad | 60 (30 caracteres) |
| telefono | 30 (15 caracteres) |
| correo | 100 (50 caracteres) |
| horaInicio | 4 (segundos desde medianoche) |
| horaFin | 4 |
| activo | 1 |

### Paciente — 336 bytes

| Campo | Bytes |
|---|---|
| identificacion | 30 (15 caracteres) |
| nombres | 80 |
| apellidos | 80 |
| fechaNacimiento | 8 (días desde 1970-01-01) |
| sexo | 1 (código del enum) |
| telefono | 30 |
| correo | 100 |
| tipoSangre | 1 (código del enum) |
| activo | 1 |

### Cita — 588 bytes

| Campo | Bytes |
|---|---|
| uuid de la cita | 16 |
| **siguienteDelMedico** | 4 (anillo 1) |
| **siguienteDelPaciente** | 4 (anillo 2) |
| identificacionPaciente | 30 |
| uuid del médico | 16 |
| fecha | 8 |
| horaInicio | 4 |
| motivo | 200 (100 caracteres) |
| estado | 1 |
| observaciones | 300 (150 caracteres) |

### Bitácora — 370 bytes

| Campo | Bytes |
|---|---|
| uuid | 16 |
| momento | 8 (segundos desde 1970-01-01, UTC) |
| modulo | 40 (20 caracteres) |
| operacion | 1 |
| detalle | 300 (150 caracteres) |

### Decisiones de codificación

**Cadenas de longitud fija con `writeChars`.** Dos bytes por carácter (UTF-16).
No se usa `writeUTF` porque antepone la longitud y produce registros de tamaño
variable, lo que rompería el cálculo de posiciones por multiplicación. El costo
en espacio es irrelevante frente a la ventaja de poder llegar a cualquier
registro con un solo `seek`.

**UUID como dos `long`.** 16 bytes en lugar de los 72 que ocuparía su
representación textual de 36 caracteres. Se reconstruye exacto con
`new UUID(msb, lsb)`.

**Fechas como `long`, horas como `int`.** Días desde 1970-01-01 y segundos desde
medianoche. Ocupan un tamaño fijo y admiten fechas anteriores a 1970 (días
negativos), lo que importa para las fechas de nacimiento.

**Marcas de tiempo en UTC.** No porque la clínica opere en UTC, sino porque lo
importante es usar **siempre el mismo desplazamiento**: con la zona del sistema,
mover la máquina de zona haría que las marcas ya guardadas se leyeran corridas.

**Enums con código explícito.** `Sexo`, `TipoSangre`, `EstadoCita` y
`TipoOperacion` guardan un código numérico propio, **no** su `ordinal()`. Si
alguien reordenara las constantes del enum, los ordinales cambiarían y todos los
archivos ya escritos quedarían mal interpretados.

**Versión de formato.** La cabecera guarda un número de versión que se
incrementa cada vez que cambia la disposición de los bytes. Un archivo de una
versión anterior se **rechaza con un mensaje claro** en lugar de leerse mal y
mostrar datos corruptos.

---

## 5. Estructuras de datos implementadas

Las cinco estructuras vistas en el curso están presentes, cada una donde
corresponde:

| Estructura | Dónde se usa | Complejidad |
|---|---|---|
| **Apilo (pila LIFO)** | Espacios libres de todos los archivos | push/pop O(1) |
| **Secuencial** | Bitácora | O(n) |
| **Secuencial indexado** | Médicos | O(log n) |
| **Directo (hash)** | Pacientes | O(1) |
| **Multianillo** | Citas | O(k) |

### El apilo de espacios libres

Los huecos que deja un borrado forman una **pila LIFO**: la cabecera apunta a la
cima y cada hueco guarda, en su campo `siguienteLibre`, el hueco de abajo.

- **Liberar** un registro es un *push*: el hueco se vuelve la nueva cima
- **Reservar** espacio es un *pop*: se toma la cima y la cabecera pasa al siguiente

Ambas operaciones son O(1) y no recorren nada. Es la misma estructura LIFO del
curso, aplicada a la administración del espacio del archivo.

### Borrado lógico, no físico

Eliminar un registro solo cambia su byte de estado. Un borrado físico obligaría
a desplazar todos los registros posteriores —costoso en disco— y a recalcular
todas las posiciones ya conocidas, invalidando los índices.

### Reconstrucción de índices

Los índices son **información derivada**: siempre se pueden recalcular a partir
de los datos. Si el programa se cierra de golpe y un índice queda a medias, o si
se borra el archivo `.hash` o `.idx`, el sistema lo detecta al abrir y lo
reconstruye recorriendo el archivo de datos una sola vez. No se pierde nada.

---

## 6. Integridad de la información

No hay motor de base de datos que garantice que una cita apunte a un paciente y
un médico existentes. Esa integridad referencial la garantiza la capa de
servicio antes de escribir:

| Regla | Dónde se aplica |
|---|---|
| El paciente de una cita debe existir y estar activo | `ServicioCitas.validar` |
| El médico de una cita debe existir y estar activo | `ServicioCitas.validar` |
| La cita debe caber en el horario del médico | `ServicioCitas.validar` |
| Un médico no puede tener dos citas traslapadas | `ServicioCitas.seTraslapan` |
| Un paciente no puede estar en dos lugares a la vez | `ServicioCitas.seTraslapan` |
| No se da de baja un paciente con citas programadas | `ServicioPacientes.darDeBaja` |
| Cambiar el horario no debe invalidar citas programadas | `ServicioMedicos.modificar` |
| La identificación de paciente es única | Índice de `ArchivoPacientes` |

**Validar antes de escribir.** Todas las validaciones se ejecutan completas
antes de tocar el archivo. Si alguna falla, el archivo queda intacto: no existen
escrituras a medias que lo dejen inconsistente.

**Duración de consulta.** El enunciado solo pide la hora de inicio, pero sin una
duración no se puede detectar un choque de horarios. Se define un bloque
estándar de 30 minutos en `ServicioCitas.DURACION_MINUTOS`, y dos citas se
traslapan si sus intervalos se solapan: `a1 < b2 && b1 < a2`.

Una cita **atendida o cancelada libera su horario**: solo las citas en estado
*Programada* bloquean espacio.

**Borrado lógico en la capa de dominio.** Tanto `Medico` como `Paciente` tienen
un campo `activo` en su **modelo**, no solo el byte de registro vivo/borrado del
archivo. Son cosas distintas: el byte es un detalle de cómo se guardan los
bytes; dar de baja a un paciente es un hecho del negocio que la clínica
consulta, filtra y reporta.

Por eso "eliminar" un paciente lo da de baja y conserva su expediente: así sus
citas históricas siguen apuntando a alguien que existe.

---

## 7. Manejo de excepciones

El sistema distingue dos tipos de error y los trata distinto:

| Excepción | Qué representa | Cómo se muestra |
|---|---|---|
| `ExcepcionValidacion` | El usuario ingresó algo inválido | Advertencia amarilla con el motivo |
| `IOException` | Falla técnica al acceder al archivo | Error rojo con el detalle |

`ExcepcionValidacion` es **revisada** (*checked*) a propósito: obliga al
compilador a que la vista la atrape y le muestre el mensaje al usuario, en lugar
de dejar que el programa truene sin explicación.

**Excepción a la regla: la bitácora.** `ServicioBitacora.registrar()` **no**
propaga `IOException`. Es una decisión consciente: la bitácora es un registro
secundario y, si falla al escribirse, la operación principal —que ya se completó
con éxito— no debería reportarse como fallida al usuario. El problema se deja
constar en la salida de error para no ocultarlo del todo.

**Validación estricta de fechas.** `DateTimeFormatter` opera por omisión en modo
*SMART*: ante una fecha inexistente como `31/02/1990` no falla, sino que la
"corrige" en silencio al 28/02 y guardaría una fecha que el usuario nunca
escribió. Se configura con `ResolverStyle.STRICT`, lo que a su vez obliga a usar
`uuuu` en lugar de `yyyy` en el patrón.

---

## 8. Compilación y despliegue

### Requisitos

- JDK 21
- Maven 3.8 o superior

### Compilar

```bash
mvn clean compile
```

### Generar el JAR ejecutable

```bash
mvn clean package
```

Produce `target/clinica-medica.jar` con la clase principal declarada en el
manifiesto.

### Ejecutar

```bash
java -jar clinica-medica.jar
```

> Los archivos de datos se crean en una carpeta `datos` **relativa al directorio
> desde donde se ejecuta el JAR**, no donde está el JAR. Se recomienda
> ejecutarlo desde la terminal, en la carpeta donde se desee guardar la
> información.

### Nota sobre `maven.compiler.release`

El `pom.xml` usa `release` y no `source`/`target`. La diferencia importa: con
`release` el compilador **impide** usar APIs que no existan en Java 21, aunque
se compile con un JDK más nuevo. Con `source`/`target` compilaría igual y luego
fallaría en una máquina con Java 21.

### Estructura del proyecto

```
clinica-medica/
├── pom.xml
├── README.md
├── datos/                     Archivos .dat, .idx y .hash (generados)
├── docs/
│   ├── MANUAL-USUARIO.md
│   ├── MANUAL-TECNICO.md
│   └── uml/
└── src/main/java/com/clinica/
    ├── Main.java
    ├── modelo/
    ├── persistencia/
    ├── servicio/
    └── vista/
```

---

## 9. Cómo extender el sistema

### Agregar una entidad nueva

1. Crear la clase de dominio en `modelo/`
2. Crear `ArchivoXxx extends ArchivoBase<ID, Xxx>` implementando cuatro métodos:
   `escribirCampos`, `leerCampos`, `leerId` e `idDe`
3. Si necesita una organización distinta a la secuencial, redefinir `localizar`,
   `indexarInsercion`, `indexarEliminacion` y `prepararIndice`
4. Crear `ServicioXxx` con las validaciones
5. Crear `PanelXxx` y agregarlo a `VentanaPrincipal`

Una subclase que no redefina nada obtiene un archivo secuencial funcional.

### Agregar un reporte

Los quince reportes devuelven el mismo tipo `Reporte` (título, columnas y filas
de texto). Gracias a eso hay **una** pantalla, **un** modelo de tabla y **un**
exportador para todos.

Agregar uno nuevo son dos pasos:

1. Un método en `ServicioReportes` que devuelva un `Reporte`
2. Una constante en el enum `TipoReporte` de `PanelReportes` y un caso en el
   `switch` de `construir()`

Si el reporte necesita parámetros, se declara qué tarjeta de controles usar; el
`CardLayout` muestra la que corresponda.

### Cambiar la duración de las consultas

Modificar la constante `ServicioCitas.DURACION_MINUTOS`. Toda la lógica de
traslapes y de validación de horarios la utiliza.

### Cambiar el tamaño de un campo

Modificar la constante `LARGO_XXX` correspondiente en la clase `ArchivoXxx`. El
tamaño del registro se recalcula solo, porque se define sumando los campos en
lugar de escribir un número fijo.

**Importante:** al hacerlo hay que incrementar `VERSION_FORMATO` en
`ArchivoBase`, o los archivos existentes se leerían mal.
