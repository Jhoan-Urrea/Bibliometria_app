import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { ClusteringResponse } from '../api/client';
import { Dendrogram } from './Dendrogram';

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
});
