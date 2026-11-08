import { fireEvent, render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { ClusteringResponse } from '../../api/client';
import { MergeStepTable } from './MergeStepTable';

describe('MergeStepTable', () => {
  const articleIds = ['A01', 'A02'];
  const merges: ClusteringResponse['merges'] = [
    { leftChild: 0, rightChild: 1, height: 0.25, size: 2 },
  ];

  it('highlights a hovered row and reports the active step', () => {
    const onStepHover = vi.fn();
    const { getByRole } = render(
      <MergeStepTable
        articleIds={articleIds}
        hoveredStepIndex={0}
        merges={merges}
        onStepHover={onStepHover}
      />,
    );
    const row = getByRole('row', { name: /1 A01 A02 0.2500 2/ });

    expect(row.className).toBe('table-row--active');
    fireEvent.mouseEnter(row);
    expect(onStepHover).toHaveBeenCalledWith(0);
    fireEvent.mouseLeave(row);
    expect(onStepHover).toHaveBeenCalledWith(null);
  });
});
