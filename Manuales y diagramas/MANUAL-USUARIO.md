# Manual de Usuario
## Sistema de Gestión de Clínica Médica

Universidad de San Carlos de Guatemala — Centro Universitario de Occidente
Ingeniería en Ciencias y Sistemas — Manejo e Implementación de Archivos  
Luis Alejandro Regalado Villatoro - 202131920

---

## 1. Requisitos e instalación

Para ejecutar el sistema se necesita **Java 21** o superior instalado.

Para verificarlo, abra una terminal y escriba:

```
java -version
```

Debe mostrar la versión 21 o mayor. Si no tiene Java instalado, puede
descargarlo desde el sitio de Adoptium o, en Linux, con el gestor de paquetes de
su distribución.

### Ejecutar la aplicación

Coloque el archivo `clinica-medica.jar` en la carpeta donde desee guardar la
información de la clínica, abra una terminal en esa carpeta y escriba:

```
java -jar clinica-medica.jar
```

> **Importante:** el sistema crea una carpeta llamada `datos` **en el directorio
> desde donde se ejecuta**, y ahí guarda toda la información. Si abre el
> programa haciendo doble clic sobre el archivo, esa carpeta podría crearse en
> un lugar inesperado. Se recomienda siempre ejecutarlo desde la terminal.

Al abrirse por primera vez todas las pantallas aparecerán vacías: es normal,
todavía no hay información registrada.

---

## 2. La ventana principal

La aplicación se organiza en cuatro pestañas y un menú superior.

| Pestaña | Para qué sirve |
|---|---|
| **Médicos** | Registrar y administrar a los profesionales de la clínica |
| **Pacientes** | Registrar y administrar los expedientes de los pacientes |
| **Citas** | Programar y dar seguimiento a las consultas |
| **Reportes** | Consultar y exportar la información del sistema |

El menú **Datos** permite cargar información desde archivos CSV, y el menú
**Ayuda** muestra el formato que deben tener esos archivos.

### Antes de empezar

El orden en que se registra la información importa, porque una cita necesita que
su paciente y su médico ya existan:

1. Primero registre **médicos**
2. Después **pacientes**
3. Y por último programe **citas**

---

## 3. Módulo de Médicos

### Registrar un médico

En el formulario de la izquierda llene los datos. Los campos marcados con
asterisco (*) son obligatorios:

- **Nombres**, **Apellidos** y **Especialidad** son obligatorios
- **Teléfono** y **Correo** son opcionales
- **Hora inicio** y **Hora fin** definen el horario de atención. Use las flechas
  del control o escriba directamente sobre él; el formato es de 24 horas
- La casilla **Médico activo** viene marcada por omisión

Presione **Guardar**. El código del médico lo genera el sistema automáticamente.

### Modificar un médico

Seleccione el médico en la tabla de la derecha. Sus datos se cargarán en el
formulario y el botón cambiará a **Guardar cambios**. Modifique lo necesario y
presione el botón.

> Si intenta cambiar el horario de atención y el médico tiene citas ya
> programadas que quedarían fuera del nuevo horario, el sistema no lo permitirá y
> le mostrará cuáles son esas citas. Deberá atenderlas o cancelarlas primero.

### Activar o desactivar

Seleccione el médico y presione **Activar / Desactivar**. Un médico inactivo se
conserva en el sistema con todo su historial, pero **no puede recibir citas
nuevas**.

### Buscar y filtrar

- **Buscar**: escriba parte del nombre, apellido, especialidad o el código, y
  presione **Enter**
- **Estado**: muestra solo activos, solo inactivos, o todos
- **Especialidad**: filtra por una especialidad específica

Para ver todo de nuevo, deje la búsqueda vacía y presione Enter.

---

## 4. Módulo de Pacientes

### Registrar un paciente

- **Identificación**: el número de identificación personal. Solo dígitos, y no
  puede repetirse
- **Nombres** y **Apellidos** son obligatorios
- **Nacimiento**: escriba la fecha (las barras ya están puestas, solo teclee los
  ocho dígitos) o presione el botón **...** para abrir un calendario
- **Sexo** y **Tipo de sangre** se eligen de las listas
- **Teléfono** y **Correo** son opcionales

### El calendario

Al presionar **...** se abre un calendario donde puede:

- Cambiar de mes con las flechas **<** y **>**, o eligiéndolo de la lista
- Cambiar de año con las flechas del control numérico
- Presionar **Hoy** para elegir la fecha actual
- Hacer clic en cualquier día para seleccionarlo

### Modificar un paciente

Selecciónelo en la tabla. Note que el campo de **Identificación se bloquea**:
ese número es la llave del expediente y las citas lo referencian, por lo que no
puede cambiarse. Si se registró mal, deberá dar de baja ese expediente y crear
uno nuevo.

### Dar de baja o reactivar

Seleccione el paciente y presione **Dar de baja / Reactivar**.

Dar de baja **no borra el expediente**: lo marca como inactivo y lo conserva,
de modo que sus citas históricas sigan teniendo sentido. Un paciente dado de
baja no puede recibir citas nuevas hasta que se le reactive.

> Si el paciente tiene citas todavía programadas, el sistema no permitirá darlo
> de baja. Atiéndalas o cancélelas primero.

### Buscar y filtrar

Escriba el número de identificación, nombre o apellido en **Buscar** y presione
Enter. También puede filtrar por tipo de sangre y por estado.

---

## 5. Módulo de Citas

### Programar una cita

- **Paciente** y **Médico** se eligen de las listas. En la lista de médicos solo
  aparecen los que están activos
- **Fecha**: escriba o use el calendario
- **Hora**: use las flechas o escriba sobre el control
- **Motivo** es obligatorio; **Observaciones** es opcional

Presione **Programar cita**.

Las consultas tienen una duración estándar de **30 minutos**. El sistema
verificará automáticamente que:

- La cita quepa dentro del horario de atención del médico
- El médico no tenga otra cita a esa hora
- El paciente no tenga otra cita a esa hora
- La fecha no sea anterior al día de hoy

Si algo no se cumple, aparecerá un mensaje explicando exactamente cuál es el
problema.

### Dar seguimiento

Seleccione una cita en la tabla y use los botones de la sección inferior:

| Botón | Qué hace |
|---|---|
| **Guardar motivo/observaciones** | Actualiza esos dos campos de la cita |
| **Marcar atendida** | Registra que la consulta se realizó |
| **Cancelar cita** | Marca la cita como cancelada |
| **Eliminar cita** | Borra la cita del sistema (pide confirmación) |

> Una cita ya atendida no puede cancelarse, y una cancelada no puede marcarse
> como atendida. Para reagendar, programe una cita nueva.

Cuando una cita se atiende o se cancela, **su horario queda libre** y puede
programarse otra cita a esa misma hora.

### Filtrar citas

Puede combinar los filtros de fecha, estado, médico y paciente. El botón
**Quitar filtros** los limpia todos de una vez.

---

## 6. Módulo de Reportes

### Generar un reporte

1. Elija el reporte de la lista desplegable
2. Si el reporte necesita datos adicionales (una fecha, una especialidad, un
   médico), los campos aparecerán automáticamente al lado
3. Presione **Generar**

Los reportes disponibles son:

**De pacientes**
- Listado completo
- Por tipo de sangre
- Con mayor cantidad de citas
- Que nunca han tenido una cita

**De médicos**
- Listado completo
- Por especialidad
- Con mayor cantidad de citas
- Con citas programadas para una fecha

**De citas**
- Listado completo
- Por rango de fechas
- Por médico
- Por paciente
- Por estado
- Cantidad por especialidad

**Bitácora**
- Todas las operaciones realizadas en el sistema, de la más reciente a la más
  antigua

### Exportar

Con un reporte ya generado, presione **Exportar CSV** o **Exportar TXT**, elija
dónde guardarlo y confirme.

- **CSV** se abre con Excel o LibreOffice Calc
- **TXT** queda con las columnas alineadas, útil para imprimir

Ambos formatos se guardan en UTF-8, de modo que las tildes y eñes se ven
correctamente en cualquier computadora.

---

## 7. Carga masiva desde CSV

Cuando hay mucha información que ingresar, el menú **Datos** permite cargarla
desde archivos.

### Obtener las plantillas

1. Menú **Datos** → **Generar plantillas CSV...**
2. Elija una carpeta
3. Se crearán tres archivos de ejemplo con el encabezado y filas de muestra

Ábralos con Excel o cualquier editor de texto, reemplace las filas de ejemplo
por sus datos y guarde manteniendo el formato CSV.

### Formato de cada archivo

**Médicos**
```
nombres,apellidos,especialidad,telefono,correo,horaInicio,horaFin,activo
```
Horas en formato `HH:mm`. La columna `activo` acepta `si` o `no`.

**Pacientes**
```
identificacion,nombres,apellidos,nacimiento,sexo,telefono,correo,tipoSangre
```
Fecha en formato `dd/mm/aaaa`. Sexo: `M` o `F`. Tipo de sangre: `O+`, `A-`,
`AB+`, etc.

**Citas**
```
identificacionPaciente,medico,fecha,hora,motivo,observaciones
```
La columna `medico` admite el código completo del médico **o** su nombre y
apellidos.

> Si un campo contiene comas —por ejemplo un motivo como
> `dolor de cabeza, náuseas`— debe ir entre comillas dobles.

### Cargar

1. Menú **Datos** → **Cargar médicos / pacientes / citas desde CSV...**
2. Seleccione el archivo
3. El sistema mostrará cuántos registros entraron y cuántos se rechazaron

**Cargue en este orden: médicos, luego pacientes, luego citas.** Una cita
necesita que ambos ya existan.

### Si hay filas rechazadas

Las filas válidas **sí se cargan**; solo se rechazan las que tienen problemas. El
sistema muestra el número de línea de cada una y el motivo, por ejemplo:

```
Linea 5: La fecha de nacimiento debe tener el formato dd/MM/aaaa
Linea 6: sexo no reconocido: 'X' (use M o F)
Linea 9: se esperaban 8 columnas y llegaron 3
```

Corrija esas líneas en el archivo y vuelva a cargarlo. Las que ya entraron serán
rechazadas por identificación repetida, lo cual es correcto y esperado.

---

## 8. Mensajes frecuentes

| Mensaje | Qué significa |
|---|---|
| *Ya existe un paciente con la identificación...* | Ese número ya está registrado. Búsquelo en la pestaña Pacientes. |
| *El número de identificación solo puede contener dígitos* | Quite letras, guiones o espacios. |
| *La fecha debe tener el formato dd/MM/aaaa* | La fecha no existe (por ejemplo 31/02) o está incompleta. |
| *El doctor X está inactivo y no puede recibir citas* | Actívelo en la pestaña Médicos, o elija otro. |
| *El doctor X atiende de HH:mm a HH:mm* | La cita no cabe en su horario. Recuerde que dura 30 minutos. |
| *Ya tiene una cita a las HH:mm ese día* | Ese espacio está ocupado. Elija otra hora. |
| *No se puede dar de baja al paciente porque tiene N citas programadas* | Atienda o cancele esas citas primero. |
| *El horario nuevo dejaría fuera N citas ya programadas* | El sistema lista cuáles. Reprográmelas antes de cambiar el horario. |
| *El archivo es de la versión N...* | Los archivos de datos son de una versión anterior del sistema. Borre la carpeta `datos` para empezar de nuevo. |

---

## 9. Sobre la información guardada

Toda la información se guarda **en el momento** en que se realiza cada
operación. No existe un botón de "guardar": si registra un paciente y cierra la
aplicación enseguida, el paciente ya quedó guardado.

Dentro de la carpeta `datos` encontrará:

| Archivo | Contenido |
|---|---|
| `medicos.dat` / `medicos.idx` | Médicos y su índice de búsqueda |
| `pacientes.dat` / `pacientes.hash` | Pacientes y su tabla de dispersión |
| `citas.dat` | Citas |
| `citas_medico.idx` / `citas_paciente.hash` | Enlaces de las citas por médico y por paciente |
| `bitacora.dat` | Registro de operaciones |

> **No edite ni borre estos archivos manualmente** mientras la aplicación está
> abierta. Los archivos de índice (`.idx` y `.hash`) se reconstruyen solos si se
> pierden, pero los `.dat` contienen la información y no pueden recuperarse.

Para respaldar la información, cierre la aplicación y copie la carpeta `datos`
completa.

### Cerrar correctamente

Cierre siempre la aplicación con la **X de la ventana**. Al hacerlo, el sistema
cierra ordenadamente todos los archivos. Si cierra la terminal de golpe, los
últimos datos escritos podrían no llegar al disco.
