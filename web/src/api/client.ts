import type { components, operations } from './schema';

export type Article = components['schemas']['Article'];
export type SimilarityRequest = components['schemas']['SimilarityRequest'];
export type SimilarityResponse = components['schemas']['SimilarityResponse'];
export type ClusteringRequest = components['schemas']['ClusteringRequest'];
export type ClusteringResponse = components['schemas']['ClusteringResponse'];
export type SearchRequest = components['schemas']['SearchRequest'];
export type SearchResponse = components['schemas']['SearchResponse'];
export type SearchTraceResponse = components['schemas']['SearchTraceResponse'];
export type Problem = components['schemas']['Problem'];

export class ApiError extends Error {
  readonly problem: Problem;

  constructor(problem: Problem) {
    super(problem.detail ?? problem.title);
    this.name = 'ApiError';
    this.problem = problem;
  }
}

type JsonResponse<Operation> =
  Operation extends { responses: infer Responses }
    ? Responses extends { 200: { content: { 'application/json': infer Body } } }
      ? Body
      : never
    : never;

async function request<T>(
  path: string,
  method: 'GET' | 'POST',
  body?: unknown,
  signal?: AbortSignal,
): Promise<T> {
  const headers = new Headers();
  if (body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal,
  });

  const contentType = response.headers.get('content-type') ?? '';
  if (!response.ok) {
    if (contentType.includes('application/problem+json')) {
      const problem: Problem = await response.json();
      throw new ApiError(problem);
    }

    throw new ApiError({
      type: 'about:blank',
      title: 'Error de comunicación',
      status: response.status,
      detail: 'El servidor no devolvió un error Problem Details válido.',
      correlationId: response.headers.get('X-Correlation-ID') ?? 'no-disponible',
    });
  }

  if (!contentType.includes('application/json')) {
    throw new Error('La API devolvió una respuesta que no es JSON.');
  }

  return (await response.json()) as T;
}

export const api = {
  health: (signal?: AbortSignal) =>
    request<JsonResponse<operations['getHealth']>>('/api/health', 'GET', undefined, signal),
  articles: (signal?: AbortSignal) =>
    request<JsonResponse<operations['listArticles']>>('/api/articles', 'GET', undefined, signal),
  similarity: (input: SimilarityRequest, signal?: AbortSignal) =>
    request<JsonResponse<operations['calculateSimilarity']>>(
      '/api/similarity',
      'POST',
      input,
      signal,
    ),
  clustering: (input: ClusteringRequest, signal?: AbortSignal) =>
    request<JsonResponse<operations['clusterCorpus']>>(
      '/api/clustering',
      'POST',
      input,
      signal,
    ),
  search: (input: SearchRequest, signal?: AbortSignal) =>
    request<JsonResponse<operations['searchArticles']>>('/api/search', 'POST', input, signal),
  searchTrace: (pattern: string, articleId?: string, signal?: AbortSignal) => {
    const query = new URLSearchParams({ pattern });
    if (articleId) query.set('articleId', articleId);
    return request<JsonResponse<operations['traceExactSearch']>>(
      `/api/search/trace?${query}`,
      'GET',
      undefined,
      signal,
    );
  },
};
