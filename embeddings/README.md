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
- No acoplar la generación de embeddings a la API HTTP.
- Validar dimensiones, valores no finitos y vectores nulos.
- Probar la similitud con casos de referencia calculables manualmente.
