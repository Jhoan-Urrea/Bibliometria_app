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
- Mantener cobertura de pruebas igual o superior al 80%.
- Verificar invariantes y casos límite con resultados reproducibles.
