import { useState } from 'react';
import { DataTable, type Column } from '../components/DataTable';
import { MathExplainerCard } from '../components/explaining/MathExplainerCard';
import {
  BenchmarkDashboard,
  type BenchmarkDashboardProps,
} from '../components/performance/BenchmarkDashboard';

type Scenario = BenchmarkDashboardProps['scenario'];

interface ComplexityEntry {
  algorithm: string;
  category: string;
  time: string;
  space: string;
  notes: string;
}

const complexity: ComplexityEntry[] = [
  {
    algorithm: 'KMP',
    category: 'String Matching',
    time: 'O(n + m)',
    space: 'O(m)',
    notes: 'Preprocesamiento LPS O(m); búsqueda lineal O(n).',
  },
  {
    algorithm: 'Boyer–Moore',
    category: 'String Matching',
    time: 'Mejor: sublineal; peor: O(n·m)',
    space: 'O(m + |Σ|)',
    notes: 'Esta implementación usa una tabla de últimas apariciones de tamaño 65.536.',
  },
  {
    algorithm: 'Rabin–Karp',
    category: 'String Matching',
    time: 'Promedio: O(n + m); peor: O(n·m)',
    space: 'O(1) adicional',
    notes: 'El peor caso ocurre cuando muchas ventanas requieren verificación por colisión/hash igual.',
  },
  {
    algorithm: 'Levenshtein',
    category: 'Programación dinámica',
    time: 'O(n·m)',
    space: 'O(min(n,m))',
    notes: 'La implementación conserva dos filas de la matriz DP.',
  },
  {
    algorithm: 'LCS',
    category: 'Programación dinámica',
    time: 'O(n·m)',
    space: 'O(n·m)',
    notes: 'La tabla se conserva para reconstruir una subsecuencia común.',
  },
  {
    algorithm: 'TF-IDF + Coseno',
    category: 'Espacio vectorial',
    time: 'O(|Vₐ| + |Vᵦ|)',
    space: 'O(|Vₐ| + |Vᵦ|)',
    notes: 'Operación dispersa sobre los términos presentes en cada documento.',
  },
  {
    algorithm: 'Jaccard',
    category: 'Teoría de conjuntos',
    time: 'O(|A| + |B|)',
    space: 'O(1) adicional',
    notes: 'Supone conjuntos hash con operaciones promedio O(1).',
  },
  {
    algorithm: 'FastText (IA-1)',
    category: 'Espacio vectorial / IA',
    time: 'O(d)',
    space: 'O(1) adicional',
    notes: 'Comparación de vectores densos precalculados; d = 300.',
  },
  {
    algorithm: 'Sentence-BERT (IA-2)',
    category: 'Espacio vectorial / IA',
    time: 'O(d)',
    space: 'O(1) adicional',
    notes: 'Comparación de embeddings normalizados; d = 384.',
  },
  {
    algorithm: 'Clustering aglomerativo',
    category: 'Clustering jerárquico',
    time: 'O(N² log N)–O(N³)',
    space: 'O(N²)',
    notes: 'Depende de la estrategia de enlace y de la estructura de distancias.',
  },
];

const complexityColumns: Column<ComplexityEntry>[] = [
  { key: 'algorithm', header: 'Algoritmo', render: (row) => row.algorithm },
  { key: 'category', header: 'Familia', render: (row) => row.category },
  { key: 'time', header: 'Complejidad temporal', render: (row) => <code>{row.time}</code> },
  { key: 'space', header: 'Complejidad espacial', render: (row) => <code>{row.space}</code> },
  { key: 'notes', header: 'Supuestos / detalle', render: (row) => row.notes },
];

export function PerformancePage() {
  const [selectedScenario, setSelectedScenario] = useState<Scenario>('E1');

  return (
    <section className="page-stack performance-workspace" aria-labelledby="performance-title">
      <header className="hero">
        <p className="eyebrow">Entrada → Proceso → Salida</p>
        <h1 id="performance-title">Rendimiento algorítmico</h1>
        <p>
          Contrasta cotas asintóticas con las mediciones del benchmark versionado. Los datos
          empíricos se muestran solo cuando el informe registra valores observados.
        </p>
      </header>

      <section className="performance-section" aria-labelledby="performance-output-title">
        <div className="section-heading">
          <p className="eyebrow">01 · Salida</p>
          <h2 id="performance-output-title">Resultados empíricos del benchmark</h2>
          <p className="page-intro">
            Selecciona un escenario junto a las tablas. Los resultados se cargan desde un artefacto
            estático versionado; esta vista no inicia mediciones dinámicas ni simula datos.
          </p>
        </div>
        <BenchmarkDashboard
          onScenarioChange={setSelectedScenario}
          scenario={selectedScenario}
        />
      </section>

      <details className="performance-reference">
        <summary>
          02 · Proceso — Matriz Big-O y protocolo JVM (referencia)
        </summary>
        <section className="performance-section" aria-labelledby="performance-process-title">
          <div className="section-heading">
            <h2 id="performance-process-title">Matriz de complejidad asintótica</h2>
            <p className="page-intro">
              Las cotas describen el crecimiento teórico bajo los supuestos indicados; no son
              mediciones de tiempo ni comparaciones empíricas entre implementaciones.
            </p>
          </div>
          <DataTable
            caption="Complejidad teórica de los algoritmos implementados"
            columns={complexityColumns}
            rows={complexity}
            rowKey={(row) => row.algorithm}
          />
          <MathExplainerCard
            category="Protocolo de medición"
            caveat="El informe versionado dispone de 30 muestras por caso, no de 500. Los tiempos dependen del entorno registrado; comparaciones por carácter son una métrica más portable, pero no representan por sí solas todo el costo de ejecución."
            formula={
              <>
                T(n) = mediana(t₁, …, tᵣ)
                <br />
                IQR = Q₃ − Q₁
                <br />
                Comparaciones normalizadas = C / n
              </>
            }
            interpretation="El calentamiento reduce la influencia inicial de compilación JIT; mediana e IQR resumen las muestras observadas. C/n ayuda a contrastar el trabajo de comparación independiente de la velocidad de CPU."
            notation={[
              { symbol: 'JDK', meaning: 'OpenJDK 21.0.12.1, según el informe de benchmark versionado.' },
              { symbol: 'Warmup', meaning: '2.000 ejecuciones descartadas por caso y algoritmo.' },
              { symbol: 'r', meaning: '30 muestras medidas por combinación de escenario y algoritmo.' },
              { symbol: 'C/n', meaning: 'Comparaciones de caracteres divididas por longitud del texto.' },
            ]}
            purpose="Permite interpretar las mediciones considerando compilación JIT, dispersión y escala de entrada."
            title="Mediana, IQR y comparaciones por carácter"
          />
        </section>
      </details>
    </section>
  );
}
