import type { ClusteringResponse } from '../../api/client';

type Merge = ClusteringResponse['merges'][number];

export interface MergeStepTableProps {
  articleIds: ClusteringResponse['articleIds'];
  merges: Merge[];
  hoveredStepIndex?: number | null;
  onStepHover?: (stepIndex: number | null) => void;
}

function nodeName(index: number, articleIds: string[]): string {
  if (index < articleIds.length) {
    return articleIds[index] ?? `Nodo ${index}`;
  }
  return `Fusión ${index - articleIds.length + 1}`;
}

export function MergeStepTable({
  articleIds,
  merges,
  hoveredStepIndex,
  onStepHover,
}: MergeStepTableProps) {
  return (
    <div className="table-scroll">
      <table className="data-table">
        <caption>Historial de fusiones del agrupamiento jerárquico</caption>
        <thead>
          <tr>
            <th scope="col">Paso</th>
            <th scope="col">Nodo A</th>
            <th scope="col">Nodo B</th>
            <th scope="col">Altura de fusión (h)</th>
            <th scope="col">Tamaño resultante</th>
          </tr>
        </thead>
        <tbody>
          {merges.length === 0 ? (
            <tr>
              <td className="data-table__empty" colSpan={5}>
                No hay pasos de fusión para mostrar.
              </td>
            </tr>
          ) : (
            merges.map((merge, index) => (
              <tr
                className={hoveredStepIndex === index ? 'table-row--active' : ''}
                key={`${merge.leftChild}-${merge.rightChild}-${index}`}
                onBlur={() => onStepHover?.(null)}
                onFocus={() => onStepHover?.(index)}
                onMouseEnter={() => onStepHover?.(index)}
                onMouseLeave={() => onStepHover?.(null)}
                tabIndex={0}
              >
                <th scope="row">{index + 1}</th>
                <td>{nodeName(merge.leftChild, articleIds)}</td>
                <td>{nodeName(merge.rightChild, articleIds)}</td>
                <td>{merge.height.toFixed(4)}</td>
                <td>{merge.size}</td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
