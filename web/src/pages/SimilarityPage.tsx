import { useEffect, useState, type FormEvent } from 'react';
import { api, type Article, type SimilarityRequest, type SimilarityResponse } from '../api/client';
import { errorMessage } from '../api/errorMessage';
import { Alert } from '../components/Alert';
import { Button } from '../components/Button';
import { Select } from '../components/Select';

const algorithmOptions = [
  { value: 'levenshtein', label: 'Levenshtein' },
  { value: 'lcs', label: 'Subsecuencia común más larga' },
  { value: 'tfidf', label: 'TF-IDF' },
  { value: 'jaccard', label: 'Jaccard' },
  { value: 'fasttext', label: 'FastText' },
  { value: 'sentence-bert', label: 'Sentence-BERT' },
] as const;

function isSimilarityAlgorithm(value: string): value is SimilarityRequest['algorithm'] {
  return algorithmOptions.some((option) => option.value === value);
}

export function SimilarityPage() {
  const [articles, setArticles] = useState<Article[]>([]);
  const [articleAId, setArticleAId] = useState('');
  const [articleBId, setArticleBId] = useState('');
  const [algorithm, setAlgorithm] = useState<SimilarityRequest['algorithm']>('levenshtein');
  const [result, setResult] = useState<SimilarityResponse | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    api.articles(controller.signal)
      .then((loaded) => {
        setArticles(loaded);
        setArticleAId(loaded[0]?.id ?? '');
        setArticleBId(loaded[1]?.id ?? loaded[0]?.id ?? '');
      })
      .catch((cause: unknown) => {
        if (!(cause instanceof Error && cause.name === 'AbortError')) setError(errorMessage(cause));
      });
    return () => controller.abort();
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setResult(null);
    try {
      const response = await api.similarity({
        articleAId,
        articleBId,
        algorithm,
      });
      setResult(response);
    } catch (cause: unknown) {
      setError(errorMessage(cause));
    }
  }

  return (
    <section className="page-stack" aria-labelledby="similarity-title">
      <h1 id="similarity-title">Similitud entre artículos</h1>
      <p className="page-intro">Selecciona dos documentos y el algoritmo que deseas comparar.</p>
      <form className="card form-grid" onSubmit={submit}>
        <Select
          label="Artículo A"
          name="articleA"
          onChange={(event) => setArticleAId(event.currentTarget.value)}
          options={articles.map((article) => ({ value: article.id, label: `${article.id} — ${article.title}` }))}
          required
          value={articleAId}
        />
        <Select
          label="Artículo B"
          name="articleB"
          onChange={(event) => setArticleBId(event.currentTarget.value)}
          options={articles.map((article) => ({ value: article.id, label: `${article.id} — ${article.title}` }))}
          required
          value={articleBId}
        />
        <Select
          label="Algoritmo"
          name="algorithm"
          onChange={(event) => {
            const value = event.currentTarget.value;
            if (isSimilarityAlgorithm(value)) setAlgorithm(value);
          }}
          options={algorithmOptions}
          value={algorithm}
        />
        <Button type="submit" disabled={!articles.length}>Calcular similitud</Button>
      </form>
      {error && <Alert title="No se pudo calcular" tone="error">{error}</Alert>}
      {result && (
        <article className="card result-card" aria-live="polite">
          <h3>Resultado</h3>
          <p>Similitud normalizada: <strong>{result.normalizedScore.toFixed(4)}</strong></p>
          <p>Puntuación original: {result.rawScore.toFixed(4)}</p>
          <p>Algoritmo: {result.algorithm}</p>
        </article>
      )}
    </section>
  );
}
