# Registro de uso de IA generativa

Registro de auditoría basado en los campos del Anexo C de la guía del proyecto.
Cada uso que afecte artefactos del repositorio debe documentarse y verificarse.

| Fecha | Herramienta / Modelo | Propósito | Código / sección afectada | Método de verificación |
|---|---|---|---|---|
| 2026-11-01 | GitHub Copilot (modelo no expuesto en esta sesión) | Preparar automatización de manifiesto, servicio HTTP de humo, contenedores y artefactos de gobierno de Fase 0 | `scripts/generate_manifest.py`, `api`, `Dockerfile`, `docker-compose.yml`, `docs/DOCUMENTO_GENERAL.md`, `docs/evidence/f0/acta-gate-f0.md` | Build Maven, prueba HTTP de `/api/health`, prueba temporal del generador con 20 PDFs sintéticos, validación de configuración Compose y revisión de contenido; registrar resultados finales antes de aprobar el Gate F0 |
| 2026-11-04 | GitHub Copilot SDK en VS Code (modelo no expuesto) | Asistencia a la consolidación técnica de Fase 6: análisis de benchmark, síntesis de arquitectura, matriz de cumplimiento, declaración de IA y preparación del release | `tools/.../benchmark/AnalisisBenchmark.java`, `benchmark`, `docs/evidence/f6/informe-benchmark.md`, `docs/DOCUMENTO_FINAL.md` | `mvn -pl tools -am verify`; ejecución E1/E2/E3 con 1.260 muestras; inspección del JAR API y del JAR benchmark; validación final de build/tag. La aprobación humana y cotejo con la rúbrica externa permanecen pendientes. |

La asistencia generativa no sustituye revisión humana. El modelo exacto no se registra cuando la
plataforma no lo expone; no se debe inferir ni completar ese dato mediante suposiciones. La
persona responsable del proyecto conserva la responsabilidad de aprobar código, resultados,
fuentes, licencias y documentación antes de la entrega académica.

## Declaración de modelos IA del aplicativo

El corpus vigente de 20 registros se declara predominantemente en inglés conforme a
[ADR-001](../adr/ADR-001-idioma-corpus.md). Por ello, las representaciones semánticas se generan
con modelos compatibles con inglés. Esta declaración corresponde a los modelos funcionales del
aplicativo y no debe confundirse con el uso de IA generativa para desarrollar el software.

| Componente | Modelo y uso declarado | Justificación por idioma | Decisión y control |
|---|---|---|---|
| IA-1 | FastText, 300 dimensiones, con composición por subpalabras; el artefacto inglés concreto debe registrarse antes de generar resultados. | El vocabulario y los textos del corpus vigente son predominantemente ingleses; las subpalabras ayudan con términos técnicos y formas no vistas, pero no sustituyen la compatibilidad lingüística. | [ADR-001](../adr/ADR-001-idioma-corpus.md), [ADR-004](../adr/ADR-004-modelo-ia1.md). Registrar versión, licencia, procedencia y SHA-256 del artefacto. |
| IA-2 | Sentence-Transformers `sentence-transformers/all-MiniLM-L6-v2`, embeddings de 384 dimensiones normalizados. | El checkpoint se usa para la representación contextual del corpus en inglés; no se asume soporte equivalente para idiomas distintos. | [ADR-001](../adr/ADR-001-idioma-corpus.md), [ADR-005](../adr/ADR-005-modelo-ia2.md). Registrar versión, licencia, procedencia y hashes en `embeddings.meta.json`. |

Si cambia el idioma o la composición del corpus, se debe revisar la compatibilidad y reevaluar ambos
modelos antes de producir o comparar resultados.

## Registro de cambios

| Fecha | Responsable de revisión | Observaciones |
|---|---|---|
|  |  |  |