# Módulo `ingest`

## Propósito

Adaptar fuentes bibliográficas de entrada, extraer metadatos, validar registros y transformarlos al contrato de dominio acordado.

## Contratos públicos / Interfaces

- Adaptadores de lectura para formatos admitidos, incluidos PDF y JSON.
- Resultado explícito de extracción y errores de formato, validación o lectura.
- Salida normalizada y neutral respecto al formato, preparada para su consumo por capas posteriores.

## Justificación técnica / matemática

La extracción y normalización se separan de los indicadores para que las métricas operen sobre datos con reglas de calidad conocidas. La deduplicación, normalización y tratamiento de campos ausentes deben ser explícitos y trazables; no se debe inferir metadatos sin registrar esa transformación.

## Criterios de independencia

- Mantener PDFBox y Jackson confinados al adaptador.
- No implementar indicadores bibliométricos ni lógica HTTP.
- Probar archivos válidos, incompletos, malformados y límites de entrada.
- Registrar la procedencia y las transformaciones aplicadas a cada registro.

## Ingesta del corpus PDF

`PdfIngestorService` valida los 20 archivos de `data/raw/` contra los SHA-256 de
`data/manifest.csv` y escribe los registros en JSON mediante una sustitución atómica del archivo.
La salida contiene `id`, `title`, `authors`, `abstractText` y `verified` (inicializado en `false`).
La extracción de texto ordena las posiciones del PDF antes de aplicar heurísticas de encabezados
y metadatos, incluyendo artículos con texto dispuesto en columnas.

Ejecuta el proceso local y valida la forma del JSON con:

```sh
mvn -pl ingest -am -Dtest=PdfIngestorServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Para producir el corpus desde el IDE, ejecuta `PdfIngestorService.main` con el directorio de
trabajo configurado en la raíz del proyecto (`~/Documentos/Bibliometria_app`). El método escribe
`data/corpus.json` allí y falla explícitamente si falta un PDF o no coincide su SHA-256 con el
manifiesto.
