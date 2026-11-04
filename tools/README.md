# Módulo `tools`

## Propósito

Agrupar utilidades de línea de comandos y operaciones fuera de línea para leer y analizar
artefactos bibliométricos ya generados, incluidos resultados de benchmark guardados.

## Contratos públicos / Interfaces

- Entrada y salida de archivos con esquemas versionables; Jackson se usa en los límites JSON.
- Las operaciones de dominio reutilizan `core`; los vectores y contratos semánticos reutilizan
  `embeddings`.
- Rutas de archivos, formatos y errores de lectura deben ser explícitos; el módulo no debe
  modificar en silencio resultados de origen.

## Justificación técnica

Las herramientas de análisis se mantienen fuera de la API y del ejecutor de benchmarks para
evitar dependencias de ejecución entre esos componentes. La lectura de resultados guardados es
por archivos, no mediante una dependencia Maven de `benchmark`.

## Criterios de independencia

- Dependencias internas permitidas: `core` y `embeddings`.
- Dependencia externa de producción permitida: `jackson-databind`.
- No depender de `api`, `benchmark` ni `ingest`.
- Validar estructura, tipos, identificadores y errores de los artefactos antes de procesarlos.

## Estado

El módulo Maven y sus dependencias están declarados. La lectura de resultados de benchmark y las
utilidades ejecutables se implementan en las fases correspondientes; este README no afirma que
esa funcionalidad ya esté disponible.
