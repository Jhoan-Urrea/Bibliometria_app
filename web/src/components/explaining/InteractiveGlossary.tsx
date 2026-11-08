import { useMemo, useState } from 'react';
import { GLOSSARY_DATA, type GlossaryCategory } from './glossaryData';

const categories: Array<GlossaryCategory | 'Todas'> = [
  'Todas',
  'General / PLN',
  'Programación Dinámica',
  'Espacio Vectorial / IA',
  'Clustering Jerárquico',
  'String Matching',
];

function normalizeForSearch(value: string) {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase();
}

export function InteractiveGlossary() {
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<GlossaryCategory | 'Todas'>('Todas');

  const filteredTerms = useMemo(() => {
    const query = normalizeForSearch(searchTerm.trim());

    return GLOSSARY_DATA.filter((item) => {
      const matchesCategory =
        selectedCategory === 'Todas' || item.category === selectedCategory;
      const searchableContent = normalizeForSearch(
        [item.term, item.category, item.definition, item.formulaOrNotation ?? ''].join(' '),
      );
      return matchesCategory && searchableContent.includes(query);
    });
  }, [searchTerm, selectedCategory]);

  return (
    <section className="page-stack" aria-labelledby="glossary-title">
      <header className="glossary-header">
        <p className="eyebrow">Referencia académica</p>
        <h2 id="glossary-title">Glosario interactivo</h2>
        <p className="page-intro">
          Busca conceptos bibliométricos, de PLN y de los algoritmos implementados.
        </p>
      </header>

      <label className="field" htmlFor="glossary-search">
        <span className="field__label">Buscar en el glosario</span>
        <input
          className="field__control"
          id="glossary-search"
          onChange={(event) => setSearchTerm(event.currentTarget.value)}
          placeholder="Buscar término..."
          type="text"
          value={searchTerm}
        />
      </label>

      <div className="glossary-filters" role="group" aria-label="Filtrar por categoría">
        {categories.map((category) => (
          <button
            aria-pressed={selectedCategory === category}
            className="glossary-filter"
            key={category}
            onClick={() => setSelectedCategory(category)}
            type="button"
          >
            {category}
          </button>
        ))}
      </div>

      {filteredTerms.length === 0 ? (
        <p className="empty-state" role="status">
          No se encontraron términos para la búsqueda.
        </p>
      ) : (
        <div className="glossary-grid">
          {filteredTerms.map((item) => (
            <article className="card glossary-card" key={item.id}>
              <span className="glossary-card__category">{item.category}</span>
              <h3>{item.term}</h3>
              <p>{item.definition}</p>
              {item.formulaOrNotation && (
                <pre className="glossary-card__notation">
                  <code>{item.formulaOrNotation}</code>
                </pre>
              )}
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
