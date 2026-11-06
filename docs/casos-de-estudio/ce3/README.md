# Caso de estudio 3 — Búsqueda exacta en el corpus

## Pregunta y método

- **Consulta:** `artificial intelligence` (coincidencia exacta y sensible a mayúsculas).
- **Universo:** los 20 registros de `data/corpus.json`, campo `abstractText`.
- **Preparación:** cada abstract se procesó con `TextPreprocessor.normalizeText`, sin alterar
  mayúsculas ni aplicar stopwords. Las posiciones son índices UTF-16 del P-TEXTO resultante.
- **Algoritmos:** KMP, Boyer-Moore (carácter malo y sufijo bueno fuerte) y Rabin-Karp.
- **Criterio de paridad:** igualdad exacta de las listas de posiciones por artículo.
- **Resaltado:** fragmento de hasta 40 unidades UTF-16; los límites de resaltado son relativos al
  inicio del fragmento y siguen la convención semiabierta `[inicio, fin)`.

## Resultado

Los tres algoritmos coincidieron en los 20 artículos (`coinciden = true`). Hubo 38 ocurrencias en
15 artículos.

| ID | Ocurrencias | Posiciones P-TEXTO |
|---|---:|---|
| A01 | 1 | 16 |
| A02 | 1 | 69 |
| A03 | 3 | 29, 414, 1971 |
| A04 | 0 | — |
| A05 | 1 | 82 |
| A06 | 2 | 15926, 41433 |
| A07 | 2 | 22, 1487 |
| A08 | 2 | 28, 1122 |
| A09 | 3 | 2743, 49927, 56331 |
| A10 | 2 | 67, 1844 |
| A11 | 2 | 3, 1453 |
| A12 | 1 | 26 |
| A13 | 0 | — |
| A14 | 0 | — |
| A15 | 1 | 79 |
| A16 | 0 | — |
| A17 | 1 | 30 |
| A18 | 0 | — |
| A19 | 1 | 90 |
| A20 | 15 | 149, 1456, 5981, 8038, 48293, 48376, 52527, 53438, 53780, 55768, 58217, 59043, 64531, 66260, 68268 |

## Fragmento resaltado

Primera ocurrencia de A01:

```text
: Since [artificial intelligence] (AI) is 
```

- Inicio del fragmento en P-TEXTO: `8`.
- Intervalo resaltado relativo: `[8, 31)`.
- Longitud del fragmento: `40`.

## Traza de algoritmos

[`traza.json`](./traza.json) contiene la tabla LPS de KMP, las tablas preprocesadas de
Boyer-Moore y el hash del patrón y de cada ventana de Rabin-Karp para el fragmento anterior.
Rabin-Karp encontró el hash de la consulta en la ventana relativa `8`, que se verificó carácter
a carácter.

## Alcance y reproducibilidad

El servicio de `core` recibe textos P-TEXTO ya preparados; la lectura JSON y la preparación del
corpus pertenecen a la capa de ingesta. El caso se ejecutó sobre los 20 abstracts presentes en
`data/corpus.json`, y el mismo patrón produjo las posiciones indicadas con los tres matchers.
La consulta es intencionalmente exacta y sensible a mayúsculas; no representa una búsqueda
semántica ni una búsqueda sin distinción de mayúsculas.
