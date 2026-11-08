import { useEffect, useRef, useState, type FormEvent } from 'react';
import type { ClusteringRequest, ClusteringResponse } from '../api/client';
import { api } from '../api/client';
import { errorMessage } from '../api/errorMessage';
import { Alert } from '../components/Alert';
import { Button } from '../components/Button';
import { DataTable } from '../components/DataTable';
import { Dendrogram } from '../components/Dendrogram';
import { MergeStepTable } from '../components/clustering/MergeStepTable';
import { MathExplainerCard } from '../components/explaining/MathExplainerCard';
import { Select } from '../components/Select';
import { Tabs } from '../components/Tabs';

const linkageOptions = [
  { value: 'single', label: 'Single — disimilitud mínima' },
  { value: 'complete', label: 'Complete — disimilitud máxima' },
  { value: 'average', label: 'Average — promedio ponderado (UPGMA)' },
] as const;

const linkageDescriptions = {
  single: 'Single usa la disimilitud mínima entre elementos de los dos grupos.',
  complete: 'Complete usa la disimilitud máxima entre elementos de los dos grupos.',
  average: 'Average (UPGMA) usa el promedio ponderado por los tamaños de los grupos.',
} satisfies Record<ClusteringRequest['linkage'], string>;

const linkageCoefficients = {
  single: 'αₐ = 1/2, αᵦ = 1/2, β = 0, γ = −1/2',
  complete: 'αₐ = 1/2, αᵦ = 1/2, β = 0, γ = +1/2',
  average: 'αₐ = |Cₐ|/(|Cₐ|+|Cᵦ|), αᵦ = |Cᵦ|/(|Cₐ|+|Cᵦ|), β = 0, γ = 0',
} satisfies Record<ClusteringRequest['linkage'], string>;

function mergeNodeName(index: number, articleIds: string[]): string {
  if (index < articleIds.length) return articleIds[index] ?? `Nodo ${index}`;
  return `Fusión ${index - articleIds.length + 1}`;
}

export function ClusteringPage() {
  const [clusterCount, setClusterCount] = useState(3);
  const [linkage, setLinkage] = useState<ClusteringRequest['linkage']>('average');
  const [result, setResult] = useState<ClusteringResponse | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [cutHeight, setCutHeight] = useState(0);
  const [hoveredStepIndex, setHoveredStepIndex] = useState<number | null>(null);
  const requestController = useRef<AbortController | null>(null);
  const firstMerge = result?.merges[0];

  const maximumHeight = result
    ? Math.max(0, ...result.merges.map((merge) => merge.height))
    : 0;
  const mergeCountAtCut = result?.merges.filter((merge) => merge.height <= cutHeight).length ?? 0;
  const clusterCountAtCut = result
    ? Math.max(1, result.articleIds.length - mergeCountAtCut)
    : 0;

  useEffect(
    () => () => {
      requestController.current?.abort();
    },
    [],
  );

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    requestController.current?.abort();
    const controller = new AbortController();
    requestController.current = controller;
    setError('');
    setResult(null);
    setHoveredStepIndex(null);
    setLoading(true);

    try {
      const response = await api.clustering({ clusterCount, linkage }, controller.signal);
      if (controller.signal.aborted) return;
      setResult(response);
      setCutHeight(Math.max(0, ...response.merges.map((merge) => merge.height)));
      setHoveredStepIndex(null);
    } catch (cause: unknown) {
      if (!controller.signal.aborted) setError(errorMessage(cause));
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }

  return (
    <section className="page-stack clustering-workspace" aria-labelledby="clustering-title">
      <header className="hero">
        <p className="eyebrow">Entrada → Proceso → Salida</p>
        <h1 id="clustering-title">Clustering jerárquico aglomerativo</h1>
        <p>
          Configura un criterio de enlace y el número de grupos; explora cómo las fusiones
          construyen la jerarquía y dónde una altura de corte separa los clusters.
        </p>
      </header>

      <section className="clustering-section" aria-labelledby="clustering-input-title">
        <div className="section-heading">
          <p className="eyebrow">01 · Entrada</p>
          <h2 id="clustering-input-title">Parámetros del agrupamiento</h2>
        </div>
        <form className="card form-grid form-grid--compact" onSubmit={submit}>
          <label className="field" htmlFor="cluster-count">
            <span className="field__label">Número de grupos K</span>
            <input
              className="field__control"
              disabled={loading}
              id="cluster-count"
              max={20}
              min={1}
              onChange={(event) => {
                setClusterCount(Number(event.currentTarget.value));
                setResult(null);
                setHoveredStepIndex(null);
                setError('');
              }}
              required
              type="number"
              value={clusterCount}
            />
            <span className="field__hint">Selecciona un valor entre 1 y 20.</span>
          </label>
          <div className="clustering-linkage-field">
            <Select
              label="Criterio de enlace"
              disabled={loading}
              name="linkage"
              onChange={(event) => {
                const value = event.currentTarget.value;
                if (value === 'single' || value === 'complete' || value === 'average') {
                  setLinkage(value);
                  setResult(null);
                  setHoveredStepIndex(null);
                  setError('');
                }
              }}
              options={linkageOptions}
              value={linkage}
            />
            <p className="field__hint">{linkageDescriptions[linkage]}</p>
          </div>
          <Button disabled={loading} type="submit">
            {loading ? 'Agrupando…' : 'Ejecutar Agrupamiento'}
          </Button>
        </form>
        {error && <Alert title="No se pudo agrupar el corpus" tone="error">{error}</Alert>}
      </section>

      <section className="clustering-section" aria-labelledby="clustering-process-title">
        <div className="section-heading">
          <p className="eyebrow">02 · Proceso</p>
          <h2 id="clustering-process-title">Reglas de actualización y validación</h2>
          <p className="page-intro">
            El método seleccionado determina los coeficientes con los que se actualiza la
            disimilitud entre el nuevo grupo y los grupos restantes.
          </p>
        </div>

        <MathExplainerCard
          category={`Recurrencia de Lance–Williams · ${linkage}`}
          caveat="La distancia inicial es una disimilitud derivada de similitud coseno TF-IDF: δ = 1 − Sim_cos. La regla de enlace actualiza distancias entre grupos a partir de esa matriz."
          formula={
            <>
              δ(Cₖ, Cₐ ∪ Cᵦ) = αₐ · δ(Cₖ, Cₐ) + αᵦ · δ(Cₖ, Cᵦ)
              <br />
              + β · δ(Cₐ, Cᵦ) + γ · |δ(Cₖ, Cₐ) − δ(Cₖ, Cᵦ)|
              {result && firstMerge && (
                <div className="lance-williams-example">
                  <strong>Ejemplo numérico · primera fusión del servidor</strong>
                  <p>
                    Paso 1: {mergeNodeName(firstMerge.leftChild, result.articleIds)} +{' '}
                    {mergeNodeName(firstMerge.rightChild, result.articleIds)} → Fusión 1,
                    con h₁ = {firstMerge.height.toFixed(4)}.
                  </p>
                  <code>
                    δ(
                    {mergeNodeName(firstMerge.leftChild, result.articleIds)},&nbsp;
                    {mergeNodeName(firstMerge.rightChild, result.articleIds)}
                    ) = h₁ = {firstMerge.height.toFixed(4)}
                  </code>
                  <p>
                    Esta es la disimilitud observada que inicia la jerarquía. El endpoint no
                    devuelve las distancias a un tercer cluster Cₖ, por lo que no se inventan los
                    términos necesarios para evaluar una actualización posterior de
                    Lance–Williams.
                  </p>
                </div>
              )}
            </>
          }
          interpretation={linkageDescriptions[linkage]}
          notation={[
            { symbol: '(αₐ, αᵦ, β, γ)', meaning: linkageCoefficients[linkage] },
            { symbol: 'Cₐ, Cᵦ', meaning: 'Clusters que se fusionan; Cₖ es otro cluster activo.' },
            { symbol: 'δ(X,Y)', meaning: 'Disimilitud entre dos clusters según el enlace seleccionado.' },
            { symbol: 'h', meaning: 'Altura de fusión registrada en cada paso del dendrograma.' },
          ]}
          purpose="Define cómo se actualizan las distancias después de cada fusión para formar la jerarquía aglomerativa."
          title="Actualización de Lance–Williams"
        />

        <MathExplainerCard
          category="Validación interna del agrupamiento"
          caveat="El módulo core implementa evaluación global con CPCC y Silhouette. El endpoint de agrupamiento usado por esta pantalla no devuelve esos valores; se presentan las fórmulas, no métricas numéricas fabricadas."
          formula={
            <>
              CPCC = corr_Pearson(δᵢⱼ, hᵢⱼ), para M = N(N−1)/2 = 190 pares si N = 20
              <br />
              s(x) = (sep(x) − coh(x)) / max(coh(x), sep(x))
              <br />
              S(K) = promedio_x s(x)
            </>
          }
          interpretation="CPCC resume cuánto preservan las alturas cofenéticas las disimilitudes originales. Silhouette resume cohesión intracluster frente a separación del cluster vecino."
          notation={[
            { symbol: 'δᵢⱼ', meaning: 'Disimilitud original entre los documentos i y j.' },
            { symbol: 'hᵢⱼ', meaning: 'Altura a la que i y j quedan unidos por primera vez.' },
            { symbol: 'coh(x)', meaning: 'Distancia media de x a los demás elementos de su cluster.' },
            { symbol: 'sep(x)', meaning: 'Menor distancia media de x a otro cluster.' },
            { symbol: 'S(K)', meaning: 'Promedio de silueta para la partición de K clusters.' },
          ]}
          purpose="Las métricas ayudan a evaluar la fidelidad del dendrograma y la calidad de una partición."
          title="CPCC y coeficiente de Silhouette"
        />
      </section>

      <section className="clustering-section" aria-labelledby="clustering-output-title">
        <div className="section-heading">
          <p className="eyebrow">03 · Salida</p>
          <h2 id="clustering-output-title">Dendrograma e historial de fusiones</h2>
          <p className="page-intro">
            La línea α permite explorar localmente los niveles de fusión. Mover el corte no vuelve
            a llamar a la API ni modifica el agrupamiento solicitado.
          </p>
        </div>

        {!result && !loading && (
          <p className="empty-state">Ejecuta un agrupamiento para visualizar su dendrograma.</p>
        )}

        {loading && <p className="page-intro" role="status">Calculando la jerarquía…</p>}

        {result && (
          <>
            <section className="card dendrogram-controls" aria-labelledby="cut-control-title">
              <div className="dendrogram-controls__header">
                <div>
                  <h3 id="cut-control-title">Explorar altura de corte α</h3>
                  <p>
                    En α = <strong>{cutHeight.toFixed(4)}</strong>, el corte representa{' '}
                    <strong>{clusterCountAtCut}</strong> clusters.
                  </p>
                </div>
                <label className="dendrogram-cut-value" htmlFor="dendrogram-cut">
                  Altura α
                  <output htmlFor="dendrogram-cut">{cutHeight.toFixed(4)}</output>
                </label>
              </div>
              <input
                aria-label="Altura de corte alfa"
                aria-valuetext={`${cutHeight.toFixed(4)}, ${clusterCountAtCut} clusters`}
                id="dendrogram-cut"
                max={maximumHeight}
                min={0}
                onChange={(event) => setCutHeight(Number(event.currentTarget.value))}
                step={maximumHeight > 0 ? maximumHeight / 100 : 0.0001}
                type="range"
                value={cutHeight}
              />
              <div className="dendrogram-cut-range" aria-hidden="true">
                <span>0</span>
                <span>Altura máxima {maximumHeight.toFixed(4)}</span>
              </div>
            </section>

            <Tabs
              label="Resultados del clustering"
              items={[
                {
                  id: 'clustering-dendrogram',
                  label: 'Dendrograma e historial interactivo',
                  content: (
                    <div className="clustering-trace-view">
                      <Dendrogram
                        cutHeight={cutHeight}
                        hoveredStepIndex={hoveredStepIndex}
                        onStepHover={setHoveredStepIndex}
                        result={result}
                      />
                      <MergeStepTable
                        articleIds={result.articleIds}
                        hoveredStepIndex={hoveredStepIndex}
                        merges={result.merges}
                        onStepHover={setHoveredStepIndex}
                      />
                    </div>
                  ),
                },
                {
                  id: 'clustering-assignments',
                  label: `Asignación de Clusters (${result.clusters.length})`,
                  content: (
                    <DataTable
                      caption="Artículos asignados a cada grupo"
                      columns={[
                        { key: 'label', header: 'Grupo', render: (cluster) => cluster.label + 1 },
                        { key: 'articles', header: 'Artículos', render: (cluster) => cluster.articleIds.join(', ') },
                        { key: 'size', header: 'Tamaño', render: (cluster) => cluster.articleIds.length },
                      ]}
                      rows={result.clusters}
                      rowKey={(cluster) => String(cluster.label)}
                    />
                  ),
                },
              ]}
            />
          </>
        )}
      </section>
    </section>
  );
}
