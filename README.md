# Bibliometria_app

Aplicación de análisis bibliométrico desarrollada como proyecto final de la asignatura
Análisis de algoritmos. El backend usa Java 21 y Maven multimódulo; la interfaz usa React,
TypeScript, Vite y D3.

## Módulos

- `core`: dominio, preprocesamiento, similitud, búsqueda y clustering.
- `ingest`: lectura y normalización de fuentes bibliográficas.
- `embeddings`: carga y validación de vectores semánticos.
- `api`: servicios HTTP y empaquetado de la interfaz.
- `tools` y `benchmark`: análisis fuera de línea y mediciones.
- `web`: interfaz de usuario.

## Documentación y trazabilidad

- [Documento general, arquitectura y trazabilidad](docs/DOCUMENTO_GENERAL.md)
- [Contrato y operaciones de `core`](core/README.md)
- [Diseño de similitudes](docs/final/documento-diseno.md)
- [Implementación de clustering y matriz de distancias](docs/req/req2/implementacion-req2.md)
- [Búsqueda exacta y caso de estudio](docs/casos-de-estudio/ce3/README.md)

Las fórmulas y variables de cada algoritmo se relacionan con sus símbolos de implementación en
los Javadocs de las clases correspondientes; los documentos técnicos describen las convenciones
matemáticas y los límites metodológicos.

## Verificación

```sh
mvn clean verify
cd web
npm ci
npm run lint
npm test
npm run build
```
