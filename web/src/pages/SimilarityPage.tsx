import { useEffect, useRef, useState, type FormEvent } from 'react';
import { api, type Article, type SimilarityRequest, type SimilarityResponse } from '../api/client';
import { errorMessage } from '../api/errorMessage';
import { Alert } from '../components/Alert';
import { Button } from '../components/Button';
import {
  MathExplainerCard,
  type MathStep,
} from '../components/explaining/MathExplainerCard';
import { Select } from '../components/Select';
import { SimilarityComparisonTable } from '../components/similarity/SimilarityComparisonTable';

const algorithmOptions = [
  { value: 'levenshtein', label: 'Levenshtein' },
  { value: 'lcs', label: 'Subsecuencia común más larga (LCS)' },
  { value: 'tfidf', label: 'TF-IDF + Coseno' },
  { value: 'jaccard', label: 'Jaccard' },
  { value: 'fasttext', label: 'FastText (IA-1)' },
  { value: 'sentence-bert', label: 'Sentence-BERT (IA-2)' },
] as const;

function isSimilarityAlgorithm(value: string): value is SimilarityRequest['algorithm'] {
  return algorithmOptions.some((option) => option.value === value);
}

const algorithmExplanations = [
  {
    title: 'Levenshtein',
    category: 'Programación dinámica',
    purpose: 'Mide el costo mínimo de transformar un texto normalizado en otro.',
    formula: (
      <>
        E(i,j) = min(E(i−1,j)+1, E(i,j−1)+1, E(i−1,j−1)+c)
        <br />
        Sim_edit = 1 − E(|A|,|B|) / max(|A|,|B|)
      </>
    ),
    notation: [
      { symbol: 'E(i,j)', meaning: 'Costo mínimo para transformar los primeros i y j puntos de código.' },
      { symbol: 'c', meaning: '0 si los puntos de código coinciden; 1 si se sustituyen.' },
      { symbol: '|A|, |B|', meaning: 'Longitudes en puntos de código Unicode de los textos P-TEXTO.' },
    ],
    interpretation: 'El valor normalizado se expresa en [0,1]; 1 indica textos idénticos.',
    preprocessingProfile: 'P-TEXTO' as const,
    derivationSteps: [
      {
        stepNumber: 1,
        label: 'Distancia de edición cruda',
        formula: 'E = E(|A|, |B|)',
        explanation: 'La programación dinámica obtiene el mínimo de inserciones, borrados y sustituciones de costo unitario.',
      },
      {
        stepNumber: 2,
        label: 'Longitud de referencia',
        formula: 'L = max(|A|, |B|)',
        explanation: 'Se toma la longitud mayor de los textos P-TEXTO, medida en puntos de código Unicode.',
      },
      {
        stepNumber: 3,
        label: 'Proporción de desalineación',
        formula: 'r = E / L',
        explanation: 'La distancia cruda se expresa como fracción de la longitud mayor.',
      },
      {
        stepNumber: 4,
        label: 'Normalización final',
        formula: 'Sim_edit = 1 − E / max(|A|, |B|)',
        explanation: 'La API devuelve el complemento de la proporción de ediciones; el resultado pertenece a [0,1].',
      },
    ] satisfies MathStep[],
  },
  {
    title: 'LCS (Longest Common Subsequence)',
    category: 'Programación dinámica',
    purpose: 'Compara el orden relativo de tokens compartidos, sin exigir que sean contiguos.',
    formula: (
      <>
        L(i,j) = i=0 o j=0 ? 0 : Tok(A)i = Tok(B)j
        <br />
        ? L(i−1,j−1)+1 : max(L(i−1,j), L(i,j−1))
        <br />
        Sim_LCS = L(|A|,|B|) / max(|A|,|B|)
      </>
    ),
    notation: [
      { symbol: 'Tok(d)', meaning: 'Secuencia de tokens del documento tras P-TOKENS.' },
      { symbol: 'L(i,j)', meaning: 'Longitud de la subsecuencia común más larga de los prefijos.' },
      { symbol: '|A|, |B|', meaning: 'Cantidad de tokens de cada secuencia.' },
    ],
    interpretation: 'El resultado normalizado se expresa en [0,1] y refleja coincidencia de secuencia.',
    preprocessingProfile: 'P-TOKENS' as const,
    derivationSteps: [
      {
        stepNumber: 1,
        label: 'Subsecuencia coincidente',
        formula: 'L = LCS(Tok(A), Tok(B))',
        explanation: 'Se cuenta la subsecuencia común más larga conservando el orden de los tokens, aunque no sean contiguos.',
      },
      {
        stepNumber: 2,
        label: 'Cobertura máxima en tokens',
        formula: 'M = max(|Tok(A)|, |Tok(B)|)',
        explanation: 'El denominador es la cantidad de tokens de la secuencia más larga después de P-TOKENS.',
      },
      {
        stepNumber: 3,
        label: 'Normalización',
        formula: 'Sim_LCS = L / M',
        explanation: 'La longitud cruda de la subsecuencia se divide por la longitud mayor para obtener un valor en [0,1].',
      },
    ] satisfies MathStep[],
  },
  {
    title: 'TF-IDF + Coseno',
    category: 'Espacio vectorial',
    purpose: 'Pondera términos por su frecuencia en cada artículo y su rareza en el corpus de referencia.',
    formula: (
      <>
        w(t,d) = tf(t,d) · ln(20 / df(t))
        <br />
        cos(u,v) = (u · v) / (||u||₂ ||v||₂)
      </>
    ),
    notation: [
      { symbol: 'tf(t,d)', meaning: 'Frecuencia del término t en el documento d.' },
      { symbol: 'df(t)', meaning: 'Número de documentos del corpus de 20 que contienen t.' },
      { symbol: 'u, v', meaning: 'Vectores dispersos TF-IDF de los dos documentos.' },
    ],
    interpretation: 'El coseno compara orientación de vectores. La API devuelve ese valor crudo y una versión normalizada.',
    preprocessingProfile: 'P-TOKENS' as const,
    caveat: 'En esta implementación los pesos TF-IDF son no negativos y el coseno normalizado se acota a [0,1]. No se aplica arccos.',
    derivationSteps: [
      {
        stepNumber: 1,
        label: 'Ponderación de términos',
        formula: 'w(t,d) = tf(t,d) · ln(20 / df(t))',
        explanation: 'La frecuencia documental se pondera con IDF calculado sobre el corpus fijo de 20 documentos.',
      },
      {
        stepNumber: 2,
        label: 'Producto escalar',
        formula: 'dot = u · v = Σₜ uₜvₜ',
        explanation: 'Se suman los productos de pesos correspondientes a términos compartidos por ambos vectores dispersos.',
      },
      {
        stepNumber: 3,
        label: 'Normalización por normas',
        formula: 'cos = dot / (||u||₂ · ||v||₂)',
        explanation: 'El producto escalar se divide entre las normas euclídeas para medir alineación angular.',
      },
      {
        stepNumber: 4,
        label: 'Puntaje comparable',
        formula: 'normalizedScore = max(0, min(1, cos))',
        explanation: 'El backend conserva el coseno como rawScore y lo limita a [0,1] como normalizedScore.',
      },
    ] satisfies MathStep[],
  },
  {
    title: 'Coeficiente de Jaccard',
    category: 'Teoría de conjuntos',
    purpose: 'Compara la proporción de tokens únicos compartidos frente a todos los tokens únicos observados.',
    formula: <>J(A,B) = |Set(A) ∩ Set(B)| / |Set(A) ∪ Set(B)|</>,
    notation: [
      { symbol: 'Set(d)', meaning: 'Conjunto de tokens únicos del documento tras P-TOKENS.' },
      { symbol: '∩', meaning: 'Intersección: tokens presentes en ambos conjuntos.' },
      { symbol: '∪', meaning: 'Unión: tokens presentes en al menos uno de los conjuntos.' },
    ],
    interpretation: 'La similitud está en [0,1]; 1 significa conjuntos idénticos.',
    preprocessingProfile: 'P-TOKENS' as const,
    derivationSteps: [
      {
        stepNumber: 1,
        label: 'Intersección',
        formula: 'I = |Set(A) ∩ Set(B)|',
        explanation: 'Cuenta tokens distintos presentes en ambos conjuntos P-TOKENS.',
      },
      {
        stepNumber: 2,
        label: 'Unión',
        formula: 'U = |Set(A) ∪ Set(B)|',
        explanation: 'Cuenta tokens distintos presentes en al menos uno de los documentos.',
      },
      {
        stepNumber: 3,
        label: 'Razón de solapamiento',
        formula: 'J(A,B) = I / U',
        explanation: 'El rawScore ya es el coeficiente Jaccard, no el conteo I; ambos conjuntos vacíos se definen como similitud 1.',
      },
    ] satisfies MathStep[],
  },
  {
    title: 'FastText (IA-1)',
    category: 'Semántica IA',
    purpose: 'Compara vectores documentales construidos como promedio de embeddings FastText de sus tokens.',
    formula: (
      <>
        v(d) = promedio de vectores FastText(t), t ∈ Tok(d)
        <br />
        Sim = (cos(v(A),v(B)) + 1) / 2
      </>
    ),
    notation: [
      { symbol: 'v(d)', meaning: 'Vector documental IA-1 de 300 dimensiones.' },
      { symbol: 'cos', meaning: 'Similitud coseno entre los vectores documentales.' },
      { symbol: 'Tok(d)', meaning: 'Tokens sobre los que se generó previamente el vector.' },
    ],
    interpretation: 'El coseno crudo se transforma linealmente a la escala normalizada [0,1].',
    preprocessingProfile: 'P-TOKENS' as const,
    caveat: 'Los vectores se cargan precalculados por artículo. Si un vector falta, la API reporta un error para este algoritmo.',
    derivationSteps: [
      {
        stepNumber: 1,
        label: 'Representación FastText',
        formula: 'v(d) = promedio de embeddings de sus tokens, v(d) ∈ R³⁰⁰',
        explanation: 'El vector documental es precalculado como promedio de vectores que incluyen información subléxica.',
      },
      {
        stepNumber: 2,
        label: 'Similitud angular cruda',
        formula: 'c = cos(v(A), v(B)), c ∈ [−1, 1]',
        explanation: 'El producto escalar se divide por las normas de los vectores; este valor se informa como rawScore.',
      },
      {
        stepNumber: 3,
        label: 'Escalamiento lineal',
        formula: 'normalizedScore = (c + 1) / 2',
        explanation: 'Trasladar y dividir el coseno mapea el intervalo [−1,1] al intervalo [0,1].',
      },
    ] satisfies MathStep[],
  },
  {
    title: 'Sentence-BERT (IA-2)',
    category: 'Transformer contextual',
    purpose: 'Compara embeddings contextuales precalculados de los artículos.',
    formula: (
      <>
        cos(u,v) = u · v, si ||u||₂ = ||v||₂ = 1
        <br />
        Sim_norm = (cos(u,v) + 1) / 2
      </>
    ),
    notation: [
      { symbol: 'u, v', meaning: 'Embeddings IA-2 normalizados de 384 dimensiones.' },
      { symbol: 'u · v', meaning: 'Producto punto equivalente al coseno para vectores unitarios.' },
      { symbol: 'Sim_norm', meaning: 'Escala normalizada entregada por el backend.' },
    ],
    interpretation: 'El producto punto acotado se transforma a [0,1]; no se recalculan embeddings en el navegador.',
    preprocessingProfile: 'P-TEXTO' as const,
    caveat: 'La implementación compara directamente vectores unitarios mediante coseno; no usa la transformación 1/(1+d) de distancia euclídea.',
    derivationSteps: [
      {
        stepNumber: 1,
        label: 'Embeddings contextuales',
        formula: 'u, v ∈ R³⁸⁴; ||u||₂ = ||v||₂ = 1',
        explanation: 'El backend carga embeddings Transformer precalculados y valida que tengan norma L2 unitaria.',
      },
      {
        stepNumber: 2,
        label: 'Coseno crudo',
        formula: 'c = cos(u,v) = u · v',
        explanation: 'Para vectores unitarios, el producto punto equivale al coseno; se informa como rawScore.',
      },
      {
        stepNumber: 3,
        label: 'Escala normalizada',
        formula: 'normalizedScore = (c + 1) / 2',
        explanation: 'El backend transforma el coseno de [−1,1] a [0,1]; no aplica una distancia euclídea.',
      },
    ] satisfies MathStep[],
  },
];

type ResultMode = 'single' | 'all';
type SingleResultState = 'idle' | 'loading';

function formatNormalizedScore(score: number): string {
  return new Intl.NumberFormat('es-CO', {
    style: 'percent',
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }).format(score);
}

function normalizedBadgeClass(score: number): string {
  if (score >= 0.75) return 'badge--qualitative-high';
  if (score >= 0.45) return 'badge--qualitative-medium';
  return 'badge--qualitative-low';
}

function qualitativeLabel(score: number): string {
  if (score >= 0.75) return 'Alta';
  if (score >= 0.45) return 'Media';
  return 'Baja';
}

function qualitativeConclusion(score: number): string {
  if (score >= 0.75) {
    return 'Según este algoritmo, los documentos presentan alta similitud. Esto no demuestra por sí solo que sean duplicados ni que exista una relación causal.';
  }
  if (score >= 0.45) {
    return 'Según este algoritmo, los documentos presentan similitud intermedia; conviene contrastar sus temas, métodos y contexto bibliográfico.';
  }
  return 'Según este algoritmo, la similitud es baja. El resultado depende de la representación y el criterio de este método.';
}

function rawScoreMeaning(result: SimilarityResponse): string {
  switch (result.algorithm) {
    case 'levenshtein':
      return 'Distancia mínima de edición: número de inserciones, borrados y sustituciones unitarias entre textos P-TEXTO (menor implica más parecido).';
    case 'lcs':
      return 'Longitud de la subsecuencia común más larga, medida en tokens conservando el orden (mayor implica más coincidencia ordenada).';
    case 'tfidf':
      return 'Coseno entre vectores dispersos TF-IDF; mide su alineación angular antes del acotamiento a [0,1].';
    case 'jaccard':
      return 'Coeficiente |Set(A) ∩ Set(B)| / |Set(A) ∪ Set(B)|. Es una razón adimensional, no un número de palabras coincidentes.';
    case 'fasttext':
      return 'Coseno entre vectores documentales FastText de 300 dimensiones; su escala cruda es [−1,1].';
    case 'sentence-bert':
      return 'Coseno entre embeddings contextuales unitarios de 384 dimensiones; rawScore es el producto punto.';
  }
}

function formatRawScore(score: number, algorithm: SimilarityResponse['algorithm']): string {
  if (algorithm === 'levenshtein' || algorithm === 'lcs') {
    return new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 }).format(score);
  }
  return score.toFixed(4);
}

function normalizePText(article: Article): string {
  return `${article.title} ${article.abstractText}`
    .normalize('NFKC')
    .replace(/(\p{L})-\r?\n(\p{L})/gu, '$1$2')
    .replace(/\s+/gu, ' ')
    .trim();
}

function resultDerivation(
  result: SimilarityResponse,
  articleA: Article | undefined,
  articleB: Article | undefined,
): MathStep[] {
  const raw = formatRawScore(result.rawScore, result.algorithm);
  const normalized = result.normalizedScore.toFixed(4);

  switch (result.algorithm) {
    case 'levenshtein': {
      if (!articleA || !articleB) {
        return [{
          stepNumber: 1,
          label: 'Resultado del servicio',
          formula: `E = ${raw}; Sim_edit = ${normalized}`,
          explanation: 'La API devuelve la distancia cruda y su puntuación normalizada. No hay metadatos de ambos artículos para reconstruir las longitudes.',
        }];
      }
      const lengthA = Array.from(normalizePText(articleA)).length;
      const lengthB = Array.from(normalizePText(articleB)).length;
      const maximumLength = Math.max(lengthA, lengthB);
      return [
        {
          stepNumber: 1,
          label: 'Distancia cruda reportada',
          formula: `E = ${raw} operaciones`,
          explanation: 'El backend cuenta ediciones de costo unitario sobre puntos de código de los textos P-TEXTO.',
        },
        {
          stepNumber: 2,
          label: 'Longitud mayor de los textos',
          formula: `max(|A|, |B|) = max(${lengthA}, ${lengthB}) = ${maximumLength}`,
          explanation: 'Longitudes de referencia contadas localmente sobre los campos de título y resumen normalizados.',
        },
        {
          stepNumber: 3,
          label: 'Proporción de edición',
          formula: maximumLength === 0
            ? 'Ambos textos vacíos'
            : `${raw} / ${maximumLength} = ${(result.rawScore / maximumLength).toFixed(4)}`,
          explanation: 'La distancia se divide por la longitud mayor antes de tomar el complemento.',
        },
        {
          stepNumber: 4,
          label: 'Similitud normalizada',
          formula: maximumLength === 0
            ? `Sim_edit = 1.0000 (API: ${normalized})`
            : `1 − ${raw} / ${maximumLength} = ${(1 - result.rawScore / maximumLength).toFixed(4)} (API: ${normalized})`,
          explanation: 'El valor de la API es autoritativo; las longitudes locales siguen el perfil P-TEXTO para explicar la fórmula.',
        },
      ];
    }
    case 'lcs':
      return [
        {
          stepNumber: 1,
          label: 'Longitud LCS reportada',
          formula: `L = ${raw} tokens`,
          explanation: 'El rawScore es el tamaño de la subsecuencia común más larga en orden.',
        },
        {
          stepNumber: 2,
          label: 'Denominador de tokens',
          formula: 'M = max(|Tok(A)|, |Tok(B)|)',
          explanation: 'El endpoint no expone las secuencias tokenizadas ni sus longitudes; por ello no se inventa M ni se recalcula la división.',
        },
        {
          stepNumber: 3,
          label: 'Normalización devuelta por el backend',
          formula: `Sim_LCS = L / M; normalizedScore = ${normalized}`,
          explanation: 'La API calcula y devuelve el cociente usando los tokens P-TOKENS del backend.',
        },
      ];
    case 'tfidf':
      return [
        {
          stepNumber: 1,
          label: 'Coseno crudo reportado',
          formula: `cos(u, v) = ${result.rawScore.toFixed(4)}`,
          explanation: 'El rawScore ya incluye la ponderación TF-IDF, con IDF fijado al corpus global de 20 documentos.',
        },
        {
          stepNumber: 2,
          label: 'Acotamiento a la escala común',
          formula: `max(0, min(1, ${result.rawScore.toFixed(4)})) = ${Math.max(0, Math.min(1, result.rawScore)).toFixed(4)}`,
          explanation: 'El núcleo limita el coseno a [0,1] para obtener el normalizedScore.',
        },
        {
          stepNumber: 3,
          label: 'Puntaje normalizado',
          formula: `normalizedScore = ${normalized}`,
          explanation: 'Este es el valor autoritativo recibido del backend; la vista no vuelve a calcular los vectores.',
        },
      ];
    case 'jaccard':
      return [
        {
          stepNumber: 1,
          label: 'Razón de conjuntos reportada',
          formula: `J = |A ∩ B| / |A ∪ B| = ${result.rawScore.toFixed(4)}`,
          explanation: 'El rawScore ya es la razón de solapamiento entre conjuntos de tokens únicos.',
        },
        {
          stepNumber: 2,
          label: 'Normalización',
          formula: `normalizedScore = rawScore = ${normalized}`,
          explanation: 'Jaccard ya pertenece a [0,1], por lo que rawScore y normalizedScore coinciden. El caso de dos conjuntos vacíos se define como 1.',
        },
      ];
    case 'fasttext':
    case 'sentence-bert':
      return [
        {
          stepNumber: 1,
          label: 'Coseno crudo reportado',
          formula: `cos(u, v) = ${result.rawScore.toFixed(4)}`,
          explanation: result.algorithm === 'fasttext'
            ? 'Coseno de los vectores documentales FastText de 300 dimensiones.'
            : 'Producto punto equivalente al coseno porque los embeddings de 384 dimensiones tienen norma unitaria.',
        },
        {
          stepNumber: 2,
          label: 'Traslación y escalamiento',
          formula: `(${result.rawScore.toFixed(4)} + 1) / 2 = ${((result.rawScore + 1) / 2).toFixed(4)}`,
          explanation: 'La transformación mapea el intervalo crudo [−1,1] a la escala [0,1].',
        },
        {
          stepNumber: 3,
          label: 'Puntaje normalizado',
          formula: `normalizedScore = ${normalized}`,
          explanation: 'Valor entregado por el backend; no se generan embeddings en el navegador.',
        },
      ];
  }
}

export function SimilarityPage() {
  const [articles, setArticles] = useState<Article[]>([]);
  const [articleAId, setArticleAId] = useState('');
  const [articleBId, setArticleBId] = useState('');
  const [algorithm, setAlgorithm] = useState<SimilarityRequest['algorithm']>('levenshtein');
  const [result, setResult] = useState<SimilarityResponse | null>(null);
  const [error, setError] = useState('');
  const [articleLoadError, setArticleLoadError] = useState('');
  const [resultMode, setResultMode] = useState<ResultMode>('single');
  const [singleResultState, setSingleResultState] = useState<SingleResultState>('idle');
  const requestController = useRef<AbortController | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    api.articles(controller.signal)
      .then((loaded) => {
        setArticles(loaded);
        setArticleAId(loaded[0]?.id ?? '');
        setArticleBId(loaded[1]?.id ?? loaded[0]?.id ?? '');
      })
      .catch((cause: unknown) => {
        if (!(cause instanceof Error && cause.name === 'AbortError')) {
          setArticleLoadError(errorMessage(cause));
        }
      });
    return () => controller.abort();
  }, []);

  useEffect(
    () => () => {
      requestController.current?.abort();
    },
    [],
  );

  const articleA = articles.find((article) => article.id === articleAId);
  const articleB = articles.find((article) => article.id === articleBId);
  const articleOptions = articles.map((article) => ({
    value: article.id,
    label: `${article.id} — ${article.title}`,
  }));

  function resetResults() {
    requestController.current?.abort();
    requestController.current = null;
    setResult(null);
    setError('');
    setSingleResultState('idle');
    setResultMode('single');
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!articleAId || !articleBId) return;
    requestController.current?.abort();
    const controller = new AbortController();
    requestController.current = controller;
    setResultMode('single');
    setError('');
    setResult(null);
    setSingleResultState('loading');
    try {
      const response = await api.similarity({
        articleAId,
        articleBId,
        algorithm,
      }, controller.signal);
      if (controller.signal.aborted) return;
      setResult(response);
    } catch (cause: unknown) {
      if (!controller.signal.aborted) setError(errorMessage(cause));
    } finally {
      if (!controller.signal.aborted) setSingleResultState('idle');
    }
  }

  function compareAll() {
    if (!articleAId || !articleBId) return;
    requestController.current?.abort();
    requestController.current = null;
    setError('');
    setResult(null);
    setSingleResultState('idle');
    setResultMode('all');
  }

  return (
    <section className="page-stack similarity-workspace" aria-labelledby="similarity-title">
      <header className="hero">
        <p className="eyebrow">Entrada → Proceso → Salida</p>
        <h1 id="similarity-title">Comparación de similitud entre artículos</h1>
        <p>
          Selecciona dos artículos, revisa qué representa cada método y compara sus resultados
          normalizados y crudos sin salir del espacio analítico.
        </p>
      </header>

      <section className="similarity-section" aria-labelledby="similarity-input-title">
        <div className="section-heading">
          <p className="eyebrow">01 · Entrada</p>
          <h2 id="similarity-input-title">Selecciona y revisa los artículos</h2>
        </div>

        {articleLoadError && (
          <Alert title="No se pudieron cargar los artículos" tone="error">
            {articleLoadError}
          </Alert>
        )}

        <form className="card form-grid" onSubmit={submit}>
          <Select
            label="Artículo A"
            name="articleA"
            onChange={(event) => {
              setArticleAId(event.currentTarget.value);
              resetResults();
            }}
            options={articleOptions}
            required
            value={articleAId}
          />
          <Select
            label="Artículo B"
            name="articleB"
            onChange={(event) => {
              setArticleBId(event.currentTarget.value);
              resetResults();
            }}
            options={articleOptions}
            required
            value={articleBId}
          />
          <Select
            label="Algoritmo individual"
            name="algorithm"
            onChange={(event) => {
              const value = event.currentTarget.value;
              if (isSimilarityAlgorithm(value)) {
                setAlgorithm(value);
                resetResults();
              }
            }}
            options={algorithmOptions}
            value={algorithm}
          />
          <div className="similarity-actions">
            <Button type="submit" disabled={!articles.length || singleResultState === 'loading'}>
              {singleResultState === 'loading' ? 'Calculando…' : 'Calcular algoritmo'}
            </Button>
            <Button
              disabled={!articles.length}
              onClick={compareAll}
              type="button"
              variant="secondary"
            >
              Comparar Todos los Algoritmos
            </Button>
          </div>
        </form>

        {!articles.length && !articleLoadError && (
          <p className="page-intro" role="status">Cargando artículos del corpus…</p>
        )}

        <div className="form-grid similarity-article-grid">
          <article className="card article-preview" aria-labelledby="article-a-title">
            <p className="eyebrow">Artículo A</p>
            {articleA ? (
              <>
                <h3 id="article-a-title">{articleA.title}</h3>
                <p className="article-preview__id"><strong>ID:</strong> {articleA.id}</p>
                <p>
                  {articleA.abstractText.length > 360
                    ? `${articleA.abstractText.slice(0, 360).trimEnd()}…`
                    : articleA.abstractText}
                </p>
              </>
            ) : <p>No hay artículo seleccionado.</p>}
          </article>
          <article className="card article-preview" aria-labelledby="article-b-title">
            <p className="eyebrow">Artículo B</p>
            {articleB ? (
              <>
                <h3 id="article-b-title">{articleB.title}</h3>
                <p className="article-preview__id"><strong>ID:</strong> {articleB.id}</p>
                <p>
                  {articleB.abstractText.length > 360
                    ? `${articleB.abstractText.slice(0, 360).trimEnd()}…`
                    : articleB.abstractText}
                </p>
              </>
            ) : <p>No hay artículo seleccionado.</p>}
          </article>
        </div>
      </section>

      <section className="similarity-section" aria-labelledby="similarity-process-title">
        <div className="section-heading">
          <p className="eyebrow">02 · Proceso</p>
          <h2 id="similarity-process-title">Fundamento de los seis algoritmos</h2>
          <p className="page-intro">
            Abre cada explicación para consultar la formulación, la notación y las consideraciones
            de la implementación.
          </p>
        </div>
        <div className="similarity-explanations">
          {algorithmExplanations.map((explanation) => (
            <details className="similarity-explanation" key={explanation.title}>
              <summary>{explanation.title}</summary>
              <MathExplainerCard {...explanation} />
            </details>
          ))}
        </div>
      </section>

      <section className="similarity-section" aria-labelledby="similarity-output-title">
        <div className="section-heading">
          <p className="eyebrow">03 · Salida</p>
          <h2 id="similarity-output-title">Resultados comparables</h2>
          <p className="page-intro">
            La puntuación normalizada permite comparar algoritmos; la puntuación cruda conserva
            la escala nativa de cada método.
          </p>
        </div>

        {error && <Alert title="No se pudo calcular" tone="error">{error}</Alert>}

        {resultMode === 'all' && articleAId && articleBId && (
          <SimilarityComparisonTable
            articleAId={articleAId}
            articleBId={articleBId}
            key={`${articleAId}:${articleBId}`}
          />
        )}

        {singleResultState === 'loading' && (
          <p className="page-intro" role="status">Calculando similitud…</p>
        )}

        {result && resultMode === 'single' && (
          <article className="card result-card" aria-live="polite">
            <p className="eyebrow">{algorithmOptions.find(({ value }) => value === result.algorithm)?.label}</p>
            <h3>Resultado individual</h3>
            <div className="result-interpretation">
              <section className="result-interpretation__score" aria-label="Puntaje comparable">
                <h4>Similitud normalizada</h4>
                <div className="score-dual-display">
                  <strong>{formatNormalizedScore(result.normalizedScore)}</strong>
                  <span>({result.normalizedScore.toFixed(4)})</span>
                  <span className={normalizedBadgeClass(result.normalizedScore)}>
                    Similitud {qualitativeLabel(result.normalizedScore)}
                  </span>
                </div>
              </section>

              <section>
                <h4>Significado del puntaje crudo</h4>
                <p>
                  <strong>{formatRawScore(result.rawScore, result.algorithm)}</strong>
                  {' — '}
                  {rawScoreMeaning(result)}
                </p>
              </section>

              <section>
                <h4>Conclusión cualitativa</h4>
                <p>{qualitativeConclusion(result.normalizedScore)}</p>
                <p className="result-interpretation__articles">
                  Artículos comparados: {result.articleAId} y {result.articleBId}.
                </p>
              </section>

              <section aria-label="Demostración matemática paso a paso">
                <h4>Demostración matemática paso a paso</h4>
                <ol className="math-card__steps">
                  {resultDerivation(result, articleA, articleB).map((step) => (
                    <li key={step.stepNumber}>
                      <span className="math-card__step-number">{step.stepNumber}</span>
                      <div className="math-card__step-content">
                        <strong>{step.label}</strong>
                        <code>{step.formula}</code>
                        <p>{step.explanation}</p>
                      </div>
                    </li>
                  ))}
                </ol>
              </section>
            </div>
          </article>
        )}
      </section>
    </section>
  );
}
