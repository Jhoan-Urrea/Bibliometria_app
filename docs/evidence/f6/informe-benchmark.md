# Informe de benchmark: búsqueda exacta

## Alcance y metodología

Se compararon KMP, Boyer–Moore y Rabin–Karp con los escenarios E1 (abstracts reales),
E2 (texto sintético) y E3 (entrada adversarial). Cada combinación de escenario, algoritmo,
`n`, `m` y `σ` tiene 30 muestras. El runner descartó 2.000 ejecuciones de calentamiento por
caso y algoritmo, aleatorizó el orden de algoritmos en cada repetición y usó la semilla 42.
Los tiempos de preprocesamiento y búsqueda se midieron por separado; se reportan medianas e
IQR. Los datos se analizaron desde las 1.260 filas del CSV de muestras, no desde los promedios
del runner.

Entorno registrado: OpenJDK 21.0.12.1, Maven 3.8.7, Linux x86_64 (kernel 7.0.0-42-generic).
La configuración incluyó 14 casos: E1 presente/ausente con `n=36.185`, E2 con
`n ∈ {256, 1.024}`, `m ∈ {8, 24}`, `σ ∈ {2, 16}`, y E3 con `n ∈ {256, 512}`,
`m ∈ {8, 16}`, `σ=1`. El corpus utilizado ocupa 49.039 bytes. La referencia Git del
entorno era `dd70e48`; había cambios locales sin confirmar al ejecutar.

## Teoría frente a observaciones

| Algoritmo | Cota teórica | Observado en E2 | Observado en E3 |
|---|---|---|---|
| KMP | Preprocesamiento `O(m)`; búsqueda `O(n)`; total `O(n+m)`. | Comparaciones/n entre 1,05 y 1,50; pendientes log-log de tiempo vs. `n` entre 0,62 y 0,76. | Exactamente `n` comparaciones en las cuatro combinaciones (`C/(n·m)=1/m`); pendientes vs. `n` de 0,88–1,20. Compatible con crecimiento lineal, aunque el rango y el tamaño de muestra no bastan para estimar la asíntota con precisión. |
| Boyer–Moore | Preprocesamiento `O(m+|Σ|)`; aquí la tabla UTF-16 densa tiene 65.536 posiciones. Búsqueda promedio sublineal; peor caso `O(n·m)`. | Comparaciones/n entre 0,086 y 0,582; pendiente vs. `n` muy inestable (−1,31 a 1,40), dominada en varios casos por el costo fijo de inicializar la tabla. | `C/(n·m)` entre 0,941 y 0,986, cercano a una comparación por carácter de patrón y ventana; pendientes vs. `n` de 0,64–0,94. La entrada repetitiva activa el comportamiento cuadrático esperado. |
| Rabin–Karp | Preprocesamiento `O(m)`; recorrido esperado `O(n)` con hash rodante; peor caso `O(n·m)` por verificaciones. | Comparaciones/n entre 0 y 0,031; pendientes vs. `n` de 0,96–1,03. La mayoría de las entradas aleatorias no produjo verificaciones de ventanas. | `C/(n·m)` entre 0,941 y 0,986; pendientes vs. `n` de 0,96–1,18. Las coincidencias de hash en cada ventana fuerzan la verificación carácter a carácter y muestran el peor caso. |

Las pendientes se calcularon mediante regresión lineal de `log(mediana(nanosPreproceso +
nanosBusqueda))` respecto a `log(n)` o `log(m)`, manteniendo fijos escenario, algoritmo,
`σ` y la otra longitud. En esta ejecución cada pendiente válida usa solo dos tamaños; por eso
`R²=1` en esas regresiones es una consecuencia de ajustar dos puntos, no evidencia de un ajuste
predictivo. Las pendientes observadas son indicativas y no sustituyen una curva con más tamaños.

### Muestras representativas

Medianas para E3 con `n=512`, `m=16`, `σ=1`:

| Algoritmo | Preprocesamiento (ns) | Búsqueda (ns) | Total (ns) | Comparaciones | `C/(n·m)` |
|---|---:|---:|---:|---:|---:|
| KMP | 352,5 | 10.207,5 | 10.482 | 512 | 0,0625 |
| Boyer–Moore | 23.712,5 | 31.402 | 54.629 | 7.952 | 0,9707 |
| Rabin–Karp | 200 | 29.719,5 | 29.912 | 7.952 | 0,9707 |

En E1, la mediana total fue 38.610 ns / 66.877,5 ns (Boyer–Moore, patrón ausente/presente),
90.799,5 ns / 148.177 ns (KMP) y 548.620 ns / 554.313,5 ns (Rabin–Karp). El resultado
describe este corpus y estas entradas, no una superioridad universal de un algoritmo.

## Interpretación de costos

- **Preprocesamiento:** Boyer–Moore dedicó entre 88,13% y 99,14% del tiempo total a
  preprocesar en E2. En E3 esa fracción fue 46,23%–65,23%. La inicialización de su tabla
  densa de 65.536 enteros es un costo fijo notable para los patrones cortos probados. En E2,
  las fracciones fueron 2,65%–25,38% para KMP y 0,95%–6,82% para Rabin–Karp.
- **Caché y JVM:** el corpus y los casos se reutilizan en 2.000 calentamientos y luego en las
  muestras, por lo que texto, patrón y estructuras pueden permanecer en caché. El calentamiento
  reduce efectos de compilación JIT, pero no garantiza estado estable ni elimina pausas de GC,
  planificación del sistema u otras cargas. El benchmark no aísla estos factores.
- **Aritmética modular:** Rabin–Karp hace multiplicaciones y restos respecto a `1.000.000.007`
  al construir y actualizar hashes. Ese trabajo no incrementa el contador de comparaciones de
  caracteres, pero sí está incluido en `nanosBusqueda`; por ello, comparar únicamente conteos
  entre algoritmos no explica toda la diferencia temporal.
- **Salidas:** en E3 todos los desplazamientos coinciden y se almacenan; el costo temporal
  incluye la creación de la lista de posiciones, compartida por los tres algoritmos.

## Amenazas a la validez y pendientes

1. Solo se midió una ejecución completa, en una máquina y con una versión de JVM; no se
   registraron modelo de CPU, carga concurrente ni estado térmico.
2. E2/E3 cubren dos tamaños de texto y dos longitudes de patrón. La regresión con dos puntos
   no permite comprobar curvatura, y las pendientes respecto a `m` están igualmente limitadas
   a dos longitudes.
3. Las 30 repeticiones son secuenciales sobre los mismos datos; no son 30 corpus independientes.
   El calentamiento repetido y la caché favorecen estas entradas estables.
4. E3 repite un único carácter y maximiza las verificaciones, pero no representa todos los
   patrones adversariales posibles ni demuestra una cota por sí solo.
5. Se recomienda repetir en varias JVM/procesos y máquinas, aumentar el rango de `n` y `m`,
   variar patrones y alfabetos, y conservar CSV, configuración, CPU, sistema, versión de Java y
   revisión del código con cada resultado publicado.

## Artefactos reproducibles

- Configuración: [`benchmark-config.example.json`](../../../benchmark/benchmark-config.example.json)
- Runner y semántica de escenarios: [`BenchmarkRunner.java`](../../../benchmark/src/main/java/com/bibliometria/benchmark/BenchmarkRunner.java)
- Analizador de CSV: [`AnalisisBenchmark.java`](../../../tools/src/main/java/com/bibliometria/tools/benchmark/AnalisisBenchmark.java)
- Comando: `mvn --batch-mode --no-transfer-progress -pl benchmark exec:java -Dbenchmark.config=benchmark/benchmark-config.example.json`
- El CSV detallado y el informe generado por el analizador quedan en `benchmark/target/`; no se versionan como evidencia permanente.
