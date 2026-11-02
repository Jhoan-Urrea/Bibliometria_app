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
