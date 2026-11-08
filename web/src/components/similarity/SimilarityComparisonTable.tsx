import { useEffect, useState } from 'react';
import { api, type SimilarityRequest, type SimilarityResponse } from '../../api/client';
import { errorMessage } from '../../api/errorMessage';

export interface SimilarityComparisonTableProps {
  articleAId: string;
  articleBId: string;
}

type AlgorithmDefinition = {
  value: SimilarityRequest['algorithm'];
  label: string;
  category: string;
  rawScoreMeaning: string;
};

const algorithms: AlgorithmDefinition[] = [
  {
    value: 'levenshtein',
    label: 'Levenshtein',
    category: 'Prog. Dinámica',
    rawScoreMeaning: 'operaciones de edición sobre puntos de código',
  },
  {
    value: 'lcs',
    label: 'LCS',
    category: 'Prog. Dinámica',
    rawScoreMeaning: 'tokens de la subsecuencia común ordenada',
  },
  {
    value: 'tfidf',
    label: 'TF-IDF + Coseno',
    category: 'Espacio Vectorial',
    rawScoreMeaning: 'coseno entre vectores TF-IDF',
  },
  {
    value: 'jaccard',
    label: 'Jaccard',
    category: 'Teoría de Conjuntos',
    rawScoreMeaning: 'coeficiente |A ∩ B| / |A ∪ B|; no es un conteo',
  },
  {
    value: 'fasttext',
    label: 'FastText (IA-1)',
    category: 'Semántica IA',
    rawScoreMeaning: 'coseno entre vectores subléxicos (r ∈ [-1, 1])',
  },
  {
    value: 'sentence-bert',
    label: 'Sentence-BERT (IA-2)',
    category: 'Transformer Contextual',
    rawScoreMeaning: 'coseno entre embeddings contextuales',
  },
];

type AlgorithmState =
  | { status: 'loading' }
  | { status: 'success'; result: SimilarityResponse }
  | { status: 'error'; message: string };

interface ComparisonResults {
  articleAId: string;
  articleBId: string;
  algorithms: AlgorithmState[];
}

function formatScore(score: number): string {
  return new Intl.NumberFormat('es-CO', {
    style: 'percent',
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }).format(score);
}

function scoreBadgeClass(score: number): string {
  if (score >= 0.75) return 'badge--qualitative-high';
  if (score >= 0.45) return 'badge--qualitative-medium';
  return 'badge--qualitative-low';
}

function qualitativeLabel(score: number): string {
  if (score >= 0.75) return 'Alta Similitud';
  if (score >= 0.45) return 'Similitud Media';
  return 'Baja Similitud';
}

function formatRawScore(score: number, algorithm: SimilarityRequest['algorithm']): string {
  if (algorithm === 'levenshtein' || algorithm === 'lcs') {
    return new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 }).format(score);
  }
  return score.toFixed(4);
}

export function SimilarityComparisonTable({
  articleAId,
  articleBId,
}: SimilarityComparisonTableProps) {
  const [comparison, setComparison] = useState<ComparisonResults | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;

    const requests = algorithms.map(({ value }) =>
      api.similarity(
        { articleAId, articleBId, algorithm: value },
        controller.signal,
      ),
    );

    Promise.allSettled(requests).then((settledResults) => {
      if (!active) return;

      setComparison({
        articleAId,
        articleBId,
        algorithms: settledResults.map((settled): AlgorithmState =>
          settled.status === 'fulfilled'
            ? { status: 'success', result: settled.value }
            : { status: 'error', message: errorMessage(settled.reason) },
        ),
      });
    });

    return () => {
      active = false;
      controller.abort();
    };
  }, [articleAId, articleBId]);

  const currentResults =
    comparison?.articleAId === articleAId && comparison.articleBId === articleBId
      ? comparison.algorithms
      : null;

  return (
    <div className="table-scroll">
      <table className="data-table">
        <caption>
          Comparación de similitud entre {articleAId} y {articleBId}
        </caption>
        <thead>
          <tr>
            <th scope="col">Algoritmo</th>
            <th scope="col">Categoría</th>
            <th scope="col">Puntaje normalizado [0, 1]</th>
            <th scope="col">Puntaje crudo</th>
            <th scope="col">Estado</th>
          </tr>
        </thead>
        <tbody>
          {algorithms.map((algorithm, index) => {
            const state = currentResults?.[index] ?? { status: 'loading' as const };

            return (
              <tr key={algorithm.value}>
                <th scope="row">{algorithm.label}</th>
                <td>{algorithm.category}</td>
                {state.status === 'success' ? (
                  <>
                    <td>
                      <div className="similarity-score">
                        <div className="score-dual-display">
                          <strong>{formatScore(state.result.normalizedScore)}</strong>
                          <span>[{state.result.normalizedScore.toFixed(4)}]</span>
                        </div>
                        <span className={scoreBadgeClass(state.result.normalizedScore)}>
                          {qualitativeLabel(state.result.normalizedScore)}
                        </span>
                      </div>
                    </td>
                    <td>
                      <span className="raw-score">
                        {formatRawScore(state.result.rawScore, algorithm.value)}
                      </span>
                      <small className="raw-score__meaning">{algorithm.rawScoreMeaning}</small>
                    </td>
                    <td>
                      <span aria-label="Cálculo completado" role="img">✓</span>
                      {' '}Calculado
                    </td>
                  </>
                ) : state.status === 'error' ? (
                  <>
                    <td>—</td>
                    <td>—</td>
                    <td>
                      <span
                        aria-label={`Error: ${state.message}`}
                        className="badge--low"
                        title={state.message}
                      >
                        ⚠ Error
                      </span>
                    </td>
                  </>
                ) : (
                  <>
                    <td aria-label="Calculando">—</td>
                    <td>—</td>
                    <td>
                      <progress aria-label={`Calculando ${algorithm.label}`} />
                      {' '}Calculando
                    </td>
                  </>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
