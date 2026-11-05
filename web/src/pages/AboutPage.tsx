export function AboutPage() {
  return (
    <section className="page-stack" aria-labelledby="about-title">
      <h1 id="about-title">Acerca de Bibliometría</h1>
      <article className="card prose">
        <p>
          Aplicativo para explorar un corpus bibliográfico mediante similitud, agrupamiento
          jerárquico y búsqueda.
        </p>
        <p>
          Las respuestas y los algoritmos se consumen desde la API. El cliente no reproduce cálculos
          del núcleo.
        </p>
      </article>
    </section>
  );
}
