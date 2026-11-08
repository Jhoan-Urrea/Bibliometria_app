import { useEffect, useState } from 'react';

export interface TraceEvent {
  step: number;
  textIndex: number;
  patternIndex: number;
  comparedCharText?: string;
  comparedCharPattern?: string;
  match: boolean;
  shift: number;
  commentary?: string;
  lpsState?: number[];
  badCharacterTable?: Record<string, number>;
  goodSuffixShifts?: number[];
  shiftAfterMatch?: number;
  hashState?: { patternHash: number; textHash: number };
}

export interface AlgorithmTraceInspectorProps {
  text: string;
  pattern: string;
  algorithm: 'KMP' | 'BOYER_MOORE' | 'RABIN_KARP';
  events: TraceEvent[];
}

const algorithmLabels = {
  KMP: 'KMP',
  BOYER_MOORE: 'Boyer–Moore',
  RABIN_KARP: 'Rabin–Karp',
} satisfies Record<AlgorithmTraceInspectorProps['algorithm'], string>;

export function AlgorithmTraceInspector({
  text,
  pattern,
  algorithm,
  events,
}: AlgorithmTraceInspectorProps) {
  const [currentIndex, setCurrentIndex] = useState(0);
  const [playing, setPlaying] = useState(false);
  const currentEvent = events[currentIndex];
  const safeTextIndex = currentEvent
    ? Math.max(0, Math.min(currentEvent.textIndex, text.length))
    : 0;
  const safePatternIndex = currentEvent
    ? Math.max(0, Math.min(currentEvent.patternIndex, pattern.length))
    : 0;
  const viewportStart = Math.max(0, safeTextIndex - 35);
  const viewportEnd = Math.min(text.length, safeTextIndex + 35);
  const visibleText = text.slice(viewportStart, viewportEnd);
  const hasLeadingEllipsis = viewportStart > 0;
  const hasTrailingEllipsis = viewportEnd < text.length;
  const focusIndexInWindow = safeTextIndex - viewportStart;
  const patternOffset = Math.max(
    0,
    safeTextIndex - safePatternIndex - viewportStart,
  ) + (hasLeadingEllipsis ? 3 : 0);

  useEffect(() => {
    setCurrentIndex(0);
    setPlaying(false);
  }, [algorithm, events, pattern, text]);

  useEffect(() => {
    if (!playing) return undefined;
    const timer = window.setTimeout(() => {
      if (currentIndex >= events.length - 1) {
        setPlaying(false);
      } else {
        setCurrentIndex(currentIndex + 1);
      }
    }, 600);
    return () => window.clearTimeout(timer);
  }, [currentIndex, events.length, playing]);

  function moveTo(index: number) {
    const boundedIndex = Math.max(0, Math.min(events.length - 1, index));
    setCurrentIndex(boundedIndex);
    if (boundedIndex === events.length - 1) setPlaying(false);
  }

  const canPlay = events.length > 1;

  return (
    <section className="card trace-inspector" aria-labelledby="trace-inspector-title">
      <header className="trace-inspector__header">
        <div>
          <p className="eyebrow">{algorithmLabels[algorithm]}</p>
          <h3 id="trace-inspector-title">Inspector de traza</h3>
        </div>
        <span className="trace-step-badge" aria-live="polite">
          Paso {events.length === 0 ? 0 : currentIndex + 1} de {events.length}
        </span>
      </header>

      <div className="trace-stepper" aria-label="Controles de reproducción">
        <button disabled={!events.length} onClick={() => moveTo(0)} type="button">Inicio</button>
        <button
          disabled={!events.length || currentIndex === 0}
          onClick={() => moveTo(currentIndex - 1)}
          type="button"
        >
          Anterior
        </button>
        <button
          disabled={!canPlay}
          onClick={() => {
            if (playing) {
              setPlaying(false);
            } else if (currentIndex >= events.length - 1) {
              setCurrentIndex(0);
              setPlaying(true);
            } else {
              setPlaying(true);
            }
          }}
          type="button"
        >
          {playing ? 'Pausa' : 'Reproducir'}
        </button>
        <button
          disabled={!events.length || currentIndex >= events.length - 1}
          onClick={() => moveTo(currentIndex + 1)}
          type="button"
        >
          Siguiente
        </button>
        <button
          disabled={!events.length}
          onClick={() => moveTo(events.length - 1)}
          type="button"
        >
          Fin
        </button>
      </div>

      {!pattern.trim() ? (
        <p className="empty-state">Ingresa un patrón no vacío para inspeccionar la búsqueda.</p>
      ) : events.length === 0 ? (
        <p className="empty-state">
          La API no devolvió eventos detallados para este algoritmo y artículo.
        </p>
      ) : (
        <>
          <div className="trace-alignment">
            <p className="trace-alignment__label">Texto P-TEXTO</p>
            <div className="trace-context-window" tabIndex={0}>
              <pre className="trace-alignment__line" aria-label="Ventana de texto con posición activa">
                <code>
                  {hasLeadingEllipsis && <span aria-hidden="true">...</span>}
                  {visibleText.split('').map((character, index) => (
                    <span
                      className={index === focusIndexInWindow ? 'trace-character--active' : undefined}
                      key={`${viewportStart + index}-${character}`}
                    >
                      {character}
                    </span>
                  ))}
                  {hasTrailingEllipsis && <span aria-hidden="true">...</span>}
                </code>
              </pre>
            </div>
            <p className="trace-alignment__label">Patrón alineado</p>
            <div className="trace-context-window trace-context-window--pattern" tabIndex={0}>
              <pre className="trace-alignment__pattern">
                <code>{' '.repeat(patternOffset)}{pattern}</code>
              </pre>
            </div>
            <p className="trace-alignment__hint">
              Ventana de contexto: caracteres {viewportStart + 1}–{viewportEnd} de {text.length}.
              {hasLeadingEllipsis || hasTrailingEllipsis
                ? ' Los puntos suspensivos indican texto omitido.'
                : ''}
            </p>
          </div>

          <article className="trace-event-card" aria-live="polite">
            <h4>Detalle del evento</h4>
            {currentEvent?.comparedCharText !== undefined
              && currentEvent.comparedCharPattern !== undefined && (
                <p>
                  Comparación:{' '}
                  <code>{JSON.stringify(currentEvent.comparedCharText)}</code>
                  {' vs '}
                  <code>{JSON.stringify(currentEvent.comparedCharPattern)}</code>
                  {' — '}
                  <strong>{currentEvent.match ? 'Coincide' : 'No coincide'}</strong>
                </p>
              )}
            {currentEvent?.hashState && (
              <dl className="trace-event-card__state">
                <div>
                  <dt>Hash del patrón</dt>
                  <dd><code>{currentEvent.hashState.patternHash}</code></dd>
                </div>
                <div>
                  <dt>Hash de la ventana</dt>
                  <dd><code>{currentEvent.hashState.textHash}</code></dd>
                </div>
                <div>
                  <dt>Comparación de hashes</dt>
                  <dd>
                    <strong>{currentEvent.match ? 'Coinciden' : 'Difieren'}</strong>
                    {currentEvent.match && ' (requiere verificación carácter por carácter)'}
                  </dd>
                </div>
              </dl>
            )}
            {currentEvent?.lpsState && (
              <div className="trace-event-card__education">
                <p>
                  Tabla LPS: <code>[{currentEvent.lpsState.join(', ')}]</code>
                </p>
                <p>
                  KMP usa el prefijo propio más largo que también es sufijo para decidir cuánto
                  retroceder en el patrón tras una discrepancia, sin retroceder en el texto.
                </p>
              </div>
            )}
            {currentEvent?.badCharacterTable && (
              <div className="trace-event-card__education">
                <p><strong>Regla del carácter malo (preprocesamiento)</strong></p>
                <dl className="trace-event-card__state">
                  {Object.entries(currentEvent.badCharacterTable).map(([character, lastIndex]) => (
                    <div key={character}>
                      <dt>Última posición de {JSON.stringify(character)}</dt>
                      <dd>{lastIndex}</dd>
                    </div>
                  ))}
                </dl>
                <p>
                  Boyer–Moore usa esta tabla para proponer un salto cuando un carácter no coincide.
                  La API entrega las tablas, pero no el detalle de cada comparación o salto.
                </p>
                {currentEvent.goodSuffixShifts && (
                  <p>
                    Desplazamientos de sufijo bueno: [
                    {currentEvent.goodSuffixShifts.join(', ')}].
                  </p>
                )}
                {currentEvent.shiftAfterMatch !== undefined && (
                  <p>
                    Desplazamiento preprocesado tras una coincidencia completa:{' '}
                    {currentEvent.shiftAfterMatch}.
                  </p>
                )}
              </div>
            )}
            {currentEvent?.hashState && (
              <p>
                Rabin–Karp actualiza el hash al deslizar la ventana; un hash igual señala una
                posible coincidencia que debe verificarse carácter por carácter.
              </p>
            )}
            {currentEvent?.shift !== undefined && currentEvent.shift > 0 && (
              <p>Desplazamiento aplicado: <strong>{currentEvent.shift}</strong></p>
            )}
            {algorithm === 'RABIN_KARP' && (
              <p>
                Inicio de ventana: <strong>{currentEvent?.textIndex ?? 0}</strong>
              </p>
            )}
            <p>{currentEvent?.commentary ?? 'El evento no incluye detalle adicional.'}</p>
          </article>
        </>
      )}
    </section>
  );
}
