## Resumen

<!-- Describe brevemente el cambio y su propósito. -->

## Trazabilidad

- Fase / paso: <!-- P0.1, P1.2, ... -->
- Artefacto: <!-- AR-F{fase}-{nn} -->
- Módulo(s): <!-- core, ingest, embeddings, api, benchmark, web -->

## Definition of Done

- [ ] Compilación limpia del proyecto (`mvn clean verify`).
- [ ] Formato y convenciones del proyecto verificados.
- [ ] Pruebas automatizadas en verde.
- [ ] Cobertura de `core` igual o superior al 80%.
- [ ] `core` no incorpora dependencias externas en producción; solo usa el JDK en runtime.
- [ ] Documentación del módulo actualizada en su `README.md`.
- [ ] `docs/DOCUMENTO_GENERAL.md` actualizado si cambian arquitectura, formulación o trazabilidad.
- [ ] No se incorporan datos locales, secretos ni archivos generados.

## Validación

<!-- Indica los comandos ejecutados y cualquier resultado relevante. -->
