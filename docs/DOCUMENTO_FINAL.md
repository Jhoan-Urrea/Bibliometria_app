# Documento final de ingeniería

## Identificación y release

| Campo | Valor |
|---|---|
| Producto | Aplicativo bibliométrico |
| Release de proyecto | `v1.0.0` |
| Línea base Java/Maven | Java 21; Spring Boot 3.2.12 |
| Interfaz | React 18, TypeScript, Vite y D3 |
| API | OpenAPI 3.1 |
| Estado | Cierre técnico documentado; aprobación académica/metodológica pendiente de la rúbrica oficial |

El nombre de release `v1.0.0` identifica esta entrega. Los módulos Maven aún declaran
`1.0.0-SNAPSHOT`; el JAR API solicitado se conserva como `api-1.0.0-SNAPSHOT.jar`. Por ello,
la etiqueta de Git no convierte esos binarios en artefactos Maven de release no-SNAPSHOT.
La publicación Maven formal requiere preparar versiones `1.0.0` y reconstruir los artefactos.

## Resumen ejecutivo

La solución organiza el dominio y sus adaptadores en módulos Java independientes, expone
funcionalidades mediante una API Spring Boot y sirve la aplicación React desde el mismo JAR.
El trabajo incluye similitud documental, clustering jerárquico, búsqueda exacta con trazas y
un harness reproducible para KMP, Boyer–Moore y Rabin–Karp.

La arquitectura, contratos, formulaciones, limitaciones y decisiones vigentes se mantienen en
[`DOCUMENTO_GENERAL.md`](./DOCUMENTO_GENERAL.md), el contrato en
[`openapi.yaml`](./openapi.yaml), y los detalles de módulo en sus README. Este documento resume
el estado final y apunta a la evidencia existente; no sustituye esos artefactos.

## Arquitectura final

```text
data/corpus.json
       |
       +--> ingest --> core <-- embeddings
                        ^  \
                        |   +--> benchmark
                        |   +--> tools (lee CSV de benchmark como artefacto)
                        |
                        +--> api (Spring Boot + recursos web estáticos)
                                  |
                                  +--> web (React / TypeScript / D3)
```

- `core`: modelo, preprocesamiento, similitud, agrupamiento y algoritmos de búsqueda.
- `ingest`: adaptación y validación de entradas bibliográficas.
- `embeddings`: carga y uso de representaciones semánticas identificadas.
- `api`: endpoints REST, validación, formato de errores y empaquetado del frontend.
- `web`: pantallas y flujos de análisis accesibles que consumen el contrato HTTP.
- `benchmark`: escenarios y medición, dependiente de `core`.
- `tools`: análisis fuera de línea de artefactos, incluido el CSV de benchmark.

El JAR de API integra `index.html` y los assets Vite en `BOOT-INF/classes/static/`; el servicio
publica API y web bajo el mismo origen. Las dependencias y reglas de dominio se detallan en
[`docs/DOCUMENTO_GENERAL.md`](./DOCUMENTO_GENERAL.md) y en los README de módulo.

## Matriz de cumplimiento Req 1–Req 4

**Límite de trazabilidad:** no se encontró en el repositorio una copia de la guía/rúbrica que
defina formalmente los títulos y criterios de Req 1–Req 4. La siguiente matriz organiza la
evidencia técnica disponible por capacidad principal; no afirma equivalencia normativa con una
rúbrica externa ni reemplaza la aprobación del curso. Los requerimientos numerados formalmente
disponibles en el repositorio son Req 1 (similitud) y Req 2 (clustering).

| ID de cierre | Capacidad evaluada | Estado técnico | Evidencia verificable | Pendiente / límite |
|---|---|---|---|---|
| Req 1 | Similitud documental y correlación de rangos entre métodos léxicos y semánticos | Implementado; evaluación del corpus documentada | [`documento-diseno.md`](./final/documento-diseno.md), pruebas de `core` y metadatos de embeddings | Revisar modelo, licencia y hashes del artefacto FastText concreto antes de reproducir embeddings; contrastar contra el texto oficial del requisito. |
| Req 2 | Matriz de distancias, clustering jerárquico, métricas y exportación del dendrograma | Implementado con pruebas; evidencia temática separada | [`implementacion-req2.md`](./req/req2/implementacion-req2.md), [`Caso de Estudio 2`](./casos-de-estudio/ce2/README.md) | El documento de Req 2 registra cotejos pendientes con el DF no versionado y ADR-013 ausente; no se declara aprobación metodológica formal. |
| Req 3 | Búsqueda exacta KMP, Boyer–Moore y Rabin–Karp, posiciones y trazas | Implementado y probado; benchmark ejecutado | [`Caso de Estudio 3`](./casos-de-estudio/ce3/README.md), [`informe-benchmark.md`](./evidence/f6/informe-benchmark.md), pruebas de `core` | Resultados de rendimiento son de una ejecución/máquina y no prueban por sí solos cotas asintóticas. |
| Req 4 | Exposición e integración del producto: API REST, frontend y empaquetado | API/UI y flujos E2E documentados; JAR integrado inspeccionado | [`openapi.yaml`](./openapi.yaml), [`integracion-e2e.md`](./evidence/f5/integracion-e2e.md), JAR verificado en cierre | Docker Compose no quedó certificado en el entorno por falta de Docker; cotejar el nombre y criterios con la rúbrica oficial. |

El informe de benchmark usa 30 muestras por combinación, descarta 2.000 ejecuciones de
calentamiento por algoritmo/caso y aleatoriza el orden. Para E3, Boyer–Moore y Rabin–Karp
registraron comparaciones cercanas a `n·m`, mientras que KMP registró `n` comparaciones en los
casos repetitivos medidos. Las pendientes log-log se basaron en solo dos tamaños en esta
configuración; sus valores y `R²` no se presentan como prueba estadística general.

## Verificación de cierre

| Verificación | Resultado |
|---|---|
| `mvn --batch-mode --no-transfer-progress clean verify` | Ejecutada para el cierre; resultado registrado por el build de la revisión etiquetada. |
| Pruebas focalizadas del analizador | 2 pruebas aprobadas en `tools`. |
| Benchmark E1/E2/E3 | 1.260 muestras generadas; CSV analizado con el módulo `tools`. |
| JAR ejecutable de API | Se inspeccionó `api/target/api-1.0.0-SNAPSHOT.jar`; incluye `BOOT-INF/classes/static/index.html` y bundles CSS/JS de Vite. |
| JAR del módulo benchmark | Se inspeccionó `benchmark/target/benchmark-1.0.0-SNAPSHOT.jar`; contiene las clases `BenchmarkRunner` y `ScenarioGenerator`. |
| Git | La etiqueta anotada `v1.0.0` identifica el commit de cierre indicado por la release. |
| Docker Compose | No certificado por la evidencia F5: Docker no estaba disponible en el entorno reportado. No se infiere éxito a partir del build Maven. |

Las evidencias de Fase 5 y Fase 6 permanecen en
[`docs/evidence/f5/`](./evidence/f5/) y [`docs/evidence/f6/`](./evidence/f6/).
Los binarios bajo `target/` son artefactos de build locales y reproducibles, no archivos de
evidencia versionados.

## Declaración formal de uso responsable de IA

Se utilizó IA generativa como herramienta de asistencia para análisis del repositorio,
propuestas y edición de código, pruebas y documentación durante el desarrollo. La IA no se
considera autora responsable ni fuente de verdad para requisitos, resultados científicos o
decisiones de arquitectura. Las decisiones y resultados deben ser revisados por el equipo
responsable, que conserva la responsabilidad sobre su exactitud, licencias, privacidad y uso.

El modelo concreto del asistente no estuvo expuesto en el entorno de esta sesión; no se atribuye
un nombre o versión no verificable. Los cambios se validaron mediante compilación/pruebas Maven,
pruebas frontend y E2E documentadas, inspección del contenido del JAR y ejecución reproducible
del benchmark según la evidencia enlazada. Esa validación automatizada no reemplaza la revisión,
aprobación ni firma humana. El registro auditable se mantiene en
[`registro-ia.md`](./gestion/registro-ia.md).

Los modelos IA-1 (FastText) e IA-2 (`all-MiniLM-L6-v2`) son componentes funcionales distintos
del uso de IA generativa para desarrollar el software; sus decisiones y controles se documentan
en [`registro-ia.md`](./gestion/registro-ia.md) y sus ADR asociados.

## Índice de documentación de cierre

- Arquitectura y formulación: [`DOCUMENTO_GENERAL.md`](./DOCUMENTO_GENERAL.md)
- Diseño y Requerimiento 1: [`documento-diseno.md`](./final/documento-diseno.md)
- Especificación de Req 2: [`implementacion-req2.md`](./req/req2/implementacion-req2.md)
- Casos de estudio: [`CE2`](./casos-de-estudio/ce2/README.md) · [`CE3`](./casos-de-estudio/ce3/README.md)
- Contrato HTTP: [`openapi.yaml`](./openapi.yaml)
- Integración frontend / E2E: [`integracion-e2e.md`](./evidence/f5/integracion-e2e.md)
- Rendimiento: [`informe-benchmark.md`](./evidence/f6/informe-benchmark.md)
- Gobierno de IA: [`registro-ia.md`](./gestion/registro-ia.md)
- Historial de decisiones de formulación: [`cambios-df.md`](./gestion/cambios-df.md)
