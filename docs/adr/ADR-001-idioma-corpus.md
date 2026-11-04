# ADR-001: Idioma de trabajo del corpus

- **Estado:** Aceptado para la versión vigente del corpus.
- **Fecha:** 2026-11-04
- **Ámbito:** `ingest`, `embeddings`, `api`
- **Relacionado:** [ADR-004](./ADR-004-modelo-ia1.md), [ADR-005](./ADR-005-modelo-ia2.md),
  [registro de uso de IA](../gestion/registro-ia.md)

## Contexto

El idioma del corpus condiciona la selección de los modelos, la cobertura de vocabulario, la
normalización y la interpretación de similitudes. La revisión de los 20 registros extraídos para
la Fase 0 muestra títulos y texto bibliográfico predominantemente en inglés.

## Decisión

Se declara el inglés como idioma de trabajo del corpus de la versión vigente. Los modelos IA-1 e
IA-2 deben ser compatibles con inglés. Esta decisión describe el corpus actual; no implica que
documentos futuros se acepten sin verificación lingüística.

Antes de incorporar documentos de otro idioma o cambiar materialmente la composición del corpus,
se debe revisar la compatibilidad de los modelos, el preprocesamiento y los criterios de
evaluación, y actualizar este ADR y los metadatos de los embeddings.

## Consecuencias

- IA-1 usa FastText compatible con inglés y conserva la representación por subpalabras indicada en
  ADR-004.
- IA-2 usa el checkpoint en inglés `all-MiniLM-L6-v2`, según ADR-005.
- El manifiesto de datos garantiza integridad de archivos, no el idioma; los cambios al corpus
  requieren revisión lingüística explícita.
