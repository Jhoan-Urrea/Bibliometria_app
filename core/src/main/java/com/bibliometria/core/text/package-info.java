/**
 * Preprocesamiento determinista del texto bibliográfico, independiente de infraestructura y
 * limitado a las capacidades del JDK.
 *
 * <p>{@link com.bibliometria.core.text.TextPreprocessor} separa dos perfiles con propósitos
 * distintos. P-TEXTO aplica normalización Unicode NFKC, une palabras divididas por guion al final
 * de línea y colapsa espacios, pero conserva mayúsculas, puntuación y stopwords. Así mantiene una
 * representación legible para búsqueda exacta y evita que filtrar términos altere el texto fuente.
 * P-TOKENS opera después sobre esa salida: normaliza a minúsculas con {@code Locale.ROOT}, define
 * límites de token mediante caracteres Unicode y excluye stopwords; esta normalización completa
 * garantiza comparaciones y conteos consistentes.
 *
 * <p>Las salidas matemáticas por documento tienen las siguientes representaciones Java:
 *
 * <ul>
 *   <li>{@code Tok(d)}: {@link java.util.List}{@code <String>} en {@link
 *       com.bibliometria.core.text.ProcessedDocument#tokens()}, en orden y con repeticiones.
 *   <li>{@code Set(d)}: {@link java.util.Set}{@code <String>} en {@link
 *       com.bibliometria.core.text.ProcessedDocument#conjunto()}, sin duplicados y en orden de
 *       primera aparición.
 *   <li>{@code f(t,d)}: {@link java.util.Map}{@code <String, Integer>} en {@link
 *       com.bibliometria.core.text.ProcessedDocument#frecuencias()}, donde cada valor es la
 *       cantidad de apariciones del término en {@code Tok(d)}.
 *   <li>{@code Cad(d)}: {@link java.lang.String} en {@link
 *       com.bibliometria.core.text.ProcessedDocument#cadenaCanonica()}, con los tokens concatenados
 *       por un espacio y en el mismo orden que {@code Tok(d)}.
 * </ul>
 *
 * <p>Las colecciones del resultado son inmutables. El texto y los tokens excluyen universos
 * diferentes intencionalmente: P-TEXTO preserva el contenido para inspección, mientras P-TOKENS
 * ofrece la forma normalizada que consumen los cálculos bibliométricos.
 */
package com.bibliometria.core.text;
