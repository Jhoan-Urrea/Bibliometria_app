import { useEffect, useState } from 'react';
import { api } from '../../api/client';

export type HeaderApiStatus = 'checking' | 'online' | 'offline';

interface HeaderStatusProps {
  onStatusChange?: (status: HeaderApiStatus) => void;
}

const statusContent: Record<
  HeaderApiStatus,
  { className: string; label: string }
> = {
  checking: { className: 'status-dot--checking', label: 'API: Verificando...' },
  online: { className: 'status-dot--online', label: 'API: Disponible' },
  offline: { className: 'status-dot--offline', label: 'API: Sin conexión' },
};

export function HeaderStatus({ onStatusChange }: HeaderStatusProps) {
  const [status, setStatus] = useState<HeaderApiStatus>('checking');
  const content = statusContent[status];

  useEffect(() => {
    const controller = new AbortController();

    api.health(controller.signal).then(
      () => {
        setStatus('online');
        onStatusChange?.('online');
      },
      (error: unknown) => {
        if (!(error instanceof Error && error.name === 'AbortError')) {
          setStatus('offline');
          onStatusChange?.('offline');
        }
      },
    );

    return () => controller.abort();
  }, [onStatusChange]);

  return (
    <div className="header-status" role="status" aria-live="polite">
      <span className={`status-dot ${content.className}`} aria-hidden="true" />
      <span>{content.label}</span>
    </div>
  );
}
