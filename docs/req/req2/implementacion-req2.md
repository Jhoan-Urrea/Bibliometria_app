# Requerimiento 2 — Indexación, recuperación y agrupamiento jerárquico

## Propósito y límites

Este documento describe la implementación del Requerimiento 2 en los módulos Java 21. Define
fórmulas, contratos, orden de cálculo y puntos de integración; no presenta resultados del corpus.
El informe experimental, las fusiones reales y su interpretación temática están separados en
[Caso de Estudio 2](../../casos-de-estudio/ce2/README.md).

El módulo `core` mantiene cero dependencias productivas externas (RNF-02). Representa las matrices,
enlaces, fusiones y métricas usando el JDK. La serialización JSON y la lectura del corpus pertenecen
a `tools`, con Jackson; el frontend puede consumir el árbol D3 sin acoplar la librería JSON al
dominio.

## 1. Vectores y distancias

Para documentos procesados \(d_i,d_j\), `TfIdfSimilarity` produce los vectores ponderados
\(u_i,u_j\). La matriz de distancias almacena:
\[
\delta(i,j)=1-\operatorname{sim}_{cos}(u_i,u_j),\qquad
\operatorname{sim}_{cos}(u_i,u_j)=
\frac{u_i\cdot u_j}{\lVert u_i\rVert_2\lVert u_j\rVert_2}.
\]
`DistanceMatrix` calcula una vez cada par, copia el resultado simétricamente, establece diagonal
exactamente cero y valida finitud y rango \([0,1]\). Los IDs y las matrices expuestas son
inmutables o defensivamente copiadas. El constructor de corpus respeta la referencia global del
modelo; `fromDistances` permite consumir matrices precomputadas validadas.

## 2. Criterios de enlace y árbol

`Linkage.update` implementa Lance–Williams:
\[
d(A\cup B,C)=\alpha_A d(A,C)+\alpha_B d(B,C)+\beta d(A,B)
+\gamma|d(A,C)-d(B,C)|.
\]

| Implementación | Coeficientes / significado |
|---|---|
| `SingleLinkage` | \(\alpha_A=\alpha_B=1/2,\beta=0,\gamma=-1/2\), equivalente al mínimo |
| `CompleteLinkage` | \(\alpha_A=\alpha_B=1/2,\beta=0,\gamma=1/2\), equivalente al máximo |
| `AverageLinkage` | \(\alpha_A=|A|/(|A|+|B|)\), \(\alpha_B=|B|/(|A|+|B|)\), \(\beta=\gamma=0\) |

`AgglomerativeClustering.run` selecciona la distancia activa mínima y emite \(N-1\) instancias
inmutables de `Merge(leftChild,rightChild,height,size)`. Las hojas reciben índices canónicos
`0..N-1`, las fusiones nuevas `N..2N-2`, y los empates se ordenan por pareja de índices activa. La
implementación actual recorre los pares activos en cada iteración: tiempo \(O(N^3)\) y espacio
\(O(N^2)\).

`Dendrogram.from` valida que las fusiones describan un árbol binario completo, con hijos activos,
tamaños coherentes y alturas no decrecientes (tolerancia \(10^{-12}\)). El nodo interno representa
la fusión; una hoja conserva ID e índice original. La altura del ancestro común más bajo define la
distancia cofenética:
\[
h(i,j)=\operatorname{height}(\operatorname{LCA}(i,j)).
\]
`CopheneticMatrix` asigna la altura de cada LCA a sus pares de hojas, en ambas direcciones. Hay
\(\binom N2\) asignaciones, por tanto tiempo \(O(N^2)\), y la matriz ocupa \(O(N^2)\).

## 3. Métricas de validación y corte

### CPCC

`CopheneticCorrelation.compute` calcula Pearson entre las \(M=N(N-1)/2\) distancias originales y
cofenéticas:
\[
r_c=\frac{\sum_{i<j}(\delta_{ij}-\bar\delta)(h_{ij}-\bar h)}
{\sqrt{\sum_{i<j}(\delta_{ij}-\bar\delta)^2
\cdot\sum_{i<j}(h_{ij}-\bar h)^2}}.
\]
Dos pasadas sobre pares calculan medias y productos centrados, con tiempo \(O(N^2)\) y espacio
auxiliar \(O(1)\). Si cualquiera de las varianzas es cero, el resultado definido es `0`.
La matriz y el dendrograma deben compartir exactamente los IDs canónicos.

### Corte \(K\)

`ClusterCut.cut(merges,K,N)` conserva las primeras \(N-K\) fusiones y omite las últimas \(K-1\).
Union-Find une las hojas que pertenecen a los componentes retenidos. Las etiquetas consecutivas
`0..K-1` se asignan por índice canónico mínimo de cada grupo. El historial de entrada debe ser
completo y tener hijos activos con tamaños coherentes.

### Silueta

Para cada objeto \(x\), `Silhouette` define cohesión \(a(x)\) como distancia media a su propio
clúster (excluyendo \(x\)) y separación \(b(x)\) como la menor distancia media a otro clúster:
\[
s(x)=\frac{b(x)-a(x)}{\max(a(x),b(x))},\qquad
S(K)=\frac1N\sum_{x=1}^{N}s(x).
\]
Un clúster unitario o denominador simultáneamente cero da \(s(x)=0\). El componente evalúa de 2 a
10 etiquetas de clúster. La implementación acumula distancias por documento y etiqueta; tiempo
\(O(N^2+NK)\), espacio \(O(NK)\), con \(K\leq10\).

## 4. Evaluación y regla de decisión

`ClusteringEvaluationService.evaluate` ejecuta las tres jerarquías Single, Complete y Average,
calcula CPCC y \(S(K)\) para cada \(K=2,\ldots,\min(10,N)\), y obtiene el K con mayor silueta por
enlace. Si puntuaciones dentro de un mismo enlace difieren en no más de \(10^{-9}\), se elige el K
menor.

La comparación entre enlaces sigue la regla suministrada como ADR-013: mayor mejor \(S(K)\);
cuando la diferencia absoluta es \(\leq10^{-9}\), mayor CPCC; si ambos empatan, orden fijo Single,
Complete, Average. El servicio devuelve métricas, mapa de \(S(K)\), fusiones, dendrograma y
ganador en records inmutables.

**Pendiente de control documental:** ADR-013 no está disponible en la carpeta `docs/adr` del
repositorio revisado. La tolerancia y el último desempate deben cotejarse con el ADR aprobado antes
de tratar esta regla como fuente normativa.

## 5. Exportación y contrato de interfaz

`ClusteringJsonExporter` y `ClusteringExportCommand` viven en `tools`. El comando recibe un corpus
JSON verificado, transforma título y abstract mediante `TextPreprocessor`, construye
`DistanceMatrix`, evalúa enlaces y escribe el archivo UTF-8 solicitado. Valida `verified:true` y
campos de entrada obligatorios; los errores de lectura, datos ausentes o escritura se propagan
explícitamente.

La raíz del JSON exportado incluye `formatVersion`, `decisionRule`, selección y `candidates`. Cada
enlace contiene CPCC, mejor K/silueta, \(S(K)\), lista de fusiones, orden canónico de hojas y árbol
recursivo:

- Nodo interno: `id`, `height`, `size`, `children`.
- Hoja: `id`, `documentId`, `name`, `title`, `authors`, `height`, `size`, `cluster`.

Esta estructura es consumible como jerarquía por `d3.hierarchy`; las unidades de `height` son
distancias TF-IDF. El orden de hijos se conserva determinista por índice canónico mínimo. La
exportación no afirma que la estructura temática esté validada ni que el frontend de Fase 5 ya
esté integrado.

## 6. Pruebas y verificaciones

Las pruebas de `core` cubren propiedades de simetría/ultrametricidad, fusión determinista, CPCC,
cortes, silueta, empates y casos límite. Las pruebas de `tools` parsean el JSON emitido, inspeccionan
nodos D3 y metadatos, y ejercitan el comando con un corpus verificado de 20 artículos.

Comandos de validación:

```sh
mvn -pl core -Dtest='*Clustering*Test,*Dendrogram*Test,*Silhouette*Test,*Cophenetic*Test' test
mvn -pl tools -am -Dtest=ClusteringJsonExporterTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn clean verify
```

## 7. Separación del Caso de Estudio 2 (DF §10.2)

Este documento es la especificación de implementación: define clases, fórmulas, invariantes,
complejidad, interfaces y comandos. No contiene mapas de calor del corpus, tablas de las 19
fusiones, resultados numéricos empíricos ni interpretaciones temáticas. Esos entregables pertenecen
exclusivamente a [docs/casos-de-estudio/ce2/README.md](../../casos-de-estudio/ce2/README.md).

**Cotejo pendiente antes de cierre normativo:** verificar las ecuaciones y convenciones contra el
DF §§6 y 8.3; sustituir/confirmar el fixture manual con el ejemplo literal de §9; y confirmar la
regla y tolerancia con ADR-013 y la separación requerida en §10.2. El DF no está versionado en esta
workspace, por lo que este borrador no certifica por sí solo esa trazabilidad.
