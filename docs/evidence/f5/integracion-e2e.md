# Evidencia Fase 5: integración y E2E

## Decisiones de integración

- Maven instala Node/npm en `api/target`, ejecuta `npm ci` y compila Vite antes de copiar `web/dist` a `BOOT-INF/classes/static`. El JAR resultante sirve la interfaz y `/api/*` desde el mismo origen, evitando una segunda aplicación web y problemas de CORS.
- `GET /api/search/trace` devuelve posiciones y fragmentos del corpus; `articleId` opcional solicita los eventos detallados de un solo artículo. Esto mantiene acotada la respuesta del barrido y conserva las trazas completas bajo demanda.
- Docker Compose publica HTTP solo en `127.0.0.1:8080` para ejecución local. En despliegues públicos, TLS debe terminar en un proxy/ingress de confianza.

## Verificaciones

| Comprobación | Resultado |
|---|---|
| `mvn --batch-mode --no-transfer-progress -pl api -am verify` | Correcto; incluye build frontend, pruebas Java y empaquetado. |
| `npm --prefix web run build`, `lint` y `test` | Correcto; 4 pruebas unitarias aprobadas. |
| `cd web && npm run test:e2e` | Correcto; 3 flujos Chromium aprobados contra el JAR integrado: similitud, cambio de criterio/dendrograma y búsqueda exacta con trazas. |
| Contenido del JAR | `BOOT-INF/classes/static/index.html` y recursos Vite presentes. |
| Docker Compose local | Bloqueado en este entorno: no está instalado `docker` (ni CLI de Compose), por lo que no se pudo iniciar un daemon ni certificar el contenedor. |

Con Docker disponible, ejecutar desde la raíz:

```sh
docker compose up --build -d
curl --fail http://127.0.0.1:8080/api/health
docker compose down
```
