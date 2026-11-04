# Módulo `embeddings`

## Propósito

Gestionar la representación vectorial de textos bibliográficos y las operaciones semánticas acordadas, manteniendo explícita la procedencia del modelo.

## Contratos públicos / Interfaces

- Contratos para solicitar, validar y asociar vectores a entradas bibliográficas.
- Resultados que identifican dimensión, modelo, versión y estado del procesamiento.
- Serialización mediante Jackson en el límite del módulo; tipos de transporte no deben filtrarse al dominio.

## Justificación técnica / matemática

La similitud coseno entre vectores no nulos se define en `docs/DOCUMENTO_GENERAL.md`, sección “Formulación matemática v2.0”. Vectores ausentes, de dimensión incompatible o de norma cero deben producir un resultado inválido explícito, no una similitud ficticia. La reproducibilidad requiere conservar la identificación del modelo y el preprocesamiento.

## Criterios de independencia

- Depender de `core` para conceptos del dominio, sin modificar sus reglas.
- Puede ser consumido por `api` y `tools`; no depende de `api`, `tools` ni `benchmark`.
- No acoplar la generación de embeddings a la API HTTP.
- Validar dimensiones, valores no finitos y vectores nulos.
- Probar la similitud con casos de referencia calculables manualmente.

## Archivos por modelo

`JsonEmbeddingProvider` carga IA-1 desde `vectors_ia1.json` e IA-2 desde
`embeddings_ia2.json`. Ambos se validan contra los hashes registrados en `embeddings.meta.json`;
IA-1 requiere vectores de 300 dimensiones e IA-2 vectores de 384 dimensiones con norma L2 unitaria.
La identidad, idioma, versión, procedencia y licencia del modelo deben quedar en sus metadatos y
en los ADR correspondientes. La política de IA-1 está en
[`ADR-004`](../docs/adr/ADR-004-modelo-ia1.md).
