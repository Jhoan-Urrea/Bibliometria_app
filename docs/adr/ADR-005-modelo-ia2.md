# ADR-005: Modelo IA-2 basado en Sentence-Transformers

- **Estado:** Aceptado para el corpus de trabajo en inglés.
- **Fecha:** 2026-11-04
- **Ámbito:** `embeddings`
- **Relacionado:** [ADR-001](./ADR-001-idioma-corpus.md), [ADR-004](./ADR-004-modelo-ia1.md),
  [política de embeddings](../final/documento-diseno.md)

## Contexto

IA-2 aporta una representación semántica contextual complementaria a los vectores léxicos de
FastText. El corpus vigente se declara en inglés en ADR-001; por ello, el checkpoint debe ser
compatible con inglés. Los embeddings se generan fuera de la API y se cargan como artefactos
identificados.

## Decisión

IA-2 usa Sentence-Transformers con el checkpoint `sentence-transformers/all-MiniLM-L6-v2`. Cada
documento se representa mediante un embedding de 384 dimensiones normalizado a norma L2 unitaria.
Los textos que excedan la longitud admitida se truncan por la derecha, según
`docs/final/documento-diseno.md`.

El identificador del checkpoint, la versión de la biblioteca, la procedencia, la licencia y los
hashes de los artefactos deben registrarse en `embeddings.meta.json` antes de usar resultados.
La salida se conserva separada de IA-1 como `embeddings_ia2.json`.

## Consecuencias

- El modelo contextual es compatible con el idioma inglés declarado en ADR-001; un cambio de
  idioma exige reevaluar y versionar la decisión.
- La dimensión, normalización, truncado e integridad se validan al cargar los embeddings.
- IA-2 complementa y no sustituye la salida independiente de IA-1 documentada en ADR-004.
