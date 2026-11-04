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
- Depender de `core` y `embeddings`, nunca de `ingest` o `benchmark`.
- Evitar que controladores contengan cálculos bibliométricos.
- Probar contratos, validaciones, códigos de estado y errores.
- No depender de detalles internos del frontend.

## Rutas de DF v2.1

`GET /api/health` es la ruta de salud implementada. DF v2.1 registra además
`/api/similarity/trace`, `/api/search/trace` y `/api/similarity/rank-correlation`; se consideran
pendientes hasta contar con controladores y pruebas de contrato. Sus criterios y límites están
registrados en [`ADR-012`](../docs/adr/ADR-012-contrato-busqueda.md).
