# Registro de cambios del Documento de Formulación v2.1

Este registro conserva la trazabilidad de los cambios de DF v2.1 respecto del diseño previo.
“Registrado” significa que el cambio está documentado; no implica que la funcionalidad esté
implementada o desplegada. El estado se indica por cambio para evitar confundir diseño con
evidencia de ejecución.

| ID | Cambio incorporado | Módulos / artefactos afectados | Estado observado |
|---|---|---|---|
| DF21-01 | Se incorpora `tools` al reactor Maven. Consume `core`, `embeddings` y Jackson; no depende de `benchmark` para leer sus resultados guardados. | `pom.xml`, `tools/pom.xml`, `tools/README.md` | Reactor y contrato documental incorporados. Las utilidades ejecutables siguen pendientes. |
| DF21-02 | Se establecen las rutas de trazas `/api/similarity/trace` y `/api/search/trace`, y la ruta de correlación de rangos `/api/similarity/rank-correlation`. | `api`, `core`, `docs/adr/ADR-012-contrato-busqueda.md` | Rutas registradas como requisito. La inspección actual solo confirma `/api/health`; endpoints pendientes de implementación y pruebas. |
| DF21-03 | `tools` leerá resultados de benchmark guardados como artefactos, sin acoplarse al módulo ejecutor `benchmark`. | `tools`, `benchmark` | Límite arquitectónico documentado. Formato y lector ejecutable pendientes de concretar/implementar. |
| DF21-04 | Los vectores se almacenan en archivos separados por modelo: `vectors_ia1.json` para IA-1 y `embeddings_ia2.json` para IA-2. | `embeddings`, `docs/final/documento-diseno.md` | Nombres y carga diferenciada ya están presentes en `JsonEmbeddingProvider`; conservar metadatos y SHA-256 por artefacto. |
| DF21-05 | Se ajustan las decisiones de IA-1 y del contrato de búsqueda: FastText con subpalabras, función `codigo` de Rabin-Karp y mapa `extras`. | ADR-004, ADR-012, `core` | Decisiones registradas en ADR. El idioma de trabajo vigente es inglés según ADR-001; la identidad/licencia del artefacto FastText se registra antes de generar resultados. |

## Reglas de seguimiento

- Cada endpoint se considera implementado únicamente cuando existe controlador, contrato probado y
  documentación de solicitud/respuesta.
- La lectura de benchmark se considera implementada únicamente cuando se fija y valida el esquema
  del archivo consumido.
- Los cambios de modelo y corpus deben enlazarse a la decisión de idioma y a la procedencia del
  artefacto.
- Las modificaciones posteriores deben añadir fecha, responsable, evidencia y estado sin borrar
  el historial.
