import { afterEach, describe, expect, it, vi } from 'vitest';
import { render } from '@testing-library/react';
import { App } from './App';

describe('App', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('initializes and displays the backend health status', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true }));

    const { getByRole, findByText } = render(<App />);

    expect(getByRole('heading', { name: 'Bibliometría' })).not.toBeNull();
    expect(await findByText('Backend disponible')).not.toBeNull();
    expect(fetch).toHaveBeenCalledWith('/api/health', expect.any(Object));
  });
});