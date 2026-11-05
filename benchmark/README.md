# Módulo `benchmark`

## Propósito

Medir de forma repetible el rendimiento de operaciones bibliométricas seleccionadas y detectar regresiones con datos y entornos declarados.

## Contratos públicos / Interfaces

- Escenarios de benchmark con operación, parámetros y tamaño de datos explícitos.
- Resultados que reportan unidad, configuración, versión del código y entorno de ejecución.
- Integración con las operaciones públicas de `core`; no debe redefinir su comportamiento.

## Justificación técnica / matemática

Una medición de rendimiento solo es interpretable si identifica el conjunto de entrada, el método y el entorno. Los datos deben ser reproducibles y representativos; el benchmark no altera ni aproxima la definición matemática de los indicadores, centralizada en `docs/DOCUMENTO_GENERAL.md`.

## Criterios de independencia

- Depender únicamente de `core` para las operaciones del dominio.
- No depender de `api`, `ingest` ni `tools`. Las herramientas fuera de línea pueden leer resultados
  guardados mediante un formato de archivo, sin acoplamiento Maven.
- Separar medición de pruebas unitarias y de lógica de producción.
- Evitar datos locales pesados o sensibles en el repositorio.
- Documentar configuración, unidad y limitaciones de cada resultado.

## Resultados guardados

DF v2.1 requiere que `tools` pueda leer resultados de benchmark guardados. Antes de producir o
consumir esos resultados debe definirse y versionarse su esquema, incluyendo operación, parámetros,
unidad, configuración, versión del código y entorno. El lector no debe requerir una dependencia de
este módulo.

## Búsqueda exacta: E1, E2 y E3

Ejecutar desde la raíz:

```sh
mvn --batch-mode --no-transfer-progress -pl benchmark exec:java \
  -Dbenchmark.config=benchmark/benchmark-config.example.json
```

La configuración JSON permite seleccionar escenarios, semilla, número de muestras y rutas de los
CSV. `BenchmarkRunner` descarta exactamente 2.000 ejecuciones de calentamiento por caso y
algoritmo, aleatoriza el orden en cada ronda y exige al menos 30 muestras. El CSV de muestras
contiene los valores individuales; el segundo CSV resume mediana, Q1, Q3 e IQR por métrica usando
cuantiles interpolados linealmente.

- **E1:** concatena los `abstractText` de un corpus JSON en orden y ejecuta un patrón presente y
  otro ausente, ambos declarados en la configuración.
- **E2:** genera texto y patrón deterministas con semilla fija para cada combinación de longitud
  `n`, longitud `m` y alfabeto `sigma` (1–94 caracteres ASCII imprimibles).
- **E3:** usa texto y patrón de caracteres repetidos para forzar comparaciones por ventana en
  Boyer-Moore y verificación carácter a carácter en Rabin-Karp.

`n`, `m` y `sigma` se miden en unidades UTF-16, igual que los algoritmos de `core`. La columna
`extras` conserva como JSON las métricas específicas del algoritmo. El harness comprueba la paridad
de posiciones entre los tres algoritmos antes de aceptar cada muestra.
