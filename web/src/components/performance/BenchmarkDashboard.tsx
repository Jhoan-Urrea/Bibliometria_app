import benchmarkData from '../../assets/benchmarks.json';

export interface BenchmarkDashboardProps {
  scenario: 'E1' | 'E2' | 'E3';
  onScenarioChange: (scenario: BenchmarkDashboardProps['scenario']) => void;
}

interface BenchmarkMetric {
  algorithm: string;
  category: string;
  timeMedianMs: number | null;
  timeIqrMs: number | null;
  comparisonsPerChar: number | null;
  comparisonsPerCharRange?: number[];
  comparisons?: number;
  memoryAllocatedMb: number | null;
}

interface BenchmarkCase {
  id: string;
  label: string;
  pattern: string;
  patternLength: number;
  metrics: BenchmarkMetric[];
}

type ScenarioData = {
  title: string;
  description: string;
  inputs: {
    metrics?: BenchmarkMetric[];
    cases?: BenchmarkCase[];
    measurement?: {
      textLength: number;
      patternLength: number;
      alphabetSize: number;
      metrics: BenchmarkMetric[];
    };
    observedTextLengths?: number[];
    patternLengths?: number[];
    alphabetSizes?: number[];
  };
};

const data: {
  schemaVersion: number;
  source: string;
  metadata: {
    jdkVersion: string;
    warmupIterations: number;
    measuredRuns: number;
    timestamp: string | null;
    environment: string;
    notes: string[];
  };
  scenarios: Record<'E1' | 'E2' | 'E3', ScenarioData>;
} = benchmarkData;

const scenarioOptions: Array<{
  id: BenchmarkDashboardProps['scenario'];
  label: string;
}> = [
  { id: 'E1', label: 'E1 · Dominio real' },
  { id: 'E2', label: 'E2 · Escala sintética' },
  { id: 'E3', label: 'E3 · Adversarial' },
];

if (
  data.schemaVersion !== 1
  || !data.scenarios.E1
  || !data.scenarios.E2
  || !data.scenarios.E3
) {
  throw new Error('El artefacto benchmarks.json no cumple la versión de esquema esperada.');
}

function formatNumber(value: number, digits = 3): string {
  return new Intl.NumberFormat('es-CO', {
    maximumFractionDigits: digits,
    minimumFractionDigits: 0,
  }).format(value);
}

function absentMetric(label: string, scenario: BenchmarkDashboardProps['scenario']) {
  return (
    <span className="metric-badge metric-badge--absent">
      {label} en {scenario}
    </span>
  );
}

function metricRows(
  metrics: BenchmarkMetric[],
  scenario: BenchmarkDashboardProps['scenario'],
  groupLabel?: string,
) {
  const measuredTimes = metrics
    .map((metric) => metric.timeMedianMs)
    .filter((value): value is number => value !== null);
  const bestTime = measuredTimes.length ? Math.min(...measuredTimes) : undefined;
  const worstTime = measuredTimes.length ? Math.max(...measuredTimes) : undefined;
  const measuredComparisons = metrics
    .map((metric) => metric.comparisonsPerChar)
    .filter((value): value is number => value !== null);
  const bestComparisons = measuredComparisons.length
    ? Math.min(...measuredComparisons)
    : undefined;
  const worstComparisons = measuredComparisons.length
    ? Math.max(...measuredComparisons)
    : undefined;
  const maximumComparisonRange = Math.max(
    0,
    ...metrics.map((metric) => metric.comparisonsPerCharRange?.[1] ?? 0),
  );

  return metrics.map((metric) => {
    const timeValue = metric.timeMedianMs;
    const comparisonValue = metric.comparisonsPerChar;
    const timeAvailable = timeValue !== null && bestTime !== undefined && worstTime !== undefined;
    const comparisonAvailable =
      comparisonValue !== null
      && bestComparisons !== undefined
      && worstComparisons !== undefined;
    const timeWidth =
      timeValue !== null && worstTime !== undefined && worstTime > 0
        ? Math.max(3, (timeValue / worstTime) * 100)
        : 0;
    const comparisonWidth =
      comparisonValue !== null && worstComparisons !== undefined && worstComparisons > 0
        ? Math.max(3, (comparisonValue / worstComparisons) * 100)
        : 0;
    const comparisonRangeWidth =
      metric.comparisonsPerCharRange && maximumComparisonRange > 0
        ? (metric.comparisonsPerCharRange[1] / maximumComparisonRange) * 100
        : 0;

    return (
      <tr key={`${groupLabel ?? ''}-${metric.algorithm}`}>
        <th scope="row">
          {metric.algorithm}
          {groupLabel && <span className="benchmark-case-label">{groupLabel}</span>}
        </th>
        <td>{metric.category}</td>
        <td>
          <div className="benchmark-metric">
            {metric.timeMedianMs === null
              ? absentMetric('Sin mediana', scenario)
              : <span>{formatNumber(metric.timeMedianMs, 6)} ms</span>}
            {metric.timeIqrMs !== null
              ? <small>IQR: {formatNumber(metric.timeIqrMs, 6)} ms</small>
              : absentMetric('Sin IQR', scenario)}
            {timeAvailable && (
              <span
                aria-label={`Mediana relativa ${formatNumber(timeWidth)} por ciento`}
                className={`benchmark-bar benchmark-bar--time${timeValue === bestTime ? ' benchmark-bar--best' : ''}${timeValue === worstTime ? ' benchmark-bar--worst' : ''}`}
                style={{ width: `${timeWidth}%` }}
              />
            )}
          </div>
        </td>
        <td>
          <div className="benchmark-metric">
            {comparisonValue !== null
              ? formatNumber(comparisonValue, 4)
              : metric.comparisonsPerCharRange
                ? `${formatNumber(metric.comparisonsPerCharRange[0], 3)}–${formatNumber(metric.comparisonsPerCharRange[1], 3)}`
                : absentMetric('Sin C/n', scenario)}
            {comparisonAvailable && (
              <span
                aria-label={`Comparaciones relativas ${formatNumber(comparisonWidth)} por ciento`}
                className={`benchmark-bar benchmark-bar--comparisons${metric.comparisonsPerChar === bestComparisons ? ' benchmark-bar--best' : ''}${metric.comparisonsPerChar === worstComparisons ? ' benchmark-bar--worst' : ''}`}
                style={{ width: `${comparisonWidth}%` }}
              />
            )}
            {!comparisonAvailable && metric.comparisonsPerCharRange && (
              <span
                aria-label={`Extremo superior del rango relativo ${formatNumber(comparisonRangeWidth)} por ciento`}
                className="benchmark-bar benchmark-bar--range"
                style={{ width: `${comparisonRangeWidth}%` }}
              />
            )}
            {metric.comparisons !== undefined && (
              <small>{formatNumber(metric.comparisons, 0)} comparaciones en total</small>
            )}
          </div>
        </td>
        <td>
          {metric.memoryAllocatedMb === null
            ? absentMetric('Sin memoria', scenario)
            : `${formatNumber(metric.memoryAllocatedMb)} MB`}
        </td>
      </tr>
    );
  });
}

export function BenchmarkDashboard({ scenario, onScenarioChange }: BenchmarkDashboardProps) {
  const scenarioData = data.scenarios[scenario];
  const isExactCase = scenarioData.inputs.measurement !== undefined;
  const tableMetrics =
    scenarioData.inputs.measurement?.metrics ?? scenarioData.inputs.metrics ?? [];

  return (
    <section className="benchmark-dashboard" aria-labelledby="benchmark-dashboard-title">
      <div className="benchmark-scenario-tabs" role="group" aria-label="Cambiar escenario">
        {scenarioOptions.map((option) => (
          <button
            aria-pressed={scenario === option.id}
            className="benchmark-scenario-tabs__button"
            key={option.id}
            onClick={() => onScenarioChange(option.id)}
            type="button"
          >
            {option.label}
          </button>
        ))}
      </div>
      <span className="artifact-status-badge" role="status">
        ✓ Artefacto benchmarks.json verificado · esquema v{data.schemaVersion}
      </span>
      <header className="benchmark-dashboard__header">
        <div>
          <p className="eyebrow">{scenario}</p>
          <h3 id="benchmark-dashboard-title">{scenarioData.title}</h3>
          <p>{scenarioData.description}</p>
        </div>
        <div className="benchmark-protocol" aria-label="Protocolo de medición">
          <span><strong>JDK:</strong> {data.metadata.jdkVersion}</span>
          <span><strong>Warmup JIT:</strong> {formatNumber(data.metadata.warmupIterations, 0)} iteraciones</span>
          <span><strong>Muestras:</strong> {formatNumber(data.metadata.measuredRuns, 0)} por caso</span>
          <span><strong>Métrica:</strong> mediana + IQR</span>
        </div>
      </header>

      <p className="benchmark-environment">
        Entorno registrado: {data.metadata.environment}. Fecha de ejecución no registrada.
      </p>

      <aside className="scenario-scope-card" aria-labelledby="scenario-scope-title">
        <h4 id="scenario-scope-title">Ficha de Alcance del Escenario</h4>
        {scenario === 'E1' && (
          <>
            <p><strong>Con registro:</strong> mediana de tiempo para patrón presente y ausente.</p>
            <p>
              <strong>Sin registro:</strong> IQR, comparaciones por carácter y memoria asignada;
              estos campos son nulos en el artefacto.
            </p>
          </>
        )}
        {scenario === 'E2' && (
          <>
            <p>
              <strong>Con registro:</strong> rangos de comparaciones por carácter para n=256 y
              n=1.024.
            </p>
            <p>
              <strong>Sin registro:</strong> medianas, IQR y memoria; tampoco hay mediciones para
              el objetivo n=10⁴–10⁶.
            </p>
          </>
        )}
        {scenario === 'E3' && (
          <>
            <p>
              <strong>Con registro:</strong> una condición (n=512, m=16, σ=1), con medianas,
              comparaciones totales y C/n.
            </p>
            <p>
              <strong>Sin registro:</strong> IQR y memoria asignada; la condición medida no se
              extrapola a otros tamaños.
            </p>
          </>
        )}
      </aside>

      {scenario === 'E1' && scenarioData.inputs.cases?.map((benchmarkCase) => (
        <section className="benchmark-case" key={benchmarkCase.id} aria-labelledby={`benchmark-${benchmarkCase.id}`}>
          <h4 id={`benchmark-${benchmarkCase.id}`}>
            {benchmarkCase.label} · n={formatNumber(scenarioData.inputs.observedTextLengths?.[0] ?? 36185, 0)},
            {' '}m={benchmarkCase.patternLength}
          </h4>
          <div className="table-scroll">
            <table className="data-table benchmark-table">
              <thead>
                <tr>
                  <th scope="col">Algoritmo</th>
                  <th scope="col">Categoría</th>
                  <th scope="col">Mediana (ms)</th>
                  <th scope="col">Comparaciones / n</th>
                  <th scope="col">Memoria asignada</th>
                </tr>
              </thead>
              <tbody>{metricRows(benchmarkCase.metrics, scenario)}</tbody>
            </table>
          </div>
        </section>
      ))}

      {scenario !== 'E1' && (
        <div className="table-scroll">
          <table className="data-table benchmark-table">
            <caption>
              {isExactCase
                ? `Resultados medidos: n=${scenarioData.inputs.measurement?.textLength}, m=${scenarioData.inputs.measurement?.patternLength}, σ=${scenarioData.inputs.measurement?.alphabetSize}`
                : `Rango observado; n=${scenarioData.inputs.observedTextLengths?.join(', ')}, m=${scenarioData.inputs.patternLengths?.join(', ')}, σ=${scenarioData.inputs.alphabetSizes?.join(', ')}`}
            </caption>
            <thead>
              <tr>
                <th scope="col">Algoritmo</th>
                <th scope="col">Categoría</th>
                <th scope="col">Mediana (ms)</th>
                <th scope="col">Comparaciones / n</th>
                <th scope="col">Memoria asignada</th>
              </tr>
            </thead>
            <tbody>{metricRows(tableMetrics, scenario)}</tbody>
          </table>
        </div>
      )}

      {scenario === 'E2' && (
        <p className="benchmark-caveat" role="note">
          El escenario sintético disponible solo llega a n=1.024. El objetivo de 10⁴–10⁶
          caracteres aún no está medido; tampoco se dispone aquí de medianas, IQR ni memoria para
          este escenario.
        </p>
      )}

      {scenario === 'E3' && (
        <p className="benchmark-caveat" role="note">
          El informe incluye medición exacta para una combinación representativa. No se extrapola
          ese valor a los demás tamaños adversariales.
        </p>
      )}

      <div className="benchmark-legend" aria-label="Leyenda de barras">
        <span><i className="benchmark-legend__swatch benchmark-bar--best" /> Mejor dentro de esta condición medida</span>
        <span><i className="benchmark-legend__swatch benchmark-bar--worst" /> Peor dentro de esta condición medida</span>
      </div>

      <ul className="benchmark-notes">
        {data.metadata.notes.map((note) => <li key={note}>{note}</li>)}
      </ul>
      <p className="benchmark-source">Fuente: {data.source}</p>
    </section>
  );
}
