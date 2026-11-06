import { Alert } from '../components/Alert';

export function PerformancePage() {
  return (
    <section className="page-stack" aria-labelledby="performance-title">
      <h1 id="performance-title">Rendimiento</h1>
      <p className="page-intro">
        Espacio preparado para visualizar métricas de los benchmarks reproducibles del proyecto.
      </p>
      <Alert title="Datos no disponibles" tone="info">
        La API aún no publica resultados de rendimiento. No se muestran valores estimados como si
        fueran mediciones.
      </Alert>
    </section>
  );
}
