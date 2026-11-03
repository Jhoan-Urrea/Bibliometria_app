# Acta de Aprobación Gate F2 — Búsqueda de Patrones Exactos

- **Fecha:** 2 de noviembre de 2026
- **Proyecto:** Aplicativo de Análisis Bibliométrico (Universidad del Quindío)
- **Módulo:** `core` (paquete `com.bibliometria.core.matching`)
- **Estado de criterios de salida:** **APROBADO**

## Checklist de verificación

- [x] KMP, Boyer-Moore y Rabin-Karp implementados con JDK 21, sin dependencias de producción y sin las APIs de `String` prohibidas.
- [x] Escaneo automático de las APIs prohibidas y de referencias a `java.util.regex` en fuentes de producción de `core.matching`; las pruebas negativas confirman que el escáner detecta `indexOf` y el paquete regex.
- [x] Cobertura JaCoCo en `core.matching`: **93,68 %**; umbral configurado: **85 %**.
- [x] Suite de estrés de 10.000 casos aleatorios con semilla `42`, por debajo de 60 segundos.
- [x] Caso de estudio 3 ejecutado sobre los 20 abstracts de `data/corpus.json`: **38 ocurrencias en 15 abstracts** y paridad de posiciones entre los tres algoritmos.
- [x] Formato verificado con `mvn spotless:apply`.
- [x] Build limpio completo verificado con `mvn clean verify`; resultado: `BUILD SUCCESS`.
- [x] Javadocs de KMP, Boyer-Moore y Rabin-Karp documentan fórmulas, correspondencia con código y complejidad temporal y espacial.

## Evidencia

- [Caso de estudio 3 y método](../../casos-de-estudio/ce3/README.md)
- [Traza JSON del caso de estudio 3](../../casos-de-estudio/ce3/traza.json)
- Pruebas automatizadas: `ForbiddenApiScanTest`, `PatternMatcherStressTest`, `CorpusSearchServiceTest`, `TraceRecorderTest` y `PatternMatcherTest`.
- Gate JaCoCo por paquete: `core/pom.xml`.

## Resolución

Se aprueba el cierre técnico de Fase 2 para búsqueda exacta de patrones. El servicio de corpus consume textos P-TEXTO ya preparados; la lectura JSON y la normalización del corpus siguen siendo responsabilidad de la capa de ingesta.
