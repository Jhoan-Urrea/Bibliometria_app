# Borrador — Caso de Estudio 2: Agrupamiento jerárquico del corpus de IA en educación

**Estado:** borrador técnico reproducible; pendiente de cotejo documental con el DF §§6.2, 6.4 y 9,
la sección §10.2 y ADR-013.  
**Corpus evaluado:** 20 registros verificados `A01`–`A20`.  
**SHA-256 de `data/corpus.json`:** `e8210ea7ed2e39e7f67f6f636768a3af9e728e6ef839a1c230b9be2be7eaea41`.  
**Método:** TF-IDF sobre título y abstract procesados con el `TextPreprocessor` de `core`; distancia
coseno \(\delta=1-\operatorname{sim}_{cos}\); enlace Single, Complete y Average.

> **Nota de fidelidad a las fuentes.** En el repositorio de trabajo no se encuentra el Documento de
> Formulación ni ADR-013 como artefactos independientes. Por ello, las tablas de corpus abajo son
> resultados reproducidos desde los archivos actuales, y la regla de decisión se documenta como la
> regla suministrada para este trabajo (mayor \(S(K)\); si la diferencia es \(\leq10^{-9}\), mayor
> CPCC). El fixture manual de cuatro elementos retoma el fixture de desarrollo ya usado en Req 2;
> no debe citarse como transcripción del ejemplo numérico del DF §9 hasta cotejarlo con la fuente.

## 1. Datos, método y límites

La entrada es `data/corpus.json`, sincronizada con
`api/src/main/resources/data/corpus.json`; ambos archivos tienen el SHA-256 indicado arriba. Los
20 artículos están marcados `verified:true`. Para la matriz se procesa la concatenación de título
y resumen, según el contrato vigente de `TextPreprocessor`; autores y otros metadatos no participan
en el vector TF-IDF. Los IDs se ordenan canónicamente de `A01` a `A20`.

Los valores se generaron ejecutando `ClusteringExportCommand` y se redondean a seis decimales en
las tablas de fusiones y evaluación. Las etiquetas internas `M01`–`M19` identifican la fusión
creada por el paso correspondiente dentro de cada algoritmo; `M01` es el nodo interno 20 (los
índices hoja empiezan en 0). En las tablas, las hojas conservan su identificador de artículo.

La matriz de distancia coseno tiene 20 × 20 elementos. El mapa siguiente es una **visualización
conceptual cuantizada de la matriz real**, no una matriz de similitud ni una escala temática:

- `·`: diagonal (distancia cero)
- `░`: \(0 < \delta < 0.85\)
- `▒`: \(0.85 \leq \delta < 0.90\)
- `▓`: \(0.90 \leq \delta < 0.95\)
- `█`: \(0.95 \leq \delta \leq 1.00\)

Las columnas y filas están en orden `A01 … A20`; cada carácter representa una distancia desde la
fila a la columna en ese mismo orden.

```text
       01 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16 17 18 19 20
A01    ·  █  ▒  █  █  █  ▓  █  █  █  █  ▓  ▓  █  █  █  █  █  █  █
A02    █  ·  █  █  █  █  █  █  █  █  █  ▓  ▓  █  ▓  ▓  █  █  ▓  █
A03    ▒  █  ·  ▓  █  █  ▓  ▓  ▓  █  █  █  ▓  █  █  █  █  █  █  █
A04    █  █  ▓  ·  █  █  ▓  █  █  █  █  ▓  ░  █  █  ▓  █  █  █  ▓
A05    █  █  █  █  ·  █  █  █  █  █  █  █  ▓  █  █  █  █  █  █  █
A06    █  █  █  █  █  ·  ▓  █  █  █  █  █  █  █  █  █  █  █  █  ▓
A07    ▓  █  ▓  ▓  █  ▓  ·  ▓  █  ▓  █  ▓  ▓  ▓  █  █  █  █  ▓  █
A08    █  █  ▓  █  █  █  ▓  ·  █  ▓  ▓  █  █  █  █  █  █  █  █  █
A09    █  █  ▓  █  █  █  █  █  ·  █  ▓  █  ▓  █  █  ▓  █  █  █  ▓
A10    █  █  █  █  █  █  ▓  ▓  █  ·  ▓  ▓  █  █  █  █  █  █  █  ▓
A11    █  █  █  █  █  █  █  ▓  ▓  ▓  ·  ▓  █  █  █  █  █  █  █  █
A12    ▓  ▓  █  ▓  █  █  ▓  █  █  ▓  ▓  ·  ▒  █  █  ▓  ▒  ▓  ▓  ░
A13    ▓  ▓  ▓  ░  ▓  █  ▓  █  ▓  █  █  ▒  ·  █  █  ▓  ▓  ▓  █  ▒
A14    █  █  █  █  █  █  ▓  █  █  █  █  █  █  ·  █  █  █  █  █  █
A15    █  ▓  █  █  █  █  █  █  █  █  █  █  █  █  ·  █  █  █  █  █
A16    █  ▓  █  ▓  █  █  █  █  ▓  █  █  ▓  ▓  █  █  ·  ▒  ▓  ▓  ▒
A17    █  █  █  █  █  █  █  █  █  █  █  ▒  ▓  █  █  ▒  ·  ▓  █  ▒
A18    █  █  █  █  █  █  █  █  █  █  █  ▓  ▓  █  █  ▓  ▓  ·  █  ▓
A19    █  ▓  █  █  █  █  ▓  █  █  █  █  ▓  █  █  █  ▓  █  █  ·  █
A20    █  █  █  ▓  █  ▓  █  █  ▓  ▓  █  ░  ▒  █  █  ▒  ▒  ▓  █  ·
```

El mapa está dominado por distancias altas (\(\geq0.95\), `█`), con pocas celdas bajo `0.90`.
Esto anticipa poca separación y cohesión entre grupos en el espacio actual; la matriz no muestra
por sí sola tópicos discretos bien separados.

## 2. Evaluación y aplicación de la regla de decisión

Se evaluaron \(S(K)\) para cada \(K\in\{2,\ldots,10\}\) en cada jerarquía. El CPCC se calcula
sobre los 190 pares no ordenados.

| Enlace | CPCC | \(K\) de mejor \(S(K)\) | Mejor \(S(K)\) |
|---|---:|---:|---:|
| Single | 0.736928 | 2 | 0.015363 |
| Complete | 0.708266 | 10 | 0.032465 |
| Average (UPGMA) | **0.808002** | 8 | 0.031702 |

| \(K\) | Single \(S(K)\) | Complete \(S(K)\) | Average \(S(K)\) |
|---:|---:|---:|---:|
| 2 | 0.015363 | 0.016477 | 0.015363 |
| 3 | 0.008293 | 0.017965 | 0.018611 |
| 4 | 0.005858 | 0.018675 | 0.014440 |
| 5 | 0.004109 | 0.019491 | 0.011360 |
| 6 | -0.000832 | 0.022604 | 0.013199 |
| 7 | -0.003214 | 0.024875 | 0.020374 |
| 8 | -0.003551 | 0.027454 | 0.031702 |
| 9 | 0.003514 | 0.031842 | 0.030311 |
| 10 | 0.012458 | **0.032465** | 0.029491 |

**Decisión conforme a la regla indicada:** gana **Complete con \(K=10\)**, porque tiene el mayor
mejor \(S(K)\). La diferencia con Average es \(0.032465183-0.031701949\approx0.000763234\),
mayor que \(10^{-9}\); por tanto, el desempate por CPCC no se activa. Aunque Average tiene el CPCC
más alto, ese indicador solo decide cuando las siluetas están dentro de la tolerancia.

**Interpretación cautelosa:** la mejor silueta del ganador es apenas `0.032465`, muy próxima a
cero, y su corte tiene varios clústeres unitarios. La regla produce un ganador comparativo, no
evidencia de que el particionado sea fuerte ni confirmación estadística de diez temas diferenciados.
Se recomienda reportarlo como resultado provisional y contrastar el corte con expertos y análisis
temático explícito.

## 3. Efecto cadena: comparación de los tres enlaces

### Single

El corte seleccionado \(K=2\) deja un grupo de 19 documentos y un singleton (`A14`). En la traza,
después de las primeras fusiones locales, los grupos se van incorporando sucesivamente a un
componente dominante (por ejemplo, fusiones `M09`–`M19`). Este patrón es consistente con el efecto
cadena de Single: una conexión local puede bastar para anexar un clúster completo, aunque el resto
de sus documentos no estén próximos a todos los elementos del grupo. Su \(S(K)=0.015363\) en
\(K=2\) y sus puntuaciones negativas entre \(K=6\) y \(K=8\) advierten que estos cortes tienen una
separación pobre.

### Complete

El enlace Complete exige que el miembro más distante entre dos grupos también quede controlado;
evita las incorporaciones por una sola conexión cercana. En este corpus el mejor corte evaluado es
el límite máximo \(K=10\), con varios singletons y una mejor silueta `0.032465`. Se observa menos
cadena que en Single, a cambio de una partición muy fina. El valor bajo de silueta no permite
interpretar cada singleton como tema consolidado.

### Average (UPGMA)

Average promedia las distancias entre los miembros de los grupos. Su \(K=8\) produce grupos
intermedios, tiene la mayor correlación cofenética (`0.808002`) y una silueta `0.031702`, apenas
por debajo del ganador. Como la brecha de silueta supera \(10^{-9}\), el CPCC más alto no cambia la
decisión indicada. En términos descriptivos, Average es una alternativa de compromiso razonable
para discusión, pero no la selección formal bajo la regla provista.

## 4. Interpretación temática provisional del corte ganador

Los siguientes nombres son **rótulos editoriales por títulos y resúmenes**, no etiquetas
producidas por un modelo de tópicos. Se deben validar mediante revisión manual del contenido
completo antes de presentarlos como hallazgos.

| Clúster Complete, \(K=10\) | Artículos | Lectura temática provisional |
|---:|---|---|
| 0 | A01, A03 | Alfabetización en IA y evaluación con IA generativa en educación superior |
| 1 | A02, A19 | Simulación para aprendizaje y técnicas de IA para aprendizaje colaborativo |
| 2 | A04, A13 | Revisiones de IA en educación superior: ética, colaboración y aplicaciones institucionales |
| 3 | A05 | IA para educación, evaluación y *adversity quotient* |
| 4 | A06 | Marcos de competencia en IA para docentes |
| 5 | A07, A08, A10 | IA generativa en práctica docente, rediseño de evaluación y contextos educativos |
| 6 | A09, A11 | Evaluación educativa/STEM basada en IA e impacto de ChatGPT |
| 7 | A12, A16, A17, A18, A20 | Agenda y evolución de IA en educación: brechas, ecosistema y analítica centrada en personas |
| 8 | A14 | Mitos, errores y preconcepciones sobre IA |
| 9 | A15 | IA para apoyar resolución de problemas complejos |

Las interpretaciones se basan en proximidad TF-IDF y en lectura de títulos; por ejemplo, el clúster
7 es amplio y heterogéneo, por lo que su etiqueta deliberadamente agrupa una agenda general de
investigación y no implica un tópico único. Los miembros y títulos completos se encuentran en
`data/corpus.json`.

## 5. Trazas completas de fusión

Las alturas son distancias \(\delta\), no similitudes. Cada fusión `Mnn` crea un clúster nuevo que
puede aparecer como hijo en fusiones posteriores.

### Single Linkage — 19 fusiones

| Paso | Hijo izquierdo | Hijo derecho | Altura | Tamaño |
|---:|---|---|---:|---:|
| 1 | A12 | A20 | 0.827260 | 2 |
| 2 | A04 | A13 | 0.842210 | 2 |
| 3 | A17 | M01 | 0.856438 | 3 |
| 4 | A01 | A03 | 0.858664 | 2 |
| 5 | A16 | M03 | 0.874794 | 4 |
| 6 | M02 | M05 | 0.886940 | 6 |
| 7 | A02 | A19 | 0.910517 | 2 |
| 8 | A07 | A08 | 0.911351 | 2 |
| 9 | M04 | M06 | 0.913759 | 8 |
| 10 | A10 | M08 | 0.916431 | 3 |
| 11 | M09 | M10 | 0.919377 | 11 |
| 12 | M07 | M11 | 0.919598 | 13 |
| 13 | A09 | M12 | 0.920777 | 14 |
| 14 | A18 | M13 | 0.928416 | 15 |
| 15 | A11 | M14 | 0.928869 | 16 |
| 16 | A15 | M15 | 0.932239 | 17 |
| 17 | A06 | M16 | 0.932801 | 18 |
| 18 | A05 | M17 | 0.935455 | 19 |
| 19 | A14 | M18 | 0.949421 | 20 |

### Complete Linkage — 19 fusiones

| Paso | Hijo izquierdo | Hijo derecho | Altura | Tamaño |
|---:|---|---|---:|---:|
| 1 | A12 | A20 | 0.827260 | 2 |
| 2 | A04 | A13 | 0.842210 | 2 |
| 3 | A01 | A03 | 0.858664 | 2 |
| 4 | A16 | A17 | 0.874794 | 2 |
| 5 | M01 | M04 | 0.904089 | 4 |
| 6 | A02 | A19 | 0.910517 | 2 |
| 7 | A07 | A08 | 0.911351 | 2 |
| 8 | A09 | A11 | 0.933965 | 2 |
| 9 | A18 | M05 | 0.943955 | 5 |
| 10 | A10 | M07 | 0.947001 | 3 |
| 11 | A15 | M02 | 0.962035 | 3 |
| 12 | A05 | M03 | 0.966504 | 3 |
| 13 | M06 | M11 | 0.970202 | 5 |
| 14 | A14 | M12 | 0.972700 | 4 |
| 15 | M08 | M10 | 0.976577 | 5 |
| 16 | A06 | M14 | 0.982278 | 5 |
| 17 | M09 | M13 | 0.988303 | 10 |
| 18 | M15 | M16 | 0.989956 | 10 |
| 19 | M17 | M18 | 0.994627 | 20 |

### Average Linkage / UPGMA — 19 fusiones

| Paso | Hijo izquierdo | Hijo derecho | Altura | Tamaño |
|---:|---|---|---:|---:|
| 1 | A12 | A20 | 0.827260 | 2 |
| 2 | A04 | A13 | 0.842210 | 2 |
| 3 | A01 | A03 | 0.858664 | 2 |
| 4 | A16 | A17 | 0.874794 | 2 |
| 5 | M01 | M04 | 0.885138 | 4 |
| 6 | A02 | A19 | 0.910517 | 2 |
| 7 | A07 | A08 | 0.911351 | 2 |
| 8 | M02 | M05 | 0.922427 | 6 |
| 9 | A10 | M07 | 0.931716 | 3 |
| 10 | A09 | A11 | 0.933965 | 2 |
| 11 | A18 | M08 | 0.940673 | 7 |
| 12 | A15 | M06 | 0.950060 | 3 |
| 13 | M03 | M11 | 0.956393 | 9 |
| 14 | M09 | M13 | 0.958393 | 12 |
| 15 | M10 | M14 | 0.959884 | 14 |
| 16 | A05 | M15 | 0.965501 | 15 |
| 17 | A06 | M16 | 0.967972 | 16 |
| 18 | M12 | M17 | 0.969481 | 19 |
| 19 | A14 | M18 | 0.971066 | 20 |

## 6. Ejemplo manual de cuatro elementos

Este ejemplo conserva el fixture autocontenido del desarrollo de Req 2; **debe cotejarse con el
ejemplo original del DF §9 antes de atribuirle sus valores**. Se usan cuatro objetos \(A,B,C,D\) y
la matriz simétrica:

| \(\delta\) | A | B | C | D |
|---|---:|---:|---:|---:|
| A | 0.0 | 0.1 | 0.4 | 0.7 |
| B | 0.1 | 0.0 | 0.5 | 0.8 |
| C | 0.4 | 0.5 | 0.0 | 0.3 |
| D | 0.7 | 0.8 | 0.3 | 0.0 |

Las dos primeras fusiones, comunes a los tres criterios, son \(A+B\) a altura \(0.1\), y \(C+D\)
a altura \(0.3\). En la última fusión:

- **Single:** \(\min(0.4,0.7,0.5,0.8)=0.4\).
- **Complete:** \(\max(0.4,0.7,0.5,0.8)=0.8\).
- **Average:** \((0.4+0.7+0.5+0.8)/4=0.6\).

En \(K=2\), el corte es \(\{A,B\}\mid\{C,D\}\) para los tres. Las siluetas individuales son
\(s_A=0.818182\), \(s_B=0.846154\), \(s_C=0.333333\), \(s_D=0.600000\), por lo que
\(S(2)=0.649417\). Los CPCC del fixture son Single `0.810163`, Complete `0.832827` y Average
`0.836660`. Estos resultados ejemplifican que el mismo corte puede tener la misma silueta y, sin
embargo, cambiar su ajuste cofenético entre enlaces.

## 7. Reproducción

Desde la raíz del repositorio:

```sh
mvn -pl tools -am package
mvn -q -pl tools dependency:build-classpath \
  -Dmdep.outputFile=/tmp/bibliometria-tools.classpath
java -cp "tools/target/classes:core/target/classes:$(cat /tmp/bibliometria-tools.classpath)" \
  com.bibliometria.tools.clustering.ClusteringExportCommand \
  data/corpus.json /tmp/ce2-clustering.json
mvn clean verify
```

El borrador se puede cotejar con el JSON generado en `/tmp/ce2-clustering.json`; ese archivo es un
resultado de ejecución y no se versiona aquí.
