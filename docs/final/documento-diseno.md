# Política de vocabulario y embeddings

## Vocabulario y cobertura FastText

El vocabulario se obtiene de los tokens únicos de P-TEXTO (título y resumen ya normalizados),
en minúsculas y sin eliminar stopwords. No se incluyen autores ni otros metadatos. FastText
produce un vector documental de 300 dimensiones mediante el promedio de los vectores de sus
tokens. La cobertura se informa tanto para tokens únicos conocidos por el modelo como para las
ocurrencias del corpus; los tokens fuera del vocabulario explícito que pueden sintetizarse con
subpalabras se contabilizan aparte. Si un documento no contiene tokens vectorizables, su vector es
cero y la cobertura permite identificarlo.

## Truncado de all-MiniLM-L6-v2

Los textos que exceden el máximo de tokens informado por el modelo se truncan por la derecha,
conservando el prefijo en el orden original. Así se respeta la longitud de entrada entrenada por
el modelo sin introducir una selección de fragmentos dependiente del contenido. Los embeddings
resultantes se normalizan a norma L2 igual a 1. La longitud máxima efectiva y la política se
registran en `embeddings.meta.json`.

## Integridad y reproducibilidad

Antes de cargar textos o modelos, el script verifica SHA-256 de `data/corpus.json` contra
`docs/evidence/f1/corpus.sha256` o el valor explícito pasado con `--corpus-sha256`. Las salidas
incluyen los hashes de corpus, entradas y artefactos generados, además de las versiones de las
bibliotecas y la identidad/hash del modelo disponible.

## Requerimiento 1 — Matriz de similitud y correlación de rangos

Se calculan seis similitudes entre los 20 documentos del corpus. La matriz contiene los 190 pares
no ordenados distintos por algoritmo (sin diagonal), y el servicio conserva los resultados
calculados en una caché concurrente indexada por algoritmo y par de IDs.

| Algoritmo | Representación y cálculo | Valor normalizado |
|---|---|---|
| Levenshtein | Distancia de edición por tabulación sobre puntos de código Unicode, con inserción, eliminación y sustitución de costo 1. | `1 - E / max(|A|, |B|)` |
| LCS | Subsecuencia común más larga por tabulación de tokens y reconstrucción por retroceso. | `L / max(|A|, |B|)` |
| TF-IDF | Vector disperso `tf(t,d) * ln(20 / df(t))`; `N=20` se mantiene fijo para todo el corpus. La comparación es coseno. | Coseno acotado a `[0, 1]` |
| Jaccard | Intersección dividida por unión de los conjuntos de tokens; dos conjuntos vacíos se consideran idénticos. | `|A ∩ B| / |A ∪ B|` |
| FastText (IA-1) | Vector documental de 300 dimensiones, calculado como promedio de los vectores FastText de los tokens y cargado por ID. | `(coseno + 1) / 2` |
| Sentence-BERT (IA-2) | Embedding documental `all-MiniLM-L6-v2` de 384 dimensiones, unitario y cargado por ID. | `1 / (1 + distancia euclidiana)` |

Para comparar el ordenamiento producido por cada método, se convierten las 190 similitudes
normalizadas de cada algoritmo a rangos promedio en caso de empates y se calcula la correlación de
Pearson entre cada par de vectores de rangos. El resultado es una matriz simétrica de `6 × 6`;
cuando un vector de rangos es constante, la correlación no definida se representa como cero.
