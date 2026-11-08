import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { AlgorithmTraceInspector } from './AlgorithmTraceInspector';

describe('AlgorithmTraceInspector', () => {
  it('renders a bounded context window around the active text index', () => {
    const text = `${'x'.repeat(60)}AI${'x'.repeat(58)}`;
    render(
      <AlgorithmTraceInspector
        algorithm="RABIN_KARP"
        events={[{
          step: 1,
          textIndex: 60,
          patternIndex: 0,
          match: false,
          shift: 0,
          hashState: { patternHash: 123, textHash: 456 },
        }]}
        pattern="AI"
        text={text}
      />,
    );

    const window = screen.getByLabelText('Ventana de texto con posición activa');
    expect(window.textContent).toContain('...');
    expect(window.textContent).toContain('AI');
    expect(window.textContent?.length).toBeLessThan(text.length);
    expect(screen.getByText(/Rabin–Karp actualiza el hash/)).toBeInTheDocument();
  });

  it('explains the LPS state for KMP trace events', () => {
    render(
      <AlgorithmTraceInspector
        algorithm="KMP"
        events={[{
          step: 1,
          textIndex: 0,
          patternIndex: 0,
          match: false,
          shift: 0,
          lpsState: [0, 0, 1],
        }]}
        pattern="aba"
        text="abacaba"
      />,
    );

    expect(screen.getByText('Tabla LPS:')).toBeInTheDocument();
    expect(screen.getByText(/KMP usa el prefijo propio más largo/)).toBeInTheDocument();
  });

  it('explains Boyer–Moore preprocessing without claiming per-window shifts', () => {
    render(
      <AlgorithmTraceInspector
        algorithm="BOYER_MOORE"
        events={[{
          step: 1,
          textIndex: 0,
          patternIndex: 0,
          match: false,
          shift: 0,
          badCharacterTable: { a: 2 },
          goodSuffixShifts: [3, 1],
          shiftAfterMatch: 2,
        }]}
        pattern="aba"
        text="abacaba"
      />,
    );

    expect(screen.getByText('Regla del carácter malo (preprocesamiento)')).toBeInTheDocument();
    expect(screen.getByText(/no el detalle de cada comparación o salto/)).toBeInTheDocument();
    expect(screen.getByText(/Desplazamiento preprocesado tras una coincidencia completa/))
      .toBeInTheDocument();
  });
});
