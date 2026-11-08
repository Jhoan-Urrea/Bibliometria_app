import { hierarchy } from 'd3';
import { useMemo } from 'react';
import type { HierarchyNode } from 'd3';
import type { ClusteringResponse } from '../api/client';

interface TreeNode {
  label: string;
  mergeIndex?: number;
  height?: number;
  children?: TreeNode[];
}

export interface DendrogramProps {
  result: ClusteringResponse;
  cutHeight?: number;
  hoveredStepIndex?: number | null;
  onStepHover?: (stepIndex: number | null) => void;
}

const clusterPalette = ['#0284c7', '#16a34a', '#d97706', '#7c3aed'];

function clusterColor(index: number): string {
  if (index < clusterPalette.length) return clusterPalette[index]!;
  return `hsl(${(index * 137.508) % 360} 68% 42%)`;
}

export function Dendrogram({
  result,
  cutHeight,
  hoveredStepIndex,
  onStepHover,
}: DendrogramProps) {
  const chart = useMemo(() => {
    const nodes = new Map<number, TreeNode>();
    result.articleIds.forEach((id, index) => nodes.set(index, { label: id }));

    result.merges.forEach((merge, index) => {
      const left = nodes.get(merge.leftChild);
      const right = nodes.get(merge.rightChild);
      if (!left || !right) {
        throw new Error('El dendrograma contiene referencias a nodos inexistentes.');
      }
      nodes.set(result.articleIds.length + index, {
        label: `Fusión ${index + 1}`,
        mergeIndex: index,
        height: merge.height,
        children: [left, right],
      });
    });

    const root = nodes.get(result.articleIds.length + result.merges.length - 1);
    if (!root) {
      throw new Error('La respuesta no contiene un dendrograma completo.');
    }

    const hierarchyRoot = hierarchy(root);
    const leaves = hierarchyRoot.leaves();
    const svgHeight = 480;
    const positions = new Map<HierarchyNode<TreeNode>, { x: number; y: number }>();
    leaves.forEach((leaf, index) => {
      positions.set(leaf, { x: 25 + index * 22, y: 720 });
    });
    hierarchyRoot.eachAfter((node) => {
      if (node.children) {
        const childPositions = node.children.map((child) => positions.get(child));
        if (childPositions.some((position) => position === undefined)) {
          throw new Error('No fue posible asignar coordenadas a las hojas del dendrograma.');
        }
        positions.set(node, {
          x: childPositions.reduce((sum, position) => sum + position!.x, 0)
            / childPositions.length,
          y: 0,
        });
      }
    });

    const internalNodes = hierarchyRoot.descendants().filter(
      (node) => node.data.height !== undefined,
    );
    const minimumMergeHeight = Math.min(...internalNodes.map((node) => node.data.height!));
    const maximumMergeHeight = Math.max(...internalNodes.map((node) => node.data.height!));
    const mergeHeightRange = maximumMergeHeight - minimumMergeHeight || 1;
    hierarchyRoot.each((node) => {
      const position = positions.get(node);
      if (!position) {
        throw new Error('No fue posible asignar coordenadas al dendrograma.');
      }
      if (!node.children || node.data.height === undefined) {
        position.y = 720;
      } else {
        position.y =
          80
          + (1 - (node.data.height - minimumMergeHeight) / mergeHeightRange) * 600;
      }
    });

    const clusterByNode = new Map<HierarchyNode<TreeNode>, number>();
    const clusterRoots: HierarchyNode<TreeNode>[] = [];
    if (cutHeight !== undefined) {
      const findCutClusters = (node: HierarchyNode<TreeNode>) => {
        if (
          !node.children
          || (node.data.height !== undefined && node.data.height <= cutHeight)
        ) {
          clusterRoots.push(node);
          return;
        }
        node.children.forEach(findCutClusters);
      };
      findCutClusters(hierarchyRoot);

      clusterRoots.forEach((clusterRoot, clusterIndex) => {
        const assignCluster = (node: HierarchyNode<TreeNode>) => {
          clusterByNode.set(node, clusterIndex);
          node.children?.forEach(assignCluster);
        };
        assignCluster(clusterRoot);
      });
    }

    return {
      svgHeight,
      cutX:
        cutHeight === undefined
          ? null
          : maximumMergeHeight > minimumMergeHeight
            ? 80
              + (
                1
                - (
                  Math.max(minimumMergeHeight, Math.min(cutHeight, maximumMergeHeight))
                  - minimumMergeHeight
                ) / mergeHeightRange
              ) * 600
            : cutHeight >= maximumMergeHeight ? 80 : 680,
      links: hierarchyRoot.links(),
      nodes: hierarchyRoot.descendants(),
      clusterByNode,
      path: (source: HierarchyNode<TreeNode>, target: HierarchyNode<TreeNode>) => {
        const sourcePosition = positions.get(source);
        const targetPosition = positions.get(target);
        if (!sourcePosition || !targetPosition) {
          throw new Error('No fue posible construir una rama del dendrograma.');
        }
        return `M ${sourcePosition.y} ${sourcePosition.x} V ${targetPosition.x} H ${targetPosition.y}`;
      },
      positions,
    };
  }, [cutHeight, result]);

  return (
    <div className="dendrogram-container dendrogram-scroll">
      <svg
        aria-labelledby="dendrogram-title dendrogram-description"
        className="dendrogram dendrogram__svg"
        role="img"
        viewBox={`0 0 850 ${chart.svgHeight}`}
        preserveAspectRatio="xMinYMin meet"
      >
        <title id="dendrogram-title">Dendrograma de agrupamiento</title>
        <desc id="dendrogram-description">
          Árbol jerárquico para el método {result.linkage}; contiene {result.articleIds.length}{' '}
          artículos.
          {cutHeight !== undefined
            ? ` La línea vertical señala el corte de altura ${cutHeight.toFixed(4)}.`
            : ''}
        </desc>
        <g>
          {chart.links.map((link, index) => (
            <path
              className={[
                'dendrogram__link',
                hoveredStepIndex !== null
                  && hoveredStepIndex !== undefined
                  && link.source.data.mergeIndex === hoveredStepIndex
                  ? 'dendrogram__link--active'
                  : '',
              ].filter(Boolean).join(' ')}
              d={chart.path(link.source, link.target)}
              key={`${link.source.data.label}-${link.target.data.label}-${index}`}
              style={{
                stroke: hoveredStepIndex === link.source.data.mergeIndex
                  ? undefined
                  : chart.clusterByNode.has(link.source)
                    && chart.clusterByNode.get(link.source) === chart.clusterByNode.get(link.target)
                    ? clusterColor(chart.clusterByNode.get(link.source)!)
                    : undefined,
              }}
            />
          ))}
          {chart.cutX !== null && (
            <g className="dendrogram__cut" aria-hidden="true">
              <line
                x1={chart.cutX}
                x2={chart.cutX}
                y1={0}
                y2={chart.svgHeight - 40}
              />
              <text x={chart.cutX + 6} y={14}>
                α = {cutHeight?.toFixed(4)}
              </text>
            </g>
          )}
          {chart.nodes.map((node) => (
            <g
              className={[
                'dendrogram__node',
                hoveredStepIndex !== null
                  && hoveredStepIndex !== undefined
                  && (
                    node.data.mergeIndex === hoveredStepIndex
                    || node.parent?.data.mergeIndex === hoveredStepIndex
                  )
                  ? 'dendrogram__node--active'
                  : '',
              ].filter(Boolean).join(' ')}
              aria-label={
                node.data.mergeIndex === undefined
                  ? undefined
                  : `Paso ${node.data.mergeIndex + 1}: fusión`
              }
              key={`${node.data.label}-${node.depth}`}
              transform={`translate(${chart.positions.get(node)?.y},${chart.positions.get(node)?.x})`}
              onBlur={() => {
                if (node.data.mergeIndex !== undefined) onStepHover?.(null);
              }}
              onFocus={() => {
                if (node.data.mergeIndex !== undefined) onStepHover?.(node.data.mergeIndex);
              }}
              onMouseEnter={() => {
                if (node.data.mergeIndex !== undefined) onStepHover?.(node.data.mergeIndex);
              }}
              onMouseLeave={() => {
                if (node.data.mergeIndex !== undefined) onStepHover?.(null);
              }}
              tabIndex={node.data.mergeIndex === undefined ? undefined : 0}
            >
              <circle
                r={node.children ? 4 : 5}
                style={{
                  fill: chart.clusterByNode.has(node)
                    ? clusterColor(chart.clusterByNode.get(node)!)
                    : undefined,
                }}
              />
              {!node.children && (
                <text dx="10" dy="4">
                  {node.data.label}
                </text>
              )}
            </g>
          ))}
        </g>
      </svg>
      {cutHeight !== undefined && (
        <p className="dendrogram-cut-summary">
          El corte explora las fusiones con altura h ≤ α; no modifica el agrupamiento calculado por
          la API.
        </p>
      )}
    </div>
  );
}
