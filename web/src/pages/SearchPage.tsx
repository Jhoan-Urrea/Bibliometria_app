import { useState, type FormEvent } from 'react';
import type { SearchResponse, SearchTraceResponse } from '../api/client';
import { api } from '../api/client';
import { errorMessage } from '../api/errorMessage';
import { Alert } from '../components/Alert';
import { Button } from '../components/Button';
import { DataTable } from '../components/DataTable';

type SearchHit = SearchResponse['results'][number];

export function SearchPage() {
  const [query, setQuery] = useState('');
  const [result, setResult] = useState<SearchResponse | null>(null);
  const [error, setError] = useState('');
  const [pattern, setPattern] = useState('');
  const [trace, setTrace] = useState<SearchTraceResponse | null>(null);
  const [articleTraces, setArticleTraces] = useState<
    Record<string, SearchTraceResponse['articles'][number]>
  >({});
  const [traceError, setTraceError] = useState('');
  const [loadingArticleId, setLoadingArticleId] = useState('');

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    try {
      setResult(await api.search({ query, page: 1, pageSize: 10 }));
    } catch (cause: unknown) {
      setError(errorMessage(cause));
    }
  }

  async function submitTrace(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setTraceError('');
    setTrace(null);
    setArticleTraces({});
    setLoadingArticleId('');
    try {
      setTrace(await api.searchTrace(pattern));
    } catch (cause: unknown) {
      setTraceError(errorMessage(cause));
    }
  }

  async function loadArticleTrace(articleId: string) {
    setTraceError('');
    setLoadingArticleId(articleId);
    try {
      const response = await api.searchTrace(pattern, articleId);
      const article = response.articles[0];
      if (article) {
        setArticleTraces((previous) => ({ ...previous, [articleId]: article }));
      }
    } catch (cause: unknown) {
      setTraceError(errorMessage(cause));
    } finally {
      setLoadingArticleId('');
    }
  }

  const matchedArticles = trace?.articles.filter(
    (article) => article.positionsByAlgorithm.KMP.length > 0,
  );

  return (
    <section className="page-stack" aria-labelledby="search-title">
      <h1 id="search-title">Búsqueda bibliográfica</h1>
      <form className="card form-grid form-grid--compact" onSubmit={submit}>
        <label className="field" htmlFor="search-query">
          <span className="field__label">Términos de búsqueda</span>
          <input
            className="field__control"
            id="search-query"
            maxLength={200}
            onChange={(event) => setQuery(event.currentTarget.value)}
            required
            type="search"
            value={query}
          />
        </label>
        <Button type="submit">Buscar artículos</Button>
      </form>
      {error && <Alert title="La búsqueda falló" tone="error">{error}</Alert>}
      {result && (
        <DataTable<SearchHit>
          caption={`Resultados de búsqueda: ${result.totalHits} artículos`}
          columns={[
            { key: 'id', header: 'ID', render: (hit) => hit.article.id },
            { key: 'title', header: 'Título', render: (hit) => hit.article.title },
            { key: 'score', header: 'Puntuación', render: (hit) => hit.score.toFixed(3) },
          ]}
          rows={result.results}
          rowKey={(hit) => hit.article.id}
        />
      )}
      <section className="page-stack" aria-labelledby="trace-title">
        <h2 id="trace-title">Búsqueda exacta y trazas</h2>
        <p className="page-intro">
          Busca un patrón exacto en el texto normalizado y compara KMP, Boyer-Moore y Rabin-Karp.
        </p>
        <form className="card form-grid form-grid--compact" onSubmit={submitTrace}>
          <label className="field" htmlFor="trace-pattern">
            <span className="field__label">Patrón exacto</span>
            <input
              className="field__control"
              id="trace-pattern"
              maxLength={200}
              minLength={1}
              onChange={(event) => setPattern(event.currentTarget.value)}
              required
              type="search"
              value={pattern}
            />
          </label>
          <Button type="submit">Buscar patrón y ver trazas</Button>
        </form>
        {traceError && <Alert title="La búsqueda exacta falló" tone="error">{traceError}</Alert>}
        {trace && (
          <article className="card result-card" aria-live="polite">
            <h3>Traza para “{trace.pattern}”</h3>
            <p>
              Los algoritmos {trace.algorithmsAgree ? 'coinciden' : 'no coinciden'}.
              {' '}{matchedArticles?.length ?? 0} artículos con coincidencias.
            </p>
            {matchedArticles?.map((article) => {
              const articleTrace = articleTraces[article.articleId];
              return (
                <section key={article.articleId} aria-labelledby={`trace-${article.articleId}`}>
                  <h4 id={`trace-${article.articleId}`}>{article.articleId}</h4>
                  <p>
                    KMP: {article.positionsByAlgorithm.KMP.join(', ')}; Boyer-Moore:{' '}
                    {article.positionsByAlgorithm['Boyer-Moore'].join(', ')}; Rabin-Karp:{' '}
                    {article.positionsByAlgorithm['Rabin-Karp'].join(', ')}
                  </p>
                  <ul>
                    {article.fragments.map((fragment) => (
                      <li key={`${fragment.start}-${fragment.highlightStart}`}>
                        {fragment.text.slice(0, fragment.highlightStart)}
                        <mark>
                          {fragment.text.slice(fragment.highlightStart, fragment.highlightEnd)}
                        </mark>
                        {fragment.text.slice(fragment.highlightEnd)}
                      </li>
                    ))}
                  </ul>
                  <Button
                    disabled={loadingArticleId !== '' && loadingArticleId !== article.articleId}
                    onClick={() => loadArticleTrace(article.articleId)}
                    type="button"
                  >
                    {loadingArticleId === article.articleId
                      ? `Cargando traza de ${article.articleId}`
                      : `Ver traza de ${article.articleId}`}
                  </Button>
                  {articleTrace && (
                    <details>
                      <summary>Pasos de los algoritmos ({articleTrace.events.length})</summary>
                      <ol>
                        {articleTrace.events.map((event, index) => (
                          <li key={`${event.algorithm}-${event.step}-${index}`}>
                            <strong>{event.algorithm}</strong> · {event.step}:{' '}
                            <code>{JSON.stringify(event.data)}</code>
                          </li>
                        ))}
                      </ol>
                    </details>
                  )}
                </section>
              );
            })}
          </article>
        )}
      </section>
    </section>
  );
}
