# Módulo `core`

## Propósito

Contener el modelo de dominio bibliométrico, sus invariantes y los cálculos deterministas, sin depender de infraestructura ni de frameworks.

## Contratos públicos / Interfaces

- Entidades y objetos de valor para documentos, autores, fuentes e indicadores.
- Contratos de cálculo bibliométrico y validación del dominio.
- Las interfaces públicas deben expresar reglas del dominio y no tipos de frameworks.
- Runtime: únicamente JDK 21. JUnit 5 y AssertJ se permiten exclusivamente para pruebas.

## Justificación técnica / matemática

El aislamiento del dominio evita que formatos, transporte HTTP o motores de embeddings condicionen las reglas bibliométricas. Los indicadores deben declarar universo, filtros, corte temporal y tratamiento de datos faltantes. Las fórmulas oficiales y su trazabilidad se mantienen en `docs/DOCUMENTO_GENERAL.md`, sección “Formulación matemática v2.0”.

## Criterios de independencia

- No declarar dependencias de producción.
- No importar clases de otros módulos, frameworks ni adaptadores.
- Puede ser consumido por `ingest`, `embeddings`, `api`, `benchmark` y `tools`; no depende de
  ninguno de ellos.
- Mantener cobertura de pruebas igual o superior al 80%.
- Verificar invariantes y casos límite con resultados reproducibles.

## Preprocesamiento textual

El paquete `com.bibliometria.core.text` ofrece `TextPreprocessor`, que recibe la ruta UTF-8 a
`stopwords.txt` y procesa `Article` en título y resumen. `normalizeText` expone P-TEXTO; `process`
genera P-TOKENS, frecuencias, conjunto y cadena canónica. Esta funcionalidad usa únicamente el JDK.

## Búsqueda exacta de patrones

El paquete `com.bibliometria.core.matching` implementa el contrato `StringMatcher` y entrega un
`MatchResult` inmutable con posiciones, tiempos, comparaciones carácter a carácter y métricas del
algoritmo. Incluye KMP, Boyer-Moore (carácter malo y sufijo bueno fuerte) y Rabin-Karp. Las
posiciones son índices UTF-16; un patrón vacío coincide en cada límite del texto. Las
comparaciones cuentan únicamente evaluaciones de igualdad entre un carácter del texto y uno del
patrón; las comparaciones de preprocesamiento no se incluyen.

La implementación de producción no usa dependencias externas ni utilidades de búsqueda o
expresiones regulares de `String`; el oráculo ingenuo vive exclusivamente en pruebas.

`CorpusSearchService` recibe una lista de textos P-TEXTO identificados, devuelve las posiciones
de KMP, Boyer-Moore y Rabin-Karp por artículo y establece `coinciden` solo cuando las tres listas
son idénticas en todos ellos. Cada ocurrencia genera un fragmento de hasta 40 unidades UTF-16 y
un intervalo resaltado relativo al inicio del fragmento. El cargador del JSON del corpus queda a
cargo de la capa de ingesta; `core` no incorpora un parser ni dependencias de producción.

El contrato de `MatchResult` incluye `extras`, un mapa inmutable para métricas específicas del
algoritmo. Para Rabin-Karp contiene `hashCoincidencias` y `falsosPositivos`; `codigo(c)` corresponde
al valor UTF-16 de `charAt` y no requiere una dependencia externa. Ver
[`ADR-012`](../docs/adr/ADR-012-contrato-busqueda.md).

Los matchers aceptan opcionalmente un `TraceRecorder`. `JsonTraceRecorder` exporta eventos JSON
con la tabla LPS de KMP, las tablas de carácter malo y sufijo bueno de Boyer-Moore y los hashes
de patrón y ventanas de Rabin-Karp. El escáner de APIs prohibidas y la prueba de estrés con 10.000
casos de semilla fija están en `src/test`. JaCoCo exige al menos 85 % de cobertura de líneas en
`com.bibliometria.core.matching`, además del umbral global de `core`.
