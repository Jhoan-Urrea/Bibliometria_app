import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import type { Article, SearchResponse, SearchTraceResponse } from '../api/client';
import { api } from '../api/client';
import { errorMessage } from '../api/errorMessage';
import { Alert } from '../components/Alert';
import { Button } from '../components/Button';
import { AlgorithmTraceInspector, type TraceEvent } from '../components/search/AlgorithmTraceInspector';
import { PaginationControl } from '../components/search/PaginationControl';
import { MathExplainerCard } from '../components/explaining/MathExplainerCard';
import { Select } from '../components/Select';

type SearchHit = SearchResponse['results'][number];
type SearchMode = 'relevance' | 'exact';
type ExactAlgorithm = 'KMP' | 'BOYER_MOORE' | 'RABIN_KARP';
type ApiTraceEvent = SearchTraceResponse['articles'][number]['events'][number];

const algorithmOptions = [
  { value: 'KMP', label: 'KMP' },
  { value: 'BOYER_MOORE', label: 'Boyer–Moore' },
  { value: 'RABIN_KARP', label: 'Rabin–Karp' },
] as const;

const apiAlgorithmNames: Record<ExactAlgorithm, ApiTraceEvent['algorithm']> = {
  KMP: 'KMP',
  BOYER_MOORE: 'Boyer-Moore',
  RABIN_KARP: 'Rabin-Karp',
};

function isExactAlgorithm(value: string): value is ExactAlgorithm {
  return algorithmOptions.some((option) => option.value === value);
}

function numericValue(data: Record<string, unknown>, key: string): number | undefined {
  const value = data[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}

function booleanValue(data: Record<string, unknown>, key: string): boolean | undefined {
  const value = data[key];
  return typeof value === 'boolean' ? value : undefined;
}

function numberArray(data: Record<string, unknown>, key: string): number[] | undefined {
  const value = data[key];
  if (!Array.isArray(value) || !value.every((item) => typeof item === 'number')) {
    return undefined;
  }
  return value;
}

function numberRecord(
  data: Record<string, unknown>,
  key: string,
): Record<string, number> | undefined {
  const value = data[key];
  if (
    typeof value !== 'object'
    || value === null
    || Array.isArray(value)
  ) {
    return undefined;
  }
  const record: Record<string, number> = {};
  for (const [name, item] of Object.entries(value)) {
    if (typeof item !== 'number') return undefined;
    record[name] = item;
  }
  return record;
}

function normalizedEvents(events: ApiTraceEvent[], algorithm: ExactAlgorithm): TraceEvent[] {
  const apiName = apiAlgorithmNames[algorithm];
  const selectedEvents = events.filter((event) => event.algorithm === apiName);
  let patternHash: number | undefined;

  for (const event of selectedEvents) {
    if (event.step === 'hashPatron') {
      patternHash = numericValue(event.data, 'hash');
      break;
    }
  }

  return selectedEvents.map((event, index) => {
    const data = event.data;
    const stepNumber = index + 1;

    if (algorithm === 'KMP') {
      const lpsState = numberArray(data, 'lps');
      return {
        step: stepNumber,
        textIndex: 0,
        patternIndex: 0,
        match: false,
        shift: 0,
        lpsState,
        commentary: lpsState
          ? `Preprocesamiento: tabla LPS calculada para el patrón. Evento: ${event.step}.`
          : `Evento recibido: ${event.step}.`,
      };
    }

    if (algorithm === 'BOYER_MOORE') {
      const badCharacterTable = numberRecord(data, 'ultimaAparicion');
      const goodSuffixShifts = numberArray(data, 'buenSufijo');
      return {
        step: stepNumber,
        textIndex: 0,
        patternIndex: 0,
        match: false,
        shift: 0,
        badCharacterTable,
        goodSuffixShifts,
        shiftAfterMatch: numericValue(data, 'desplazamientoTrasOcurrencia'),
        commentary: `Preprocesamiento de Boyer–Moore (${event.step}). La API no devuelve comparaciones ni desplazamientos de cada ventana.`,
      };
    }

    const windowIndex = numericValue(data, 'inicio') ?? 0;
    const textHash = numericValue(data, 'hash');
    const hashMatch = booleanValue(data, 'coincideHash') ?? false;
    const isWindow = event.step === 'ventana';

    if (isWindow && textHash !== undefined && patternHash !== undefined) {
      return {
        step: stepNumber,
        textIndex: windowIndex,
        patternIndex: 0,
        match: hashMatch,
        shift: 0,
        hashState: { patternHash, textHash },
        commentary: hashMatch
          ? `Ventana en s=${windowIndex}: los hashes coinciden; Rabin–Karp verifica después los caracteres para descartar una colisión.`
          : `Ventana en s=${windowIndex}: los hashes difieren y no requiere verificación carácter por carácter.`,
      };
    }

    return {
      step: stepNumber,
      textIndex: 0,
      patternIndex: 0,
      match: false,
      shift: 0,
      commentary: `Preprocesamiento de hash: ${JSON.stringify(data)}`,
    };
  });
}

function toPText(article: Article): string {
  return `${article.title} ${article.abstractText}`
    .normalize('NFKC')
    .replace(/(?<=\p{L})-\r?\n(?=\p{L})/gu, '')
    .replace(/\s+/gu, ' ')
    .trim();
}

function algorithmLabel(algorithm: ExactAlgorithm): string {
  return algorithm === 'BOYER_MOORE'
    ? 'Boyer–Moore'
    : algorithm === 'RABIN_KARP'
      ? 'Rabin–Karp'
      : 'KMP';
}

function scoreBadgeClass(score: number): string {
  if (score >= 0.75) return 'badge--qualitative-high';
  if (score >= 0.45) return 'badge--qualitative-medium';
  return 'badge--qualitative-low';
}

function relevanceLevel(score: number): string {
  if (score >= 0.75) return 'Relevancia alta';
  if (score >= 0.45) return 'Relevancia media';
  return 'Relevancia baja';
}

function formatPercent(score: number): string {
  return new Intl.NumberFormat('es-CO', {
    style: 'percent',
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }).format(score);
}

export function SearchPage() {
  const [mode, setMode] = useState<SearchMode>('relevance');
  const [articles, setArticles] = useState<Article[]>([]);
  const [articleError, setArticleError] = useState('');
  const [query, setQuery] = useState('');
  const [result, setResult] = useState<SearchResponse | null>(null);
  const [allResults, setAllResults] = useState<SearchHit[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [searchError, setSearchError] = useState('');
  const [searching, setSearching] = useState(false);
  const [selectedArticleId, setSelectedArticleId] = useState('');
  const [pattern, setPattern] = useState('');
  const [algorithm, setAlgorithm] = useState<ExactAlgorithm>('KMP');
  const [trace, setTrace] = useState<SearchTraceResponse | null>(null);
  const [traceError, setTraceError] = useState('');
  const [tracing, setTracing] = useState(false);
  const searchController = useRef<AbortController | null>(null);
  const traceController = useRef<AbortController | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    api.articles(controller.signal)
      .then((loaded) => {
        setArticles(loaded);
        setSelectedArticleId(loaded[0]?.id ?? '');
      })
      .catch((cause: unknown) => {
        if (!(cause instanceof Error && cause.name === 'AbortError')) {
          setArticleError(errorMessage(cause));
        }
      });

    return () => {
      controller.abort();
      searchController.current?.abort();
      traceController.current?.abort();
    };
  }, []);

  const selectedArticle = articles.find((article) => article.id === selectedArticleId);
  const pText = useMemo(
    () => (selectedArticle ? toPText(selectedArticle) : ''),
    [selectedArticle],
  );
  const selectedArticleTrace = trace?.articles.find(
    (article) => article.articleId === selectedArticleId,
  );
  const traceEvents = useMemo(
    () => normalizedEvents(selectedArticleTrace?.events ?? [], algorithm),
    [algorithm, selectedArticleTrace],
  );
  const matchCount =
    selectedArticleTrace?.positionsByAlgorithm[apiAlgorithmNames[algorithm]]?.length ?? 0;

  async function runRelevanceSearch(term = query) {
    if (!term.trim()) {
      setSearchError('Ingresa términos de búsqueda antes de continuar.');
      return;
    }
    searchController.current?.abort();
    const controller = new AbortController();
    searchController.current = controller;
    setSearching(true);
    setSearchError('');
    setResult(null);
    setAllResults([]);
    setPage(1);

    try {
      const response = await api.search(
        { query: term.trim(), page: 1, pageSize: 100 },
        controller.signal,
      );
      const remainingPages = await Promise.all(
        Array.from({ length: Math.max(0, response.totalPages - 1) }, (_, index) =>
          api.search(
            { query: term.trim(), page: index + 2, pageSize: 100 },
            controller.signal,
          ),
        ),
      );
      if (!controller.signal.aborted) {
        const completeResults = [
          ...response.results,
          ...remainingPages.flatMap((pageResponse) => pageResponse.results),
        ];
        if (completeResults.length !== response.totalHits) {
          throw new Error(
            `La API reportó ${response.totalHits} resultados, pero se recibieron ${completeResults.length}.`,
          );
        }
        setResult(response);
        setAllResults(completeResults);
      }
    } catch (cause: unknown) {
      if (!controller.signal.aborted) setSearchError(errorMessage(cause));
    } finally {
      if (!controller.signal.aborted) setSearching(false);
    }
  }

  async function submitRelevance(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runRelevanceSearch();
  }

  async function submitExactTrace(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!pattern.trim()) {
      setTraceError('El patrón exacto no puede estar vacío.');
      setTrace(null);
      return;
    }
    if (!selectedArticleId) {
      setTraceError('Selecciona un artículo para solicitar su traza.');
      setTrace(null);
      return;
    }

    traceController.current?.abort();
    const controller = new AbortController();
    traceController.current = controller;
    setTracing(true);
    setTraceError('');
    setTrace(null);

    try {
      const response = await api.searchTrace(pattern, selectedArticleId, controller.signal);
      if (!controller.signal.aborted) setTrace(response);
    } catch (cause: unknown) {
      if (!controller.signal.aborted) setTraceError(errorMessage(cause));
    } finally {
      if (!controller.signal.aborted) setTracing(false);
    }
  }

  function changeMode(nextMode: SearchMode) {
    setMode(nextMode);
    searchController.current?.abort();
    traceController.current?.abort();
    setSearching(false);
    setTracing(false);
  }

  const totalPages = result ? Math.ceil(result.totalHits / pageSize) : 0;
  const visibleResults = allResults.slice((page - 1) * pageSize, page * pageSize);

  return (
    <section className="page-stack search-workspace" aria-labelledby="search-title">
      <header className="hero">
        <p className="eyebrow">Entrada → Proceso → Salida</p>
        <h1 id="search-title">Búsqueda bibliográfica y traza algorítmica</h1>
        <p>
          Explora documentos por relevancia o inspecciona el recorrido de una coincidencia exacta
          en el texto normalizado del corpus.
        </p>
      </header>

      <section className="search-section" aria-labelledby="search-input-title">
        <div className="section-heading">
          <p className="eyebrow">01 · Entrada</p>
          <h2 id="search-input-title">Modo de búsqueda y parámetros</h2>
        </div>

        <div className="tabs search-mode-tabs" role="tablist" aria-label="Modo de búsqueda">
          <button
            aria-controls="relevance-search-panel"
            aria-selected={mode === 'relevance'}
            className="tabs__tab"
            id="relevance-mode-tab"
            onClick={() => changeMode('relevance')}
            role="tab"
            type="button"
          >
            Búsqueda por Relevancia
          </button>
          <button
            aria-controls="exact-search-panel"
            aria-selected={mode === 'exact'}
            className="tabs__tab"
            id="exact-mode-tab"
            onClick={() => changeMode('exact')}
            role="tab"
            type="button"
          >
            Coincidencia Exacta con Traza
          </button>
        </div>

        {articleError && <Alert title="No se pudieron cargar los artículos" tone="error">{articleError}</Alert>}

        {mode === 'relevance' ? (
          <form
            aria-labelledby="relevance-mode-tab"
            className="card form-grid form-grid--compact"
            id="relevance-search-panel"
            onSubmit={submitRelevance}
            role="tabpanel"
            tabIndex={0}
          >
            <aside className="search-tab-banner">
              <strong>Búsqueda por relevancia</strong>
              <p>
                Recupera e indica el nivel de coincidencia temática de los 20 artículos del corpus
                según términos de consulta.
              </p>
            </aside>
            <label className="field" htmlFor="search-query">
              <span className="field__label">Términos de búsqueda</span>
              <input
                className="field__control"
                id="search-query"
                maxLength={200}
                onChange={(event) => setQuery(event.currentTarget.value)}
                placeholder="Ej.: generative AI, bibliometría"
                required
                type="search"
                value={query}
              />
            </label>
            <label className="field" htmlFor="search-page-size">
              <span className="field__label">Mostrar por página</span>
              <select
                className="field__control"
                id="search-page-size"
                onChange={(event) => {
                  setPage(1);
                  setPageSize(Number(event.currentTarget.value));
                }}
                value={pageSize}
              >
                {[10, 20, 50].map((size) => <option key={size} value={size}>{size}</option>)}
              </select>
            </label>
            <Button disabled={searching} type="submit">
              {searching ? 'Buscando…' : 'Buscar artículos'}
            </Button>
          </form>
        ) : (
          <form
            aria-labelledby="exact-mode-tab"
            className="card search-exact-form"
            id="exact-search-panel"
            onSubmit={submitExactTrace}
            role="tabpanel"
            tabIndex={0}
          >
            <aside className="search-tab-banner">
              <strong>Traza exacta</strong>
              <p>
                Inspecciona el comportamiento interno de los algoritmos KMP, Boyer–Moore y
                Rabin–Karp buscando una subcadena en un artículo del corpus.
              </p>
            </aside>
            <div className="form-grid form-grid--compact">
              <Select
                label="Artículo del corpus"
                name="trace-article"
                onChange={(event) => {
                  setSelectedArticleId(event.currentTarget.value);
                  setTrace(null);
                }}
                options={articles.map((article) => ({
                  value: article.id,
                  label: `${article.id} — ${article.title}`,
                }))}
                required
                value={selectedArticleId}
              />
              <Select
                label="Algoritmo para inspeccionar"
                name="trace-algorithm"
                onChange={(event) => {
                  if (isExactAlgorithm(event.currentTarget.value)) {
                    setAlgorithm(event.currentTarget.value);
                  }
                }}
                options={algorithmOptions}
                value={algorithm}
              />
              <label className="field" htmlFor="trace-pattern">
                <span className="field__label">
                  Patrón exacto <span className="required-badge">* Requerido</span>
                </span>
                <input
                  className="field__control"
                  id="trace-pattern"
                  maxLength={200}
                  onChange={(event) => setPattern(event.currentTarget.value)}
                  placeholder="generative AI"
                  required
                  type="search"
                  value={pattern}
                />
                <span className="field__hint">
                  Obligatorio y sensible a mayúsculas. P-TEXTO normaliza Unicode, une palabras
                  partidas por guion de línea y colapsa espacios; conserva puntuación y caja.
                </span>
              </label>
            </div>
            <label className="field search-target-text" htmlFor="trace-target-text">
              <span className="field__label">
                Texto P-TEXTO (título y abstract)
                <span className="corpus-text-badge">Texto del Corpus</span>
              </span>
              <textarea
                className="field__control"
                id="trace-target-text"
                readOnly
                rows={7}
                value={pText}
              />
              <span className="field__hint">
                Vista normalizada localmente para alinear los índices. La búsqueda y las posiciones
                reportadas por el servidor siguen siendo autoritativas.
              </span>
            </label>
            <Button disabled={tracing || !selectedArticleId} type="submit">
              {tracing ? 'Generando traza…' : 'Buscar patrón y generar traza'}
            </Button>
          </form>
        )}
      </section>

      <section className="search-section" aria-labelledby="search-process-title">
        <div className="section-heading">
          <p className="eyebrow">02 · Proceso</p>
          <h2 id="search-process-title">Coincidencia exacta sobre P-TEXTO</h2>
          <p className="page-intro">
            P-TEXTO conserva mayúsculas y puntuación, une palabras partidas por guion de línea y
            colapsa espacios. Por ello la búsqueda exacta distingue mayúsculas, pero no conserva
            los saltos de línea originales.
          </p>
        </div>
        <div className="search-theory-grid">
          <MathExplainerCard
            category="String matching"
            caveat="La posición y la traza detallada por carácter no se incluyen en el contrato de la API; el endpoint sí devuelve coincidencias y el evento de preprocesamiento LPS."
            formula={<>LPS[i] = longitud del prefijo propio más largo de P[0..i] que también es sufijo</>}
            interpretation="Tras un desajuste, KMP conserva el prefijo coincidente usando LPS y continúa sin retroceder en el texto."
            notation={[
              { symbol: 'P', meaning: 'Patrón exacto.' },
              { symbol: 'LPS[i]', meaning: 'Estado de prefijo/sufijo para el prefijo del patrón.' },
              { symbol: 'n, m', meaning: 'Longitudes del texto y el patrón.' },
              { symbol: 'Complejidad', meaning: 'Búsqueda en tiempo O(n + m), con preprocesamiento lineal del patrón.' },
            ]}
            purpose="Busca todas las apariciones del patrón evitando comparaciones repetidas."
            title="KMP"
          />
          <MathExplainerCard
            category="String matching"
            caveat="La API expone las tablas de preprocesamiento, pero no eventos individuales de comparación ni el desplazamiento aplicado en cada ventana."
            formula={
              <>
                Δ₁ = j − last(T[s+j])
                <br />
                Shift = max(1, Δ₁, Δ₂)
              </>
            }
            interpretation="Boyer–Moore compara desde el final del patrón y usa la información del carácter malo y del sufijo bueno para avanzar."
            notation={[
              { symbol: 'last(c)', meaning: 'Última posición del carácter c en el patrón.' },
              { symbol: 'Δ₁', meaning: 'Desplazamiento sugerido por la regla del mal carácter.' },
              { symbol: 'Δ₂', meaning: 'Desplazamiento preprocesado por la regla del buen sufijo.' },
            ]}
            purpose="Acelera la búsqueda de patrones, especialmente cuando puede saltar varias posiciones."
            title="Boyer–Moore"
          />
          <MathExplainerCard
            category="String matching"
            caveat="Los hashes coincidentes son candidatos, no prueba de coincidencia: el código vuelve a verificar caracteres. La API devuelve hashes por ventana, pero no los eventos de esa verificación carácter por carácter."
            formula={
              <>
                φ(s+1) = (β · (φ(s) − c₁ · h) + cₘ₊₁) mod Q
                <br />
                β = 257, Q = 1,000,000,007
              </>
            }
            interpretation="El hash rodante actualiza el valor de la ventana en tiempo constante; ante un hash igual se verifica el contenido exacto."
            notation={[
              { symbol: 'φ(s)', meaning: 'Hash polinomial de la ventana que inicia en s.' },
              { symbol: 'c₁', meaning: 'Valor del carácter que sale de la ventana.' },
              { symbol: 'h', meaning: 'Potencia β^(m−1) módulo Q.' },
              { symbol: 'cₘ₊₁', meaning: 'Valor del carácter que entra a la ventana.' },
              { symbol: 'Módulo positivo', meaning: 'Java suma Q antes de aplicar % para evitar residuos negativos.' },
            ]}
            purpose="Usa hashes de ventanas deslizantes para filtrar posiciones candidatas de coincidencia."
            title="Rabin–Karp"
          />
        </div>
      </section>

      <section className="search-section" aria-labelledby="search-output-title">
        <div className="section-heading">
          <p className="eyebrow">03 · Salida</p>
          <h2 id="search-output-title">Resultados y reproducción de traza</h2>
        </div>

        {mode === 'relevance' && (
          <>
            {searchError && <Alert title="La búsqueda falló" tone="error">{searchError}</Alert>}
            {searching && <p className="page-intro" role="status">Buscando artículos…</p>}
            {result && !searching && (
              <>
                {result.results.length === 0 ? (
                  <p className="empty-state">No se encontraron artículos para esta consulta.</p>
                ) : (
                  <div className="search-results">
                    {visibleResults.map((hit: SearchHit) => (
                      <article className="card search-result-card" key={hit.article.id}>
                        <div className="search-result-card__header">
                          <span className="search-result-card__id">{hit.article.id}</span>
                          <div className="search-result-card__score-group">
                            <div className="search-result-card__score">
                              <strong>{formatPercent(hit.score)}</strong>
                              <span>[{hit.score.toFixed(4)}]</span>
                            </div>
                            <span className={scoreBadgeClass(hit.score)}>
                              {relevanceLevel(hit.score)}
                            </span>
                          </div>
                        </div>
                        <h3>{hit.article.title}</h3>
                        <p className="search-result-card__authors">{hit.article.authors}</p>
                        <p>{hit.article.abstractText}</p>
                        <div className="score-component-breakdown">
                          <p>
                            <strong>Puntaje global de relevancia:</strong> combina los rankings
                            léxico y semántico mediante Reciprocal Rank Fusion (RRF); no es la suma
                            directa de los puntajes de componente.
                          </p>
                          <dl className="search-result-card__metrics">
                            <div>
                              <dt>Léxica (BM25)</dt>
                              <dd>
                                {formatPercent(hit.lexicalScore)} [{hit.lexicalScore.toFixed(4)}]
                              </dd>
                            </div>
                            <div>
                              <dt>Semántica (Vectorial IA-2)</dt>
                              <dd>
                                {formatPercent(hit.semanticScore)} [{hit.semanticScore.toFixed(4)}]
                              </dd>
                            </div>
                          </dl>
                          <small>
                            En esta implementación, el componente léxico usa BM25 (no TF-IDF) y el
                            semántico usa embeddings IA-2. Los niveles son orientativos: no
                            representan probabilidades ni umbrales de pertinencia validados.
                          </small>
                        </div>
                      </article>
                    ))}
                  </div>
                )}
                <PaginationControl
                  onPageChange={setPage}
                  onPageSizeChange={(newPageSize) => {
                    setPage(1);
                    setPageSize(newPageSize);
                  }}
                  page={page}
                  pageSize={pageSize}
                  totalElements={result.totalHits}
                  totalPages={totalPages}
                />
              </>
            )}
          </>
        )}

        {mode === 'exact' && (
          <>
            {traceError && <Alert title="La búsqueda exacta falló" tone="error">{traceError}</Alert>}
            {tracing && <p className="page-intro" role="status">Generando traza de {algorithmLabel(algorithm)}…</p>}
            {trace && !tracing && selectedArticleTrace && (
              <>
                <article className="card trace-summary" aria-live="polite">
                  <h3>Resumen para {selectedArticleId}</h3>
                  <p>
                    Coincidencias de {algorithmLabel(algorithm)}:{' '}
                    <strong>{matchCount}</strong>
                  </p>
                  <p>
                    Comparaciones de caracteres: <strong>No reportadas por el endpoint</strong>
                  </p>
                  <p>
                    Eventos disponibles para el algoritmo: <strong>{traceEvents.length}</strong>
                  </p>
                  <p>
                    Acuerdo entre los tres algoritmos:{' '}
                    <strong>{trace.algorithmsAgree ? 'Sí' : 'No'}</strong>
                  </p>
                </article>
                <AlgorithmTraceInspector
                  algorithm={algorithm}
                  events={traceEvents}
                  pattern={trace.pattern}
                  text={pText}
                />
              </>
            )}
            {trace && !tracing && !selectedArticleTrace && (
              <p className="empty-state">
                La API no devolvió una traza para el artículo seleccionado.
              </p>
            )}
          </>
        )}
      </section>
    </section>
  );
}
