import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from './api/client';
import { App } from './App';

vi.mock('./api/client', () => ({
  api: {
    health: vi.fn().mockResolvedValue({ status: 'ok' }),
    articles: vi.fn().mockResolvedValue([]),
  },
}));

describe('App', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.history.replaceState(null, '', '/');
  });

  afterEach(() => {
    cleanup();
  });

  it('initializes and displays the backend health status', async () => {
    render(<App />);

    expect(screen.getByRole('link', { name: 'Bibliometría' })).toBeInTheDocument();
    expect(await screen.findByText('API: Disponible')).toBeInTheDocument();
    expect(screen.getByText('El servicio de análisis está disponible.')).toBeInTheDocument();
    expect(api.health).toHaveBeenCalledWith(expect.any(AbortSignal));
  });

  it('navigates through the initial analysis screens', async () => {
    render(<App />);

    expect(screen.getByRole('link', { name: 'Inicio' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('link', { name: 'Clustering' }));

    expect(
      screen.getByRole('heading', { name: 'Clustering jerárquico aglomerativo' }),
    ).toBeInTheDocument();
  });
});
