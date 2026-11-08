import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { BenchmarkDashboard } from './BenchmarkDashboard';

describe('BenchmarkDashboard', () => {
  it('exposes scenario controls and reports absent metrics without fabricating values', () => {
    const onScenarioChange = vi.fn();
    render(<BenchmarkDashboard onScenarioChange={onScenarioChange} scenario="E1" />);

    fireEvent.click(screen.getByRole('button', { name: /E2 · Escala sintética/i }));

    expect(onScenarioChange).toHaveBeenCalledWith('E2');
    expect(screen.getByText('Ficha de Alcance del Escenario')).toBeInTheDocument();
    expect(screen.getByText(/mediana de tiempo para patrón presente y ausente/i)).toBeInTheDocument();
    expect(screen.getAllByText('Sin IQR en E1').length).toBeGreaterThan(0);
    expect(screen.getByText(/Artefacto benchmarks\.json verificado/)).toBeInTheDocument();
  });

  it('shows E2 comparison ranges and badges for unmeasured time and memory', () => {
    render(<BenchmarkDashboard onScenarioChange={vi.fn()} scenario="E2" />);

    expect(screen.getByText(/Rango observado/)).toBeInTheDocument();
    expect(screen.getAllByText('Sin mediana en E2').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Sin memoria en E2').length).toBeGreaterThan(0);
    expect(screen.getByText(/rangos de comparaciones por carácter/)).toBeInTheDocument();
  });
});
