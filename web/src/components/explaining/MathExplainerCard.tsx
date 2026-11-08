import type { ReactNode } from 'react';

export interface MathStep {
  stepNumber: number;
  label: string;
  formula: string;
  explanation: string;
}

export interface MathExplainerCardProps {
  title: string;
  category: string;
  purpose: string;
  formula: ReactNode;
  notation: Array<{ symbol: string; meaning: string }>;
  interpretation: string;
  preprocessingProfile?: 'P-TEXTO' | 'P-TOKENS';
  caveat?: string;
  derivationSteps?: MathStep[];
}

export function MathExplainerCard({
  title,
  category,
  purpose,
  formula,
  notation,
  interpretation,
  preprocessingProfile,
  caveat,
  derivationSteps,
}: MathExplainerCardProps) {
  return (
    <article className="card math-card">
      <header className="math-card__header">
        <p className="eyebrow">{category}</p>
        <h2>{title}</h2>
        {preprocessingProfile && (
          <span className="math-card__profile">{preprocessingProfile}</span>
        )}
      </header>

      <section className="math-card__purpose" aria-label="Propósito">
        <h3>¿Para qué?</h3>
        <p>{purpose}</p>
      </section>

      <section className="math-card__process" aria-label="Proceso matemático">
        <h3>Proceso</h3>
        <div className="math-card__formula">{formula}</div>
        <dl className="math-card__notation">
          {notation.map(({ symbol, meaning }) => (
            <div key={symbol}>
              <dt>
                <code>{symbol}</code>
              </dt>
              <dd>{meaning}</dd>
            </div>
          ))}
        </dl>
      </section>

      {derivationSteps && derivationSteps.length > 0 && (
        <section className="math-card__derivation" aria-label="Derivación paso a paso">
          <h3>Derivación paso a paso</h3>
          <ol className="math-card__steps">
            {derivationSteps.map((step) => (
              <li key={step.stepNumber}>
                <span className="math-card__step-number">{step.stepNumber}</span>
                <div className="math-card__step-content">
                  <strong>{step.label}</strong>
                  <code>{step.formula}</code>
                  <p>{step.explanation}</p>
                </div>
              </li>
            ))}
          </ol>
        </section>
      )}

      <section className="math-card__interpretation" aria-label="Salida e interpretación">
        <h3>Salida e interpretación</h3>
        <p>{interpretation}</p>
      </section>

      {caveat && (
        <aside className="math-card__caveat">
          <strong>Nota metodológica:</strong> {caveat}
        </aside>
      )}
    </article>
  );
}
