import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { PaginationControl } from './PaginationControl';

describe('PaginationControl', () => {
  it('labels the page-size selector and explains pagination', () => {
    const onPageSizeChange = vi.fn();
    render(
      <PaginationControl
        onPageChange={vi.fn()}
        onPageSizeChange={onPageSizeChange}
        page={1}
        pageSize={10}
        totalElements={25}
        totalPages={3}
      />,
    );

    fireEvent.change(screen.getByRole('combobox', { name: 'Mostrar por página' }), {
      target: { value: '20' },
    });

    expect(onPageSizeChange).toHaveBeenCalledWith(20);
    expect(screen.getByText(/usa los controles para recorrer los resultados/)).toBeInTheDocument();
  });
});
