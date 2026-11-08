import { Button } from '../Button';

export interface PaginationControlProps {
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  onPageChange: (newPage: number) => void;
  onPageSizeChange: (newPageSize: number) => void;
}

export function PaginationControl({
  page,
  pageSize,
  totalElements,
  totalPages,
  onPageChange,
  onPageSizeChange,
}: PaginationControlProps) {
  const firstResult = totalElements === 0 ? 0 : (page - 1) * pageSize + 1;
  const lastResult = Math.min(page * pageSize, totalElements);
  const displayedPage = totalPages === 0 ? 0 : page;

  return (
    <nav className="pagination" aria-label="Paginación de resultados">
      <p className="pagination__range" aria-live="polite">
        Mostrando {firstResult}-{lastResult} de {totalElements} resultados
      </p>
      <label className="pagination__size">
        <span>Mostrar por página</span>
        <select
          aria-label="Mostrar por página"
          onChange={(event) => onPageSizeChange(Number(event.currentTarget.value))}
          value={pageSize}
        >
          {[10, 20, 50].map((size) => (
            <option key={size} value={size}>{size}</option>
          ))}
        </select>
      </label>
      <p className="pagination__hint">
        Cambia la cantidad de artículos visibles o usa los controles para recorrer los resultados.
      </p>
      <div className="pagination__controls">
        <Button
          disabled={displayedPage <= 1}
          onClick={() => onPageChange(1)}
          type="button"
          variant="secondary"
        >
          Primera
        </Button>
        <Button
          disabled={displayedPage <= 1}
          onClick={() => onPageChange(Math.max(1, displayedPage - 1))}
          type="button"
          variant="secondary"
        >
          Anterior
        </Button>
        <span className="pagination__current" aria-current="page">
          Página {displayedPage} de {totalPages}
        </span>
        <Button
          disabled={totalPages === 0 || displayedPage >= totalPages}
          onClick={() => onPageChange(Math.min(totalPages, displayedPage + 1))}
          type="button"
          variant="secondary"
        >
          Siguiente
        </Button>
        <Button
          disabled={totalPages === 0 || displayedPage >= totalPages}
          onClick={() => onPageChange(totalPages)}
          type="button"
          variant="secondary"
        >
          Última
        </Button>
      </div>
    </nav>
  );
}
