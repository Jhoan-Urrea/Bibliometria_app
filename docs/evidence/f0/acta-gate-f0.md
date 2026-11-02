# Acta de Gate F0

## Datos de la revisión

- **Proyecto:** Aplicativo bibliométrico
- **Gate:** Fase 0 a Fase 1
- **Fecha de revisión:** ____________________
- **Versión / commit revisado:** ____________________
- **Estado del acta:** Plantilla pendiente de revisión y firmas

## Criterios de salida

Marcar un criterio como aprobado solo después de adjuntar o enlazar evidencia reproducible.

| ID | Criterio | Evidencia requerida | Resultado |
|---|---|---|---|
| F0-01 | Estructura Maven multimódulo y documentación arquitectónica unificada | `mvn clean verify`; revisión de `docs/DOCUMENTO_GENERAL.md` y README de módulos | [ ] Aprobado [ ] Pendiente |
| F0-02 | Calidad Maven y cobertura objetivo configuradas | Ejecución de Spotless y reporte JaCoCo; cobertura de líneas de `core` >= 80% cuando haya fuentes y pruebas | [ ] Aprobado [ ] Pendiente |
| F0-03 | CI de backend y frontend operativa | Enlaces a ejecución de GitHub Actions en verde para la revisión | [ ] Aprobado [ ] Pendiente |
| F0-04 | Corpus de humo inventariado | 20 PDF disponibles, `data/manifest.csv` con SHA-256 y verificación independiente | [ ] Aprobado [ ] Pendiente |
| F0-05 | Contenedor de humo operativo | `docker compose up --build` y respuesta HTTP 200 de `GET /api/health` | [ ] Aprobado [ ] Pendiente |
| F0-06 | Decisiones de arquitectura trazadas | ADR P-01 a P-09 revisados; pendientes identificados con responsable y fecha | [ ] Aprobado [ ] Pendiente |
| F0-07 | Uso de IA generativa auditado | Registro actualizado y revisión humana documentada | [ ] Aprobado [ ] Pendiente |

## Evidencias y observaciones

- **Build / CI:** ________________________________________________
- **Manifiesto y corpus:** ________________________________________
- **Docker / endpoint:** __________________________________________
- **Riesgos o pendientes aceptados:** ______________________________

## Resolución

- [ ] **Aprobado:** se autoriza el inicio de la Fase 1.
- [ ] **Aprobado con condiciones:** inicio sujeto a las condiciones anotadas y con responsables/fechas asignados.
- [ ] **No aprobado:** no se autoriza el inicio de la Fase 1.

La autorización solo es efectiva cuando los criterios de salida han sido revisados, las evidencias están disponibles y las personas responsables han firmado esta acta. Una plantilla sin firmas no constituye una aprobación.

## Aprobaciones

| Rol | Nombre | Firma | Fecha |
|---|---|---|---|
| Responsable técnico |  |  |  |
| Responsable del proyecto |  |  |  |
| Responsable metodológico / datos |  |  |  |