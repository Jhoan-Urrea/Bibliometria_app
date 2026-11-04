# ADR-012: Contrato de búsqueda, trazas y correlación

- **Estado:** Aceptado como contrato técnico de DF v2.1; implementación de los endpoints indicada
  como pendiente hasta que pase sus pruebas de contrato.
- **Fecha:** 2026-11-04
- **Ámbito:** `core`, `api`, `tools`
- **Relacionado:** ADR-004, `docs/gestion/cambios-df.md` (DF21-02, DF21-03 y DF21-05)

## Contexto

DF v2.1 requiere búsqueda exacta auditable, trazas exportables, correlación de rangos y lectura
fuera de línea de resultados guardados. El núcleo debe conservar independencia respecto de HTTP,
Jackson y los módulos de ingesta o benchmark.

## Opciones

1. Implementar los algoritmos y el transporte en controladores HTTP.
2. Mantener algoritmos y resultados en `core`, y exponerlos desde `api` mediante adaptadores.
3. Hacer que las herramientas dependan del ejecutor `benchmark` para leer sus resultados.

## Decisión

Se adopta la opción 2. `core` define resultados y operaciones neutrales respecto del transporte;
`api` es responsable de las rutas HTTP y `tools` procesa archivos fuera de línea. `tools` no
depende de `benchmark`: la integración entre ambos se realiza mediante un formato de archivo
documentado y validado.

Las rutas requeridas son:

- `GET /api/search/trace`: traza de una búsqueda exacta.
- `GET /api/similarity/trace`: traza de una operación de similitud.
- `GET /api/similarity/rank-correlation`: correlación de rangos de resultados de similitud.

Los parámetros, cuerpos, esquemas de respuesta y códigos de error de estas rutas deben definirse
y probarse en el contrato de API antes de considerar los endpoints implementados. Las trazas
conservan algoritmo, paso y datos; no deben incluir información bibliográfica sensible que no
sea necesaria para reproducir el cálculo.

### Contrato del resultado de búsqueda

`MatchResult` conserva una lista inmutable de posiciones no negativas, tiempos no negativos de
preprocesamiento y búsqueda, el contador de comparaciones y un mapa inmutable `extras`. Las claves
no pueden ser nulas. Para Rabin-Karp, `extras` contiene:

- `hashCoincidencias`: número de ventanas cuyo hash coincidió con el hash del patrón.
- `falsosPositivos`: número de coincidencias de hash que no superaron la verificación exacta.

### Función `codigo` de Rabin-Karp

En las fórmulas del algoritmo, `codigo(c)` es el valor numérico del `char` UTF-16 de Java. Por
tanto, `codigo(c) = c.charAt(0)` para un carácter Java representado por una unidad UTF-16; en la
implementación actual, la promoción numérica de `charAt` aplica directamente esa codificación al
calcular los hashes. No se afirma que exista un método Java separado llamado `codigo`.

El hash coincidente siempre se verifica carácter a carácter antes de registrar una posición. Las
posiciones conservan la convención UTF-16 del contrato actual de `StringMatcher`.

## Consecuencias

- `core` no depende de `api`, `tools`, `benchmark`, `ingest` ni de bibliotecas de transporte.
- Las rutas listadas son requisitos de API; la presencia de `/api/health` no demuestra su
  implementación.
- Cualquier modificación de `extras`, su tipado o la codificación de caracteres requiere
  actualizar pruebas y este ADR.
- Los resultados guardados de benchmark se leen como archivos validados, no por una dependencia
  entre módulos.
