import { useEffect, useState } from 'react';

type BackendStatus = 'checking' | 'available' | 'unavailable';

export function App() {
  const [status, setStatus] = useState<BackendStatus>('checking');

  useEffect(() => {
    const controller = new AbortController();

    fetch('/api/health', { signal: controller.signal })
      .then((response) => {
        if (!response.ok) {
          throw new Error('Backend health check failed');
        }
        setStatus('available');
      })
      .catch((error: unknown) => {
        if (error instanceof Error && error.name === 'AbortError') {
          return;
        }
        setStatus('unavailable');
      });

    return () => controller.abort();
  }, []);

  const statusMessage = {
    checking: 'Consultando el backend...',
    available: 'Backend disponible',
    unavailable: 'Backend no disponible',
  }[status];

  return (
    <main>
      <h1>Bibliometría</h1>
      <p role="status">{statusMessage}</p>
    </main>
  );
}