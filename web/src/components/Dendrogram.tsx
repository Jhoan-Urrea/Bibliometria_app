import { hierarchy, linkHorizontal, tree } from 'd3';
import { useMemo } from 'react';
import type { HierarchyPointLink, HierarchyPointNode } from 'd3';
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

    const layout = tree<TreeNode>()
      .nodeSize([24, 720])
      .separation((a, b) => (a.parent === b.parent ? 1 : 1.5));
    const hierarchyRoot = layout(hierarchy(root));
    const leaves = hierarchyRoot.leaves();
    const minLeafX = Math.min(...leaves.map((leaf) => leaf.x));
    const maxLeafX = Math.max(...leaves.map((leaf) => leaf.x));
    const leafPositions = leaves.map((leaf) => leaf.x).sort((a, b) => a - b);
    const minimumLeafGap = Math.min(
      Infinity,
      ...leafPositions.slice(1).map((position, index) => position - leafPositions[index]!),
    );
    const verticalScale = minimumLeafGap > 0
      ? Math.max(1, 24 / minimumLeafGap)
      : 1;
    hierarchyRoot.each((node) => {
      node.x = (node.x - minLeafX) * verticalScale + 18;
    });
    const svgHeight = Math.max(360, (maxLeafX - minLeafX) * verticalScale + 36);

    const maximumMergeHeight = Math.max(
      0,
      ...result.merges.map((merge) => merge.height),
    );
    const heightScale = maximumMergeHeight > 0 ? maximumMergeHeight : 1;
    if (maximumMergeHeight > 0) {
      hierarchyRoot.each((node) => {
        node.y =
          node.data.height === undefined
            ? 720
            : 720 * (1 - node.data.height / heightScale);
      });
    }

    const clusterByNode = new Map<HierarchyPointNode<TreeNode>, number>();
    const clusterRoots: HierarchyPointNode<TreeNode>[] = [];
    if (cutHeight !== undefined) {
      const findCutClusters = (node: HierarchyPointNode<TreeNode>) => {
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
        const assignCluster = (node: HierarchyPointNode<TreeNode>) => {
          clusterByNode.set(node, clusterIndex);
          node.children?.forEach(assignCluster);
        };
        assignCluster(clusterRoot);
      });
    }

    const linkPath = linkHorizontal<
      unknown,
      HierarchyPointLink<TreeNode>,
      HierarchyPointNode<TreeNode>
    >()
      .x((node) => node.y)
      .y((node) => node.x);
    return {
      svgHeight,
      cutX:
        cutHeight === undefined
          ? null
          : maximumMergeHeight === 0
            ? 0
            : 24
              + 720
                * (1 - Math.max(0, Math.min(cutHeight, maximumMergeHeight)) / heightScale),
      links: hierarchyRoot.links(),
      nodes: hierarchyRoot.descendants(),
      clusterByNode,
      path: (link: HierarchyPointLink<TreeNode>) => linkPath(link) ?? '',
      maximumMergeHeight,
    };
  }, [cutHeight, result]);

  return (
    <div className="dendrogram-scroll">
      <svg
        aria-labelledby="dendrogram-title dendrogram-description"
        className="dendrogram dendrogram__svg"
        role="img"
        viewBox={`0 0 920 ${chart.svgHeight}`}
      >
        <title id="dendrogram-title">Dendrograma de agrupamiento</title>
        <desc id="dendrogram-description">
          Árbol jerárquico para el método {result.linkage}; contiene {result.articleIds.length}{' '}
          artículos.
          {cutHeight !== undefined
            ? ` La línea vertical señala el corte de altura ${cutHeight.toFixed(4)}.`
            : ''}
        </desc>
        <g transform="translate(24,18)">
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
              d={chart.path(link)}
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
                x1={chart.cutX - 24}
                x2={chart.cutX - 24}
                y1={0}
                y2={chart.svgHeight - 36}
              />
              <text x={chart.cutX - 18} y={14}>
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
              transform={`translate(${node.y},${node.x})`}
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
