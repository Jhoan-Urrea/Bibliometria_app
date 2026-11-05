# Módulo `api`

## Propósito

Coordinar casos de uso y exponer mediante HTTP los servicios bibliométricos a clientes, incluida la aplicación web.

## Contratos públicos / Interfaces

- Endpoints, esquemas de entrada y salida, validaciones y errores con semántica estable.
- Adaptadores web con Spring Boot Web y validación de solicitudes.
- Orquestación de `core` y `embeddings`; no exponer directamente tipos internos de framework en los contratos públicos.

## Justificación técnica / matemática

La API publica indicadores calculados conforme a las definiciones únicas de `docs/DOCUMENTO_GENERAL.md`, sección “Formulación matemática v2.0”. Cada respuesta debe permitir identificar filtros y contexto temporal pertinentes. La capa HTTP valida la entrada, pero las invariantes del dominio se mantienen en `core`.

## Criterios de independencia

- Mantener transporte, serialización y validación HTTP fuera de `core`.
- Depender de `core` y `embeddings`, nunca de `ingest` o `benchmark`.
- Evitar que controladores contengan cálculos bibliométricos.
- Probar contratos, validaciones, códigos de estado y errores.
- No depender de detalles internos del frontend.

## Rutas de DF v2.1

`GET /api/health` responde `UP` después de que Spring crea el servicio de análisis eager. DF v2.1 registra además
`/api/similarity/trace`, `/api/search/trace` y `/api/similarity/rank-correlation`; se consideran
pendientes hasta contar con controladores y pruebas de contrato. Sus criterios y límites están
registrados en [`ADR-012`](../docs/adr/ADR-012-contrato-busqueda.md).

## Contrato HTTP (P5.1)

La fuente única del contrato HTTP es [`docs/openapi.yaml`](../docs/openapi.yaml). Define
`GET /api/articles`, `POST /api/search`, `POST /api/similarity`, `POST /api/clustering` y
`GET /api/health`; los DTO HTTP se adaptan a los modelos inmutables de `core`, sin serializar
objetos internos de preprocesamiento ni matrices Java. Los errores usan
`application/problem+json` según RFC 7807. Las rutas de trazas registradas en ADR-012 quedan fuera
de esta primera versión hasta definir sus parámetros y esquemas específicos.

Los algoritmos léxicos de similitud y la búsqueda BM25 delegan en `core`. FastText y Sentence-BERT
solo se habilitan cuando `bibliometria.embeddings.directory` apunta a un directorio con vectores y
metadatos válidos; sin esos artefactos, la similitud semántica responde `422` en vez de fabricar
una puntuación.

## Integración y arranque seguro (P5.2)

La API construye un único contexto de análisis inmutable a partir de `core` y `embeddings`. La
creación del contexto debe ser eager y secuencial; cada resultado se valida antes de pasar al
siguiente paso:

1. Cargar y validar el corpus JSON (estructura, IDs únicos y campos requeridos).
2. Cargar las stopwords versionadas del classpath.
3. Preprocesar todos los artículos con `TextPreprocessor`.
4. Construir el modelo TF-IDF/matriz de distancias usando el corpus preprocesado.
5. Si está configurado `bibliometria.embeddings.directory`, cargar y validar embeddings y
   metadatos: dimensiones, IDs, norma cuando aplique y SHA-256.
6. Calcular el dendrograma para los tres métodos de enlace.
7. Publicar el contexto completo y marcar el estado de readiness como `UP`; solo entonces
   `/api/health` devuelve `200`.

El ensamblaje pertenece al módulo `api` (configuración/fábrica de beans); los cálculos e
invariantes siguen en `core`, y la lectura/validación de vectores en `embeddings`. La API no debe
introducir una dependencia de `ingest` o `benchmark`. Un fallo en cualquier recurso o validación
crítica impide marcar el servicio como listo y debe dejar evidencia en el log de arranque; no se
publica un contexto parcial. Spring no acepta solicitudes hasta completar la creación eager del
contexto. Los errores de solicitud se mapean a `400` (validación), `404` (artículo
inexistente) o `422` (cálculo no definido para los datos); los errores inesperados no incluyen
stack traces ni rutas internas en la respuesta. Cada petición recibe un `X-Correlation-ID`, también
incluido como extensión en Problem Details. Esta fase no configura autenticación: el servicio
no debe exponerse fuera de un límite de red confiable hasta definirla.

La prueba de integración de arranque debe verificar el orden de carga, impedir `UP` antes de
completarlo, y comprobar que cualquier fallo crítico impide servir resultados parciales. Las
pruebas de contrato deben validar las respuestas y Problem Details contra `docs/openapi.yaml`.
