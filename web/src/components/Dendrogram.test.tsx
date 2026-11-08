import { fireEvent, render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { ClusteringResponse } from '../api/client';
import { Dendrogram } from './Dendrogram';

function makeTwentyArticleResult(
  linkage: ClusteringResponse['linkage'] = 'average',
): ClusteringResponse {
  const articleIds = Array.from({ length: 20 }, (_, index) =>
    `A${String(index + 1).padStart(2, '0')}`,
  );
  const merges = Array.from({ length: articleIds.length - 1 }, (_, index) => ({
    leftChild: index === 0 ? 0 : index + 1,
    rightChild: index === 0 ? 1 : articleIds.length + index - 1,
    height: (index + 1) / 100,
    size: index + 2,
  }));

  return {
    articleIds,
    linkage,
    clusters: [{ label: 0, articleIds }],
    merges,
  };
}

describe('Dendrogram', () => {
  it('renders an accessible SVG hierarchy from API merge indices', () => {
    const result: ClusteringResponse = {
      articleIds: ['A01', 'A02'],
      linkage: 'average',
      clusters: [{ label: 0, articleIds: ['A01', 'A02'] }],
      merges: [{ leftChild: 0, rightChild: 1, height: 0.25, size: 2 }],
    };

    const { getByRole, getByText, container } = render(<Dendrogram result={result} />);

    expect(getByRole('img', { name: /Dendrograma de agrupamiento/ })).toBeTruthy();
    expect(getByText('A01')).toBeTruthy();
    expect(getByText('A02')).toBeTruthy();
    expect(container.querySelectorAll('path.dendrogram__link')).toHaveLength(2);
  });

  it('keeps at least 24 SVG units between adjacent leaves for every linkage and colors cut clusters', () => {
    for (const linkage of ['single', 'complete', 'average'] as const) {
      const { container, unmount } = render(
        <Dendrogram cutHeight={0.1} result={makeTwentyArticleResult(linkage)} />,
      );
      const leafCoordinates = Array.from(
        container.querySelectorAll<SVGGElement>('.dendrogram__node'),
      )
        .filter((node) => node.querySelector('text') !== null)
        .map((node) => Number(node.getAttribute('transform')?.match(/,([\d.]+)\)/)?.[1]))
        .sort((a, b) => a - b);
      const colors = new Set(
        Array.from(container.querySelectorAll<SVGCircleElement>('.dendrogram__node circle'))
          .map((circle) => circle.style.fill)
          .filter(Boolean),
      );

      expect(leafCoordinates).toHaveLength(20);
      expect(leafCoordinates.slice(1).every((coordinate, index) =>
        coordinate - leafCoordinates[index]! >= 24,
      )).toBe(true);
      expect(colors.size).toBeGreaterThan(1);
      unmount();
    }
  });

  it('highlights the selected merge and reports hover from SVG nodes', () => {
    const onStepHover = vi.fn();
    const { container } = render(
      <Dendrogram
        hoveredStepIndex={0}
        onStepHover={onStepHover}
        result={makeTwentyArticleResult()}
      />,
    );

    expect(container.querySelectorAll('.dendrogram__link--active')).toHaveLength(2);
    expect(container.querySelectorAll('.dendrogram__node--active')).toHaveLength(3);

    const fusionNode = container.querySelector('[aria-label="Paso 1: fusión"]');
    expect(fusionNode).not.toBeNull();
    fireEvent.mouseEnter(fusionNode!);
    expect(onStepHover).toHaveBeenCalledWith(0);
  });
});
