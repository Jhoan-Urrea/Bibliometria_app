import { hierarchy, linkHorizontal, tree } from 'd3';
import { useMemo } from 'react';
import type { HierarchyPointLink, HierarchyPointNode } from 'd3';
import type { ClusteringResponse } from '../api/client';

interface TreeNode {
  label: string;
  children?: TreeNode[];
}

interface DendrogramProps {
  result: ClusteringResponse;
}

export function Dendrogram({ result }: DendrogramProps) {
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
        children: [left, right],
      });
    });

    const root = nodes.get(result.articleIds.length + result.merges.length - 1);
    if (!root) {
      throw new Error('La respuesta no contiene un dendrograma completo.');
    }

    const height = Math.max(360, result.articleIds.length * 28);
    const layout = tree<TreeNode>().size([height - 36, 720]);
    const hierarchyRoot = layout(hierarchy(root));
    const linkPath = linkHorizontal<
      unknown,
      HierarchyPointLink<TreeNode>,
      HierarchyPointNode<TreeNode>
    >()
      .x((node) => node.y)
      .y((node) => node.x);
    return {
      height,
      links: hierarchyRoot.links(),
      nodes: hierarchyRoot.descendants(),
      path: (link: HierarchyPointLink<TreeNode>) => linkPath(link) ?? '',
    };
  }, [result]);

  return (
    <div className="dendrogram-scroll">
      <svg
        aria-labelledby="dendrogram-title dendrogram-description"
        className="dendrogram"
        role="img"
        viewBox={`0 0 920 ${chart.height}`}
      >
        <title id="dendrogram-title">Dendrograma de agrupamiento</title>
        <desc id="dendrogram-description">
          Árbol jerárquico para el método {result.linkage}; contiene {result.articleIds.length}{' '}
          artículos.
        </desc>
        <g transform="translate(24,18)">
          {chart.links.map((link, index) => (
            <path
              className="dendrogram__link"
              d={chart.path(link)}
              key={`${link.source.data.label}-${link.target.data.label}-${index}`}
            />
          ))}
          {chart.nodes.map((node) => (
            <g
              className="dendrogram__node"
              key={`${node.data.label}-${node.depth}`}
              transform={`translate(${node.y},${node.x})`}
            >
              <circle r={node.children ? 4 : 5} />
              {!node.children && (
                <text dx="10" dy="4">
                  {node.data.label}
                </text>
              )}
            </g>
          ))}
        </g>
      </svg>
    </div>
  );
}
