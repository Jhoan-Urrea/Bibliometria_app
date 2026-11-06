# Plantilla de revisión manual doble — Corpus Fase 1

Copiar este checklist por registro o completar `checklists.csv`. Cada revisor debe contrastar los
valores contra el PDF indicado por `filename` y confirmar por separado:

- [ ] Título completo y sin cabeceras, editorial, DOI o texto de página.
- [ ] Todos los autores y orden cotejados con el PDF.
- [ ] Abstract completo y fiel, con al menos 100 caracteres y sin texto del cuerpo.
- [ ] `paginaAbstract` señala la página física donde comienza el abstract.
- [ ] `language` identificado correctamente.
- [ ] SHA-256 recalculado desde el PDF y coincidente con `data/manifest.csv`.
- [ ] Notas de extracción resueltas o aceptadas expresamente.

| Registro | Revisor 1 (nombre/fecha) | Resultado 1 | Revisor 2 (nombre/fecha) | Resultado 2 | Discrepancias / resolución |
|---|---|---|---|---|---|
| A01 |  | Pendiente |  | Pendiente |  |

No cambiar `verified` a `true` ni completar `verificadoPor` hasta que ambos revisores hayan
firmado y las discrepancias estén resueltas. `verified` representa una validación humana, no la
ausencia de errores de parseo.
