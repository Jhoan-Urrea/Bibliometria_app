# Registro de uso de IA generativa

Registro de auditoría basado en los campos del Anexo C de la guía del proyecto.
Cada uso que afecte artefactos del repositorio debe documentarse y verificarse.

| Fecha | Herramienta / Modelo | Propósito | Código / sección afectada | Método de verificación |
|---|---|---|---|---|
| 2026-11-01 | GitHub Copilot (modelo no expuesto en esta sesión) | Preparar automatización de manifiesto, servicio HTTP de humo, contenedores y artefactos de gobierno de Fase 0 | `scripts/generate_manifest.py`, `api`, `Dockerfile`, `docker-compose.yml`, `docs/DOCUMENTO_GENERAL.md`, `docs/evidence/f0/acta-gate-f0.md` | Build Maven, prueba HTTP de `/api/health`, prueba temporal del generador con 20 PDFs sintéticos, validación de configuración Compose y revisión de contenido; registrar resultados finales antes de aprobar el Gate F0 |

## Registro de cambios

| Fecha | Responsable de revisión | Observaciones |
|---|---|---|
|  |  |  |