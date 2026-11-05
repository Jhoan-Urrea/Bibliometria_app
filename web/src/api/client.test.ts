import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api } from './client';

describe('API client', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('converts RFC 7807 responses into typed API errors', async () => {
    const problem = {
      type: 'about:blank',
      title: 'Análisis no procesable',
      status: 422,
      detail: 'El modelo semántico no está disponible.',
      correlationId: '4a9fbfb9-140e-4d19-8fbd-dfbf23d1b14a',
    };
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(problem), {
          status: 422,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
      ),
    );

    const request = api.similarity({
      articleAId: 'A01',
      articleBId: 'A02',
      algorithm: 'sentence-bert',
    });

    await expect(request).rejects.toBeInstanceOf(ApiError);
    await expect(request).rejects.toMatchObject({ problem });
  });
});
