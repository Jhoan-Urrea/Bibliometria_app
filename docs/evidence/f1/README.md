# Evidencia de Fase 1

## Corpus aprobado

`data/corpus.json` es la fuente y `api/src/main/resources/data/corpus.json` es su copia de
runtime. `docs/evidence/f1/corpus.sha256` fija el hash del archivo fuente. La generación por
`PdfIngestorService.main` valida los PDF contra `data/manifest.csv`; conserva metadatos y
aprobaciones humanas únicamente cuando coinciden el ID, nombre de archivo y SHA-256 del PDF.

## Golden files y estadísticas

Después de generar el corpus, ejecutar:

```sh
scripts/generate_f1_evidence.py
```

El script verifica hash, copia de API, campos obligatorios y correspondencia con los PDF del
manifiesto; luego crea `core/src/test/resources/golden/A01.json`–`A20.json`,
`core/src/test/resources/golden/corpus.json` y `estadisticas.md`.

El reporte usa `P-TOKENS + 2` como aproximación al conteo de tokens IA-2 si no se carga el
tokenizer. Para recuento real con `all-MiniLM-L6-v2`, instalar `sentence-transformers` en el
entorno Python aprobado y ejecutar `scripts/generate_f1_evidence.py --exact-tokenizer`. El
repositorio no incluye actualmente un archivo de requisitos Python; el reporte distingue
explícitamente ambos métodos.

## Revisión manual doble

`checklists.csv` contiene un renglón por documento y dos juegos de campos de revisión. Los
registros A01–A20 fueron aprobados por Jhoan Urrea y Tatiana el 04/11/2026; las alertas de A06,
A13 y A20 se cotejaron contra sus PDF originales y se resolvieron en el corpus. La generación
automática no crea aprobaciones; las conserva solo para el mismo PDF cuyo SHA-256 fue revisado.
