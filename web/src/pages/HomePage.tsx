import { Alert } from '../components/Alert';
import type { BackendStatus } from './types';

interface HomePageProps {
  status: BackendStatus;
}

export function HomePage({ status }: HomePageProps) {
  const tone = status === 'available' ? 'success' : status === 'unavailable' ? 'error' : 'info';
  const message = {
    checking: 'Consultando el servicio de análisis.',
    available: 'El servicio de análisis está disponible.',
    unavailable: 'No se pudo conectar con el servicio de análisis.',
  }[status];

  return (
    <section className="page-stack" aria-labelledby="home-title">
      <div className="hero">
        <p className="eyebrow">Exploración científica</p>
        <h1 id="home-title">Análisis bibliométrico, con trazabilidad</h1>
        <p>
          Explora similitud entre artículos, agrupamientos jerárquicos y resultados de búsqueda
          desde un único espacio de trabajo.
        </p>
      </div>
      <Alert title="Estado de la API" tone={tone}>
        {message}
      </Alert>
      <div className="feature-grid">
        <article className="card">
          <h3>Similitud</h3>
          <p>Compara artículos con algoritmos léxicos y semánticos disponibles.</p>
        </article>
        <article className="card">
          <h3>Clustering</h3>
          <p>Inspecciona las fusiones del dendrograma y sus grupos resultantes.</p>
        </article>
        <article className="card">
          <h3>Búsqueda</h3>
          <p>Encuentra documentos ordenados por relevancia dentro del corpus.</p>
        </article>
      </div>
    </section>
  );
}
