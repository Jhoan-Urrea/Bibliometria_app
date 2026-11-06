# ADR-004: Modelo IA-1 basado en FastText con subpalabras

- **Estado:** Aceptado para el corpus de trabajo en inglés; el artefacto FastText concreto debe
  identificarse antes de generar resultados.
- **Fecha:** 2026-11-04
- **Ámbito:** `embeddings`
- **Relacionado:** [ADR-001](./ADR-001-idioma-corpus.md), [ADR-005](./ADR-005-modelo-ia2.md),
  `docs/gestion/cambios-df.md` (DF21-04 y DF21-05)

## Contexto

El análisis semántico necesita un vector reproducible por documento. El corpus puede incluir
términos técnicos, nombres propios y formas que no aparezcan como palabras completas en el
vocabulario del modelo. El idioma y la composición del corpus deben determinar qué artefacto
preentrenado se usa; FastText no elimina la necesidad de escoger un modelo compatible con esos
idiomas.

## Opciones

1. Usar vectores de palabras sin capacidad de representar formas fuera del vocabulario.
2. Usar FastText con vectores de subpalabras, condicionado a un modelo compatible con el idioma
   o el idioma declarado en ADR-001.
3. Delegar IA-1 a un servicio remoto de inferencia.

## Decisión

IA-1 usa FastText con información de subpalabras. Para una palabra sin vector completo en el
vocabulario, el modelo puede construir una representación a partir de sus subpalabras conforme a
la configuración del artefacto seleccionado. La configuración de IA-1 mantiene vectores de
300 dimensiones y el vector documental se obtiene promediando los vectores de sus tokens
vectorizables, según la política de `docs/final/documento-diseno.md`.

El checkpoint, idioma(s), versión, licencia y hash del modelo deben quedar identificados antes de
generar resultados. Su selección debe corresponder al perfil documentado en ADR-001. La
representación por subpalabras no se considera garantía de soporte lingüístico ni reemplaza esa
validación.

## Consecuencias

- Los artefactos IA-1 se guardan en `vectors_ia1.json`, separados de los de IA-2.
- Deben reportarse cobertura de vocabulario y ocurrencias, incluyendo tokens representados mediante
  subpalabras cuando el generador lo permita.
- Deben registrarse identidad, versión, procedencia, licencia y hash del modelo y de las salidas.
- Los documentos sin tokens vectorizables deben producir un resultado explícito y no una similitud
  válida implícita.
- La dimensión esperada es 300; cualquier cambio exige versionar el contrato y actualizar pruebas
  y metadatos.
