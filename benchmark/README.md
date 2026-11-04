# Módulo `benchmark`

## Propósito

Medir de forma repetible el rendimiento de operaciones bibliométricas seleccionadas y detectar regresiones con datos y entornos declarados.

## Contratos públicos / Interfaces

- Escenarios de benchmark con operación, parámetros y tamaño de datos explícitos.
- Resultados que reportan unidad, configuración, versión del código y entorno de ejecución.
- Integración con las operaciones públicas de `core`; no debe redefinir su comportamiento.

## Justificación técnica / matemática

Una medición de rendimiento solo es interpretable si identifica el conjunto de entrada, el método y el entorno. Los datos deben ser reproducibles y representativos; el benchmark no altera ni aproxima la definición matemática de los indicadores, centralizada en `docs/DOCUMENTO_GENERAL.md`.

## Criterios de independencia

- Depender únicamente de `core` para las operaciones del dominio.
- No depender de `api`, `ingest` ni `tools`. Las herramientas fuera de línea pueden leer resultados
  guardados mediante un formato de archivo, sin acoplamiento Maven.
- Separar medición de pruebas unitarias y de lógica de producción.
- Evitar datos locales pesados o sensibles en el repositorio.
- Documentar configuración, unidad y limitaciones de cada resultado.

## Resultados guardados

DF v2.1 requiere que `tools` pueda leer resultados de benchmark guardados. Antes de producir o
consumir esos resultados debe definirse y versionarse su esquema, incluyendo operación, parámetros,
unidad, configuración, versión del código y entorno. El lector no debe requerir una dependencia de
este módulo.
