# Módulo `api`

## Propósito

Coordinar casos de uso y exponer mediante HTTP los servicios bibliométricos a clientes autorizados, incluida la aplicación web.

## Contratos públicos / Interfaces

- Endpoints, esquemas de entrada y salida, validaciones y errores con semántica estable.
- Adaptadores web con Spring Boot Web y validación de solicitudes.
- Orquestación de `core` y `embeddings`; no exponer directamente tipos internos de framework en los contratos públicos.

## Justificación técnica / matemática

La API publica indicadores calculados conforme a las definiciones únicas de `docs/DOCUMENTO_GENERAL.md`, sección “Formulación matemática v2.0”. Cada respuesta debe permitir identificar filtros y contexto temporal pertinentes. La capa HTTP valida la entrada, pero las invariantes del dominio se mantienen en `core`.

## Criterios de independencia

- Mantener transporte, serialización y validación HTTP fuera de `core`.
- Evitar que controladores contengan cálculos bibliométricos.
- Probar contratos, validaciones, códigos de estado y errores.
- No depender de detalles internos del frontend.
