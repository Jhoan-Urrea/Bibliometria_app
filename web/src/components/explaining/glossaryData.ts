export type GlossaryCategory =
  | 'General / PLN'
  | 'Programación Dinámica'
  | 'Espacio Vectorial / IA'
  | 'Clustering Jerárquico'
  | 'String Matching';

export interface GlossaryTerm {
  id: string;
  term: string;
  category: GlossaryCategory;
  definition: string;
  formulaOrNotation?: string;
}

export const GLOSSARY_DATA: GlossaryTerm[] = [
  {
    id: 'abstract',
    term: 'Abstract',
    category: 'General / PLN',
    definition:
      'Resumen textual de un artículo científico. En este proyecto se procesa junto con el título para construir la representación textual del documento.',
  },
  {
    id: 'corpus-n20',
    term: 'Corpus (N=20)',
    category: 'General / PLN',
    definition:
      'Conjunto de referencia de 20 artículos usado por los cálculos y artefactos bibliométricos actuales. El valor N es fijo en el IDF TF-IDF implementado.',
    formulaOrNotation: 'N = 20 documentos',
  },
  {
    id: 'perfil-p-texto',
    term: 'Perfil P-TEXTO',
    category: 'General / PLN',
    definition:
      'Perfil de texto normalizado derivado del título y el abstract. Se usa como entrada textual, entre otros usos, en la búsqueda exacta.',
    formulaOrNotation: 'P-TEXTO = normalizar(título + abstract)',
  },
  {
    id: 'perfil-p-tokens',
    term: 'Perfil P-TOKENS',
    category: 'General / PLN',
    definition:
      'Representación tokenizada del documento que conserva orden y repeticiones, y de la que también se obtienen frecuencias y conjuntos de tokens.',
    formulaOrNotation: 'tokens(d) = [t₁, t₂, …, tₘ]',
  },
  {
    id: 'levenshtein',
    term: 'Distancia de Levenshtein',
    category: 'Programación Dinámica',
    definition:
      'Mínimo número de inserciones, eliminaciones y sustituciones unitarias para transformar una secuencia en otra. La implementación compara puntos de código Unicode.',
    formulaOrNotation:
      'D(i,j) = min(D(i−1,j)+1, D(i,j−1)+1, D(i−1,j−1)+c)',
  },
  {
    id: 'similaridad-edicion',
    term: 'Similitud de edición normalizada',
    category: 'Programación Dinámica',
    definition:
      'Convierte la distancia de edición en un puntaje entre 0 y 1. Para dos secuencias vacías el proyecto define similitud 1.',
    formulaOrNotation: 'sim = 1 − D(m,n) / max(m,n)',
  },
  {
    id: 'lcs',
    term: 'LCS (Longest Common Subsequence)',
    category: 'Programación Dinámica',
    definition:
      'Subsecuencia común más larga: conserva el orden relativo de los elementos, pero no exige que sean contiguos. En el proyecto se calcula sobre tokens.',
    formulaOrNotation:
      'L(i,j) = L(i−1,j−1)+1 si aᵢ=bⱼ; de lo contrario max(L(i−1,j), L(i,j−1))',
  },
  {
    id: 'tfidf-coseno',
    term: 'TF-IDF + Coseno',
    category: 'Espacio Vectorial / IA',
    definition:
      'Representa cada documento con pesos por término y compara los vectores mediante coseno. TF corresponde al conteo de tokens; el IDF usa la frecuencia documental del corpus fijo de 20 artículos.',
    formulaOrNotation:
      'w(t,d) = tf(t,d) · ln(20 / df(t)); cos(u,v) = (u·v) / (‖u‖₂‖v‖₂)',
  },
  {
    id: 'jaccard',
    term: 'Coeficiente de Jaccard',
    category: 'Espacio Vectorial / IA',
    definition:
      'Mide la proporción de tokens únicos compartidos frente al total de tokens únicos presentes en ambos documentos. Si ambos conjuntos están vacíos, la implementación define el resultado como 1.',
    formulaOrNotation: 'J(A,B) = |A ∩ B| / |A ∪ B|',
  },
  {
    id: 'fasttext-ia1',
    term: 'FastText (IA-1)',
    category: 'Espacio Vectorial / IA',
    definition:
      'Representación documental de 300 dimensiones producida a partir de embeddings FastText. La similitud se basa en coseno y se lleva a [0,1]; el núcleo recibe vectores por ID y no gestiona directamente tokens OOV.',
    formulaOrNotation: 'simₙ = (cos(u,v) + 1) / 2',
  },
  {
    id: 'sentence-bert-ia2',
    term: 'Sentence-BERT (IA-2)',
    category: 'Espacio Vectorial / IA',
    definition:
      'Embedding documental denso de 384 dimensiones, validado con norma L2 unitaria. El coseno se transforma linealmente a la escala [0,1].',
    formulaOrNotation: 'simₙ = (cos(u,v) + 1) / 2',
  },
  {
    id: 'oov',
    term: 'OOV (Out of Vocabulary)',
    category: 'Espacio Vectorial / IA',
    definition:
      'Token que no aparece en el vocabulario de un modelo de embeddings. El núcleo trabaja con vectores documentales precalculados; la política OOV debe verificarse en el proceso que genera esos artefactos.',
  },
  {
    id: 'dendrograma',
    term: 'Dendrograma',
    category: 'Clustering Jerárquico',
    definition:
      'Árbol binario que representa las fusiones sucesivas del clustering aglomerativo. La altura de una fusión corresponde a la disimilitud entre los grupos que se unen.',
  },
  {
    id: 'lance-williams',
    term: 'Recurrencia de Lance-Williams',
    category: 'Clustering Jerárquico',
    definition:
      'Esquema que actualiza la distancia del nuevo grupo A∪B a otro grupo C tras fusionar A y B. Los coeficientes determinan el criterio de enlace.',
    formulaOrNotation:
      'd(A∪B,C)=αA·d(A,C)+αB·d(B,C)+β·d(A,B)+γ·|d(A,C)−d(B,C)|',
  },
  {
    id: 'single-linkage',
    term: 'Single Linkage',
    category: 'Clustering Jerárquico',
    definition:
      'Enlace simple: la distancia entre dos grupos es la menor distancia entre un elemento de cada grupo.',
    formulaOrNotation: 'd(A∪B,C) = min(d(A,C), d(B,C))',
  },
  {
    id: 'complete-linkage',
    term: 'Complete Linkage',
    category: 'Clustering Jerárquico',
    definition:
      'Enlace completo: la distancia entre dos grupos es la mayor distancia entre un elemento de cada grupo.',
    formulaOrNotation: 'd(A∪B,C) = max(d(A,C), d(B,C))',
  },
  {
    id: 'average-linkage',
    term: 'Average Linkage (UPGMA)',
    category: 'Clustering Jerárquico',
    definition:
      'Promedia las distancias entre elementos de los grupos, ponderándolas por el tamaño de cada grupo.',
    formulaOrNotation:
      'd(A∪B,C) = (|A|·d(A,C) + |B|·d(B,C)) / (|A|+|B|)',
  },
  {
    id: 'cpcc',
    term: 'CPCC (Correlación Cofenética)',
    category: 'Clustering Jerárquico',
    definition:
      'Correlación de Pearson entre las disimilitudes originales y las alturas cofenéticas del dendrograma para cada par de documentos. Para N=20 se comparan 190 pares no ordenados.',
    formulaOrNotation: 'M = N(N−1)/2; r = corr(δᵢⱼ, hᵢⱼ), i < j',
  },
  {
    id: 'silhouette',
    term: 'Coeficiente de Silueta S(K)',
    category: 'Clustering Jerárquico',
    definition:
      'Resume cohesión y separación: a(x) es la distancia media al propio grupo y b(x) la menor distancia media a otro grupo. La media de s(x) evalúa una partición con K grupos.',
    formulaOrNotation: 's(x) = (b(x) − a(x)) / max(a(x), b(x))',
  },
  {
    id: 'kmp-lps',
    term: 'KMP (Tabla LPS)',
    category: 'String Matching',
    definition:
      'Knuth-Morris-Pratt busca el patrón sin retroceder en el texto. LPS[i] guarda la longitud del prefijo propio más largo que también es sufijo del prefijo del patrón hasta i.',
    formulaOrNotation: 'LPS[i] = longest proper prefix(P[0..i]) that is also a suffix',
  },
  {
    id: 'boyer-moore',
    term: 'Boyer-Moore (Mal Carácter / Sufijo Bueno)',
    category: 'String Matching',
    definition:
      'Compara el patrón de derecha a izquierda y desplaza la ventana con las reglas de mal carácter y sufijo bueno fuerte. El desplazamiento mínimo es uno.',
    formulaOrNotation:
      'shift = max(1, j − last(T[s+j]), goodSuffix[j])',
  },
  {
    id: 'rabin-karp',
    term: 'Rabin-Karp (Rolling Hash)',
    category: 'String Matching',
    definition:
      'Compara hashes polinomiales de ventanas consecutivas. Si los hashes coinciden, verifica los caracteres para descartar colisiones.',
    formulaOrNotation:
      'Hₛ₊₁ = ((Hₛ − T[s]·βᵐ⁻¹)·β + T[s+m]) mod Q; β=257, Q=1,000,000,007',
  },
];
