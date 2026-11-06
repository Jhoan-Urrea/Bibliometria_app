import { useState, type FormEvent } from 'react';
import type { ClusteringRequest, ClusteringResponse } from '../api/client';
import { api } from '../api/client';
import { errorMessage } from '../api/errorMessage';
import { Alert } from '../components/Alert';
import { Button } from '../components/Button';
import { DataTable } from '../components/DataTable';
import { Dendrogram } from '../components/Dendrogram';
import { Select } from '../components/Select';
import { Tabs } from '../components/Tabs';

export function ClusteringPage() {
  const [clusterCount, setClusterCount] = useState(3);
  const [linkage, setLinkage] = useState<ClusteringRequest['linkage']>('average');
  const [result, setResult] = useState<ClusteringResponse | null>(null);
  const [error, setError] = useState('');

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    try {
      setResult(await api.clustering({ clusterCount, linkage }));
    } catch (cause: unknown) {
      setError(errorMessage(cause));
    }
  }

  return (
    <section className="page-stack" aria-labelledby="clustering-title">
      <h1 id="clustering-title">Clustering jerárquico</h1>
      <p className="page-intro">Configura el número de grupos y el criterio de enlace.</p>
      <form className="card form-grid form-grid--compact" onSubmit={submit}>
        <label className="field" htmlFor="cluster-count">
          <span className="field__label">Número de grupos</span>
          <input
            className="field__control"
            id="cluster-count"
            max={20}
            min={1}
            onChange={(event) => setClusterCount(Number(event.currentTarget.value))}
            required
            type="number"
            value={clusterCount}
          />
        </label>
        <Select
          label="Criterio de enlace"
          name="linkage"
          onChange={(event) => {
            const value = event.currentTarget.value;
            if (value === 'single' || value === 'complete' || value === 'average') {
              setLinkage(value);
            }
          }}
          options={[
            { value: 'single', label: 'Single' },
            { value: 'complete', label: 'Complete' },
            { value: 'average', label: 'Average' },
          ]}
          value={linkage}
        />
        <Button type="submit">Generar agrupamiento</Button>
      </form>
      {error && <Alert title="No se pudo agrupar el corpus" tone="error">{error}</Alert>}
      {result && (
        <Tabs
          label="Visualización de clustering"
          items={[
            {
              id: 'dendrogram',
              label: 'Dendrograma',
              content: <Dendrogram result={result} />,
            },
            {
              id: 'clusters',
              label: `Grupos (${result.clusters.length})`,
              content: (
                <DataTable
                  caption="Artículos asignados a cada grupo"
                  columns={[
                    { key: 'label', header: 'Grupo', render: (cluster) => cluster.label + 1 },
                    { key: 'articles', header: 'Artículos', render: (cluster) => cluster.articleIds.join(', ') },
                  ]}
                  rows={result.clusters}
                  rowKey={(cluster) => String(cluster.label)}
                />
              ),
            },
          ]}
        />
      )}
    </section>
  );
}
