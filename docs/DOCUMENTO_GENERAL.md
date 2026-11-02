# Documento general del proyecto

> Documento único para el diseño, la arquitectura, la formulación matemática y la trazabilidad del proyecto. La documentación de cada módulo se limita a su contrato, alcance y justificación técnica/matemática en el `README.md` de dicho módulo.

## 1. Identificación y propósito

- **Proyecto:** Aplicativo bibliométrico
- **Versión del documento:** 1.0
- **Estado:** Borrador
- **Propósito:** Definir la arquitectura y el método reproducible para ingerir, normalizar y analizar información bibliográfica, y exponer resultados mediante una API y una aplicación web.

### Objetivos

1. Ingerir fuentes bibliográficas y conservar la trazabilidad de los datos.
2. Normalizar metadatos con reglas explícitas y reproducibles.
3. Calcular indicadores bibliométricos y representaciones semánticas con responsabilidades separadas.
4. Exponer resultados mediante contratos estables y verificables.
5. Validar calidad, rendimiento y reproducibilidad mediante pruebas y benchmarks.

### Alcance y exclusiones

- El backend se implementa en Java 21 y Maven como proyecto multimódulo.
- El frontend se implementa en React y TypeScript dentro de `web/`.
- Las fuentes, formatos admitidos, indicadores finales y políticas de privacidad deben precisarse en las fases correspondientes.
- Los datos locales de origen no se versionan; los datos de prueba deben ser sintéticos, pequeños o tener licencia compatible.

## 2. Diseño de arquitectura

### 2.1 Vista lógica

```text
Fuentes bibliográficas
        |
        v
     ingest  ---> registros normalizados ---> core
                                             /    \
                                            v      v
                                      embeddings  benchmark
                                            \      /
                                             v    v
                                               api
                                                |
                                                v
                                               web
```

`core` contiene el modelo y las reglas de dominio independientes de infraestructura. `ingest` adapta formatos de entrada; `embeddings` integra el procesamiento semántico; `api` coordina y publica casos de uso; `benchmark` mide el comportamiento; `web` consume la API.

### 2.2 Módulos y dependencias permitidas

| Módulo | Responsabilidad | Dependencias internas |
|---|---|---|
| `core` | Dominio, contratos y cálculos bibliométricos puros | Ninguna |
| `ingest` | Lectura y adaptación de documentos y metadatos | `core` cuando sea necesario |
| `embeddings` | Representaciones vectoriales y operaciones semánticas | `core` |
| `api` | Casos de uso y exposición HTTP | `core`, `embeddings` |
| `benchmark` | Medición reproducible de rendimiento | `core` |
| `web` | Interfaz de usuario React + TypeScript | API HTTP |

`core` solo puede depender del JDK en producción. Las dependencias de prueba permitidas se limitan a las centralizadas en el POM raíz.

### 2.3 Principios y decisiones

- Dependencias orientadas hacia el dominio; los adaptadores dependen de los contratos del dominio, no al contrario.
- UTF-8 y Java 21 en todos los módulos Maven.
- Versiones de dependencias gestionadas desde el POM raíz.
- Contratos públicos explícitos, validables y documentados en el README del módulo correspondiente.
- Las operaciones deterministas deben producir resultados reproducibles con los mismos datos, parámetros y versión de algoritmo.
- Los cambios de arquitectura, metodología o trazabilidad se registran aquí; no se crean documentos generales paralelos en la raíz.

### 2.4 Calidad y operación

- Compilación y pruebas: `mvn clean verify`.
- Cobertura objetivo de `core`: al menos 80%.
- Cada módulo debe tener pruebas unitarias de su lógica y pruebas de contrato cuando corresponda.
- Los benchmarks deben registrar conjunto de datos, configuración, entorno y versión del código.
- La configuración sensible se inyecta por entorno; no se almacena en el repositorio.
- Los datos reales pesados y los datos originales se mantienen fuera del control de versiones.

## 3. Formulación matemática v2.0

> Esta sección es la fuente única de la formulación. Las fórmulas siguientes son la plantilla metodológica inicial y deben ratificarse con las fuentes, definiciones operativas y decisiones del proyecto antes de usarse como resultados científicos.

### 3.1 Notación y datos

Sea \(D=\{d_1,\ldots,d_n\}\) el conjunto de documentos bibliográficos válidos. Para cada documento \(d_i\), se registran metadatos normalizados (identificador, año, autores, fuente, palabras clave y referencias) y, cuando aplique, un vector semántico \(v_i\in\mathbb{R}^p\). Los metadatos ausentes se representan como desconocidos, no como valores inferidos.

### 3.2 Indicadores bibliométricos

- **Producción en el periodo \(t\):**
  \[
  P_t=\sum_{i=1}^{n}\mathbf{1}[\operatorname{año}(d_i)=t].
  \]
- **Citas del documento \(d_i\):** \(c_i\), según la fuente y la fecha de corte declaradas.
- **Promedio de citas del conjunto \(S\):**
  \[
  \bar{c}_S=\frac{1}{|S|}\sum_{d_i\in S}c_i,\qquad |S|>0.
  \]
- **Índice h:** \(h=\max\{k: c_{(k)}\ge k\}\), donde \(c_{(k)}\) son las citas ordenadas de mayor a menor.

Cada indicador debe especificar universo, filtros, tratamiento de duplicados, ventana temporal, fecha de corte y convención para datos faltantes. No se comparan indicadores calculados con universos o convenciones incompatibles.

### 3.3 Similitud semántica

Para vectores no nulos \(v_i,v_j\), la similitud coseno se define como
\[
\operatorname{sim}(d_i,d_j)=\frac{v_i\cdot v_j}{\lVert v_i\rVert_2\lVert v_j\rVert_2}.
\]
Los vectores de norma cero o ausentes no producen una similitud válida y deben manejarse explícitamente. Modelo, versión, preprocesamiento, dimensión y normalización de los embeddings forman parte de la trazabilidad del resultado.

### 3.4 Agregación y análisis de redes

Una red bibliográfica se representa como \(G=(V,E,W)\), donde los nodos y las aristas se derivan de una relación declarada (coautoría, cocitación, acoplamiento bibliográfico u otra), y \(W\) contiene pesos no negativos según una regla documentada. La selección de relación, normalización, umbrales y algoritmo de agrupamiento se configura y registra; no se asume una única definición para todas las redes.

### 3.5 Redondeo, incertidumbre y validación

- Los cálculos conservan precisión interna; el redondeo se aplica solo al presentar resultados y se documenta.
- Las métricas se validan con casos pequeños calculables manualmente y propiedades invariantes.
- Toda cifra debe ser rastreable a documentos, filtros, fecha de corte y versión de algoritmo.
- No se interpreta asociación como causalidad ni se presenta una estimación como completa cuando faltan datos.

## 4. Matriz de trazabilidad

| ID | Fase / paso | Requisito o decisión | Artefacto | Módulo | Criterio de aceptación | Evidencia |
|---|---|---|---|---|---|---|
| P0.1 | F0 / Inicialización | Estructura multimódulo y documentación unificada | AR-F0-01 | Transversal | Estructura y archivos base versionados; `mvn clean verify` ejecutable | PR y build |
| P0.2 | F0 / Calidad | Reglas de compilación y calidad inicial | AR-F0-02 | `core` | Java 21; pruebas y cobertura objetivo documentadas | Reporte de build |
| P1.1 | F1 / Dominio | Modelo bibliográfico y contratos | AR-F1-01 | `core` | Contratos y validaciones cubiertos por pruebas | Pruebas de `core` |
| P2.1 | F2 / Ingesta | Adaptadores de fuentes acordadas | AR-F2-01 | `ingest` | Archivos válidos, inválidos y límites tienen resultado verificable | Pruebas de ingesta |
| P3.1 | F3 / Semántica | Generación o consumo de embeddings | AR-F3-01 | `embeddings` | Dimensión, modelo y fallos quedan explícitos y trazables | Pruebas y metadatos |
| P4.1 | F4 / API | Casos de uso y contratos HTTP | AR-F4-01 | `api` | Validación de entrada y respuestas documentadas | Pruebas de API |
| P5.1 | F5 / Interfaz | Experiencia de consulta y visualización | AR-F5-01 | `web` | Flujos principales consumen contratos publicados | Pruebas de interfaz |
| P6.1 | F6 / Validación | Rendimiento, reproducibilidad y entrega | AR-F6-01 | `benchmark` | Resultados reproducibles y criterios de aceptación medidos | Reporte de benchmark |

Las filas son identificadores iniciales de trazabilidad; cada paso ejecutable debe tener una incidencia con ID, artefacto, módulo y criterios de aceptación. Mantener esta matriz sincronizada con los issues y los cambios entregados.

## 5. Formulación y entregables por fases

### Fase 0 — Inicialización y gobierno

- **Propósito:** establecer repositorio, estructura, reglas de calidad y fuente documental única.
- **Artefactos:** `AR-F0-01` estructura y documentación; `AR-F0-02` calidad inicial.
- **Aceptación:** módulos Maven declarados, contratos de contribución disponibles y build verificable.

### Fase 1 — Dominio bibliométrico

- **Propósito:** especificar entidades, identificadores, invariantes y operaciones del dominio sin infraestructura.
- **Artefactos:** `AR-F1-01` modelo y contratos; `AR-F1-02` cálculos de referencia.
- **Aceptación:** reglas documentadas y cubiertas con ejemplos de borde y pruebas deterministas.

### Fase 2 — Ingesta y normalización

- **Propósito:** acordar fuentes y formatos; implementar extracción, validación, normalización y errores auditables.
- **Artefactos:** `AR-F2-01` adaptadores; `AR-F2-02` reglas de normalización.
- **Aceptación:** entradas válidas, incompletas, duplicadas y malformadas tienen comportamiento definido y probado.

### Fase 3 — Procesamiento semántico

- **Propósito:** definir el ciclo de vida de embeddings y las operaciones semánticas admitidas.
- **Artefactos:** `AR-F3-01` contrato de embeddings; `AR-F3-02` estrategia de similitud.
- **Aceptación:** modelo, versión, dimensiones, límites y errores son explícitos y reproducibles.

### Fase 4 — Servicios y API

- **Propósito:** coordinar los casos de uso y exponer contratos HTTP validados.
- **Artefactos:** `AR-F4-01` API; `AR-F4-02` validaciones y errores.
- **Aceptación:** endpoints, esquemas, códigos de estado y límites están probados y documentados.

### Fase 5 — Aplicación web

- **Propósito:** permitir explorar datos e indicadores mediante React y TypeScript.
- **Artefactos:** `AR-F5-01` interfaz; `AR-F5-02` visualizaciones acordadas.
- **Aceptación:** los flujos prioritarios consumen la API y presentan estados de carga, error y vacío.

### Fase 6 — Validación y entrega

- **Propósito:** comprobar calidad funcional, reproducibilidad, rendimiento y preparación de despliegue.
- **Artefactos:** `AR-F6-01` benchmarks; `AR-F6-02` evidencia de validación.
- **Aceptación:** umbrales acordados se miden en un entorno descrito; resultados y limitaciones quedan trazados.

## 6. Registro de decisiones y cambios

| Fecha | Decisión / cambio | Motivo | Impacto | Referencia |
|---|---|---|---|---|
| 2026-10-31 | Un documento general y un README contractual por módulo | Evitar duplicidad y divergencia documental | Toda la documentación del proyecto | F0 |
| 2026-11-01 | Registrar decisiones metodológicas como ADR dentro de este documento | Mantener una fuente arquitectónica única | Sección 8 | F0 |

## 7. Glosario

| Término | Definición |
|---|---|
| Documento | Registro bibliográfico individual incluido en el universo de análisis |
| Embedding | Representación vectorial de una entrada textual generada por un modelo identificado |
| Universo de análisis | Conjunto de documentos que satisface filtros y reglas declarados |
| Fecha de corte | Fecha hasta la que se consideran datos externos, como citas |

## 8. Registro de decisiones de arquitectura (ADR)

Los ADR se mantienen en este documento para conservar una fuente única de arquitectura. Las decisiones indicadas como pendientes no deben tratarse como especificaciones ratificadas hasta completar la información y obtener aprobación del equipo.

### ADR P-01: Idioma del corpus

- **Estado:** Pendiente de decisión.
- **Contexto:** El idioma condiciona la selección del corpus, la normalización lingüística, las consultas y la evaluación de resultados.
- **Decisión:** Aún no se ha especificado el idioma del corpus. Debe acordarse antes de cerrar las reglas de ingesta y preprocesamiento.
- **Consecuencias:** No se asume idioma único ni se aplican reglas lingüísticas específicas hasta ratificar esta decisión.

### ADR P-02: FastText de 300 dimensiones

- **Estado:** Aceptado como baseline, pendiente de concretar artefacto y versión.
- **Contexto:** El proyecto requiere una representación vectorial reproducible para análisis bibliométrico semántico.
- **Decisión:** Considerar FastText con vectores de 300 dimensiones como baseline. El corpus/modelo preentrenado, idioma, licencia y versión deben registrarse antes de generar resultados.
- **Consecuencias:** La dimensión esperada es 300; la compatibilidad, la procedencia y la licencia del modelo siguen siendo criterios de aceptación.

### ADR P-03: Sentence-BERT

- **Estado:** Aceptado como alternativa semántica, pendiente de concretar checkpoint.
- **Contexto:** FastText y Sentence-BERT representan estrategias distintas y deben evaluarse con el mismo conjunto de validación.
- **Decisión:** Incluir Sentence-BERT en la evaluación. No se fija un nombre de modelo, dimensión o versión porque esos datos no están especificados.
- **Consecuencias:** No se deben comparar resultados sin registrar checkpoint, versión, idioma, preprocesamiento y dimensión del embedding.

### ADR P-04: Generación externa de embeddings

- **Estado:** Propuesto; contrato pendiente.
- **Contexto:** La generación de embeddings mediante una herramienta externa separa el coste de inferencia del ciclo de vida de la API Java.
- **Decisión:** Usar un script externo como mecanismo de generación/importación, sujeto a definir formato, esquema, versiones, errores y validación de integridad.
- **Consecuencias:** La API no debe depender de una ejecución implícita del script. Las entradas y salidas deben quedar versionadas o identificadas mediante manifiestos.

### ADR P-05: Stemming desactivado

- **Estado:** Aceptado como valor por defecto inicial.
- **Contexto:** El stemming puede reducir variantes morfológicas, pero también alterar nombres propios y términos técnicos.
- **Decisión:** Mantener el stemming desactivado por defecto. Un experimento podrá habilitarlo únicamente como configuración explícita y evaluada.
- **Consecuencias:** La normalización debe registrar esta opción para permitir reproducir cada análisis.

### ADR P-06: Búsqueda con mayúsculas por defecto

- **Estado:** Aceptado como comportamiento inicial; reglas Unicode pendientes de prueba.
- **Contexto:** Una normalización consistente de mayúsculas puede facilitar búsquedas sin distinguir capitalización.
- **Decisión:** Normalizar a mayúsculas tanto la consulta como el texto indexado en el modo de búsqueda predeterminado.
- **Consecuencias:** Deben conservarse los valores originales para presentación y trazabilidad; las reglas regionales y los caracteres Unicode requieren pruebas.

### ADR P-07: Proveedor PaaS

- **Estado:** Pendiente de selección.
- **Contexto:** El despliegue gestionado requiere acordar restricciones de coste, región, persistencia, observabilidad y compatibilidad con contenedores.
- **Decisión:** Se requiere un proveedor PaaS, pero el proveedor concreto no está indicado y no se presume ninguno.
- **Consecuencias:** No se fijan servicios específicos ni configuración de despliegue hasta documentar proveedor, entorno y criterios de salida.

### ADR P-08: Decisión pendiente de identificar

- **Estado:** Pendiente de definición.
- **Contexto:** La solicitud exige trazabilidad de P-01 a P-09, pero no aporta el tema de P-08.
- **Decisión:** Reservar el identificador sin asignarle una decisión no respaldada.
- **Consecuencias:** Completar contexto, decisión, alternativas y aprobación antes de considerar cerrado el registro.

### ADR P-09: Decisión pendiente de identificar

- **Estado:** Pendiente de definición.
- **Contexto:** La solicitud exige trazabilidad de P-01 a P-09, pero no aporta el tema de P-09.
- **Decisión:** Reservar el identificador sin asignarle una decisión no respaldada.
- **Consecuencias:** Completar contexto, decisión, alternativas y aprobación antes de considerar cerrado el registro.

## 9. Gobierno de datos y entrega de Fase 0

- Los PDF de origen permanecen fuera del control de versiones en `data/raw/`; su inventario reproducible se genera con `scripts/generate_manifest.py`.
- El manifiesto solo se considera válido si contiene exactamente 20 PDF con cabecera PDF válida y hashes SHA-256.
- El uso de IA generativa se audita en `docs/gestion/registro-ia.md`.
- La autorización del Gate F0 se evidencia mediante el acta en `docs/evidence/f0/acta-gate-f0.md`; una plantilla sin firmas no constituye aprobación.
