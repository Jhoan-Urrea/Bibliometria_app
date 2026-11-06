import type { ReactNode } from 'react';

export interface Column<Row> {
  key: string;
  header: string;
  render: (row: Row) => ReactNode;
}

interface DataTableProps<Row> {
  caption: string;
  columns: Column<Row>[];
  rows: Row[];
  rowKey: (row: Row) => string;
  emptyMessage?: string;
}

export function DataTable<Row>({
  caption,
  columns,
  rows,
  rowKey,
  emptyMessage = 'No hay resultados para mostrar.',
}: DataTableProps<Row>) {
  return (
    <div className="table-scroll">
      <table className="data-table">
        <caption>{caption}</caption>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key} scope="col">
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="data-table__empty">
                {emptyMessage}
              </td>
            </tr>
          ) : (
            rows.map((row) => (
              <tr key={rowKey(row)}>
                {columns.map((column) => (
                  <td key={column.key}>{column.render(row)}</td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
