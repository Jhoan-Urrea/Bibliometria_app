import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render } from '@testing-library/react';
import { App } from './App';

describe('App', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('initializes and displays the backend health status', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: async () => ({ status: 'UP' }),
    }));

    const { getByRole, findByText } = render(<App />);

    expect(getByRole('link', { name: 'Bibliometría' })).not.toBeNull();
    expect(await findByText('El servicio de análisis está disponible.')).not.toBeNull();
    expect(fetch).toHaveBeenCalledWith('/api/health', expect.any(Object));
  });

  it('navigates through the initial analysis screens', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: async () => ({ status: 'UP' }),
    }));

    const { getByRole } = render(<App />);
    expect(getByRole('link', { name: 'Inicio' })).toBeTruthy();
    fireEvent.click(getByRole('link', { name: 'Clustering' }));

    expect(getByRole('heading', { name: 'Clustering jerárquico' })).toBeTruthy();
  });
});