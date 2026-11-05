import { useEffect, useState } from 'react';
import { api } from './api/client';
import { AboutPage } from './pages/AboutPage';
import { ClusteringPage } from './pages/ClusteringPage';
import { HomePage } from './pages/HomePage';
import { PerformancePage } from './pages/PerformancePage';
import { SearchPage } from './pages/SearchPage';
import { SimilarityPage } from './pages/SimilarityPage';
import type { BackendStatus } from './pages/types';

const navigation = [
  { id: 'inicio', label: 'Inicio' },
  { id: 'similitud', label: 'Similitud' },
  { id: 'clustering', label: 'Clustering' },
  { id: 'busqueda', label: 'Búsqueda' },
  { id: 'rendimiento', label: 'Rendimiento' },
  { id: 'acerca-de', label: 'Acerca de' },
] as const;

type PageId = (typeof navigation)[number]['id'];

function isPageId(value: string): value is PageId {
  return navigation.some((page) => page.id === value);
}

function getPageFromHash(): PageId {
  const hash = window.location.hash.slice(1);
  return isPageId(hash) ? hash : 'inicio';
}

export function App() {
  const [status, setStatus] = useState<BackendStatus>('checking');
  const [page, setPage] = useState<PageId>(() => getPageFromHash());

  useEffect(() => {
    const controller = new AbortController();
    api.health(controller.signal)
      .then(() => setStatus('available'))
      .catch((error: unknown) => {
        if (!(error instanceof Error && error.name === 'AbortError')) setStatus('unavailable');
      });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    const updatePage = () => setPage(getPageFromHash());
    window.addEventListener('hashchange', updatePage);
    return () => window.removeEventListener('hashchange', updatePage);
  }, []);

  function renderPage() {
    switch (page) {
      case 'inicio':
        return <HomePage status={status} />;
      case 'similitud':
        return <SimilarityPage />;
      case 'clustering':
        return <ClusteringPage />;
      case 'busqueda':
        return <SearchPage />;
      case 'rendimiento':
        return <PerformancePage />;
      case 'acerca-de':
        return <AboutPage />;
    }
  }

  return (
    <div className="app-shell">
      <a className="skip-link" href="#contenido">Saltar al contenido principal</a>
      <header className="site-header">
        <div className="site-header__inner">
          <a className="brand" href="#inicio" onClick={() => setPage('inicio')}>
            <span className="brand__mark" aria-hidden="true">B</span>
            <span>Bibliometría</span>
          </a>
          <nav aria-label="Navegación principal" className="primary-nav">
            {navigation.map((item) => (
              <a
                aria-current={page === item.id ? 'page' : undefined}
                href={`#${item.id}`}
                key={item.id}
                onClick={() => setPage(item.id)}
              >
                {item.label}
              </a>
            ))}
          </nav>
        </div>
      </header>
      <main className="main-content" id="contenido" tabIndex={-1}>
        {renderPage()}
      </main>
      <footer className="site-footer">
        <span>Bibliometría</span>
        <span>Exploración reproducible del corpus</span>
      </footer>
    </div>
  );
}
