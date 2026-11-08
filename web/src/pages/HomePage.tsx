import { Alert } from '../components/Alert';
import { InteractiveGlossary } from '../components/explaining/InteractiveGlossary';
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
      <header className="hero">
        <p className="eyebrow">Exploración científica</p>
        <h1 id="home-title">Análisis bibliométrico, con trazabilidad</h1>
        <p className="hero__subtitle">
          Un entorno de trabajo para explorar un corpus de 20 artículos científicos con métodos
          reproducibles de PLN, similitud y clustering.
        </p>
        <p>
          Sigue el recorrido desde la entrada textual, a través de los perfiles de procesamiento,
          hasta las métricas y agrupamientos producidos por los algoritmos.
        </p>
      </header>

      <Alert title="Estado de la API" tone={tone}>
        {message}
      </Alert>

      <section className="pipeline-section" aria-labelledby="pipeline-title">
        <div className="section-heading">
          <p className="eyebrow">Entrada → Proceso → Salida</p>
          <h2 id="pipeline-title">Dos perfiles, distintos objetivos analíticos</h2>
          <p className="page-intro">
            El texto normalizado conserva información de presentación; la tokenización produce
            estructuras para comparar contenido y calcular distancias.
          </p>
        </div>

        <div className="pipeline-diagram">
          <article className="card pipeline-card pipeline-card--text">
            <header className="pipeline-card__header">
              <span className="badge--profile">P-TEXTO</span>
              <span className="pipeline-card__kind">Texto normalizado</span>
            </header>
            <p>
              Parte del título y el abstract. Aplica Unicode NFKC, une palabras partidas por
              guion de línea y colapsa espacios; conserva mayúsculas y puntuación ordinaria.
            </p>
            <ol className="pipeline-steps">
              <li><span className="step-badge">N1</span> Normalización Unicode NFKC</li>
              <li><span className="step-badge">N2</span> Unión de palabras con guion de línea</li>
              <li><span className="step-badge">N3</span> Colapso de espacios</li>
            </ol>
            <p className="pipeline-card__output">
              <strong>Usos:</strong> búsqueda exacta KMP, Boyer–Moore y Rabin–Karp; entrada
              textual de Levenshtein. Los vectores IA-2 se consumen precalculados por documento.
            </p>
            <details className="pipeline-card__details">
              <summary>Ver perfil y supuestos</summary>
              <p>
                El endpoint de Sentence-BERT compara embeddings ya cargados. El preprocesamiento
                concreto usado para generarlos depende del artefacto de embeddings y no se
                reconstruye en el núcleo durante la consulta.
              </p>
            </details>
          </article>

          <div className="pipeline-connector" aria-hidden="true">
            <span>→</span>
          </div>

          <article className="card pipeline-card pipeline-card--tokens">
            <header className="pipeline-card__header">
              <span className="badge--profile">P-TOKENS</span>
              <span className="pipeline-card__kind">Estructuras lingüísticas</span>
            </header>
            <p>
              Se deriva de P-TEXTO: convierte a minúsculas, tokeniza letras y dígitos, conserva
              guiones internos y filtra stopwords para obtener la secuencia del documento.
            </p>
            <ol className="pipeline-steps">
              <li><span className="step-badge">T1</span> Conversión a minúsculas</li>
              <li><span className="step-badge">T2</span> Tokenización y normalización de tokens</li>
              <li><span className="step-badge">T3</span> Filtrado de stopwords</li>
            </ol>
            <p className="pipeline-card__output">
              <strong>Salida:</strong> <code>Tok(d)</code> (secuencia), <code>Set(d)</code>{' '}
              (tokens únicos) y <code>f(t,d)</code> (frecuencias). Se usan en LCS, TF-IDF,
              Jaccard, FastText y clustering aglomerativo.
            </p>
            <p className="pipeline-card__note">
              Levenshtein usa el texto normalizado P-TEXTO en la implementación actual; LCS,
              TF-IDF y Jaccard operan sobre tokens o sus agregados.
            </p>
          </article>
        </div>
      </section>

      <InteractiveGlossary />

      <section className="navigation-section" aria-labelledby="navigation-title">
        <div className="section-heading">
          <p className="eyebrow">Continuar análisis</p>
          <h2 id="navigation-title">Herramientas del entorno</h2>
        </div>
        <div className="feature-grid">
          <a className="card navigation-card" href="#similitud">
            <h3>Similitud</h3>
            <p>Compara artículos con algoritmos léxicos y semánticos disponibles.</p>
            <span className="navigation-card__action">Abrir similitud →</span>
          </a>
          <a className="card navigation-card" href="#clustering">
            <h3>Clustering</h3>
            <p>Inspecciona las fusiones del dendrograma y los grupos resultantes.</p>
            <span className="navigation-card__action">Abrir clustering →</span>
          </a>
          <a className="card navigation-card" href="#busqueda">
            <h3>Búsqueda</h3>
            <p>Encuentra documentos por relevancia o coincidencia exacta.</p>
            <span className="navigation-card__action">Abrir búsqueda →</span>
          </a>
        </div>
      </section>
    </section>
  );
}
