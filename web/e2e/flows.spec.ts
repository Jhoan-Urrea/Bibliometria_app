import { expect, test } from '@playwright/test';

test('selecciona dos artículos y calcula su similitud', async ({ page }) => {
  const failedRequests: string[] = [];
  page.on('requestfailed', (request) => failedRequests.push(request.url()));
  await page.goto('/#similitud');

  await page.getByLabel('Artículo A').selectOption('A01');
  await page.getByLabel('Artículo B').selectOption('A02');
  await page.getByLabel('Algoritmo').selectOption('levenshtein');
  await page.getByRole('button', { name: 'Calcular similitud' }).click();

  await expect(page.getByRole('heading', { name: 'Resultado' })).toBeVisible();
  await expect(page.getByText('Algoritmo: levenshtein')).toBeVisible();
  expect(failedRequests).toEqual([]);
});

test('cambia el criterio y actualiza el dendrograma', async ({ page }) => {
  const clusteringRequests: Array<{ clusterCount: number; linkage: string }> = [];
  const failedRequests: string[] = [];
  page.on('requestfailed', (request) => failedRequests.push(request.url()));
  page.on('request', (request) => {
    if (request.url().endsWith('/api/clustering') && request.method() === 'POST') {
      clusteringRequests.push(request.postDataJSON() as { clusterCount: number; linkage: string });
    }
  });
  await page.goto('/#clustering');

  const linkage = page.getByLabel('Criterio de enlace');
  await linkage.selectOption('single');
  await page.getByRole('button', { name: 'Generar agrupamiento' }).click();
  await expect(page.getByRole('img', { name: 'Dendrograma de agrupamiento' })).toBeVisible();
  await expect(page.locator('.dendrogram desc')).toContainText('método single');

  await linkage.selectOption('complete');
  await page.getByRole('button', { name: 'Generar agrupamiento' }).click();
  await expect(page.locator('.dendrogram desc')).toContainText('método complete');
  expect(clusteringRequests).toEqual([
    { clusterCount: 3, linkage: 'single' },
    { clusterCount: 3, linkage: 'complete' },
  ]);
  expect(failedRequests).toEqual([]);
});

test('busca un patrón y muestra coincidencias y pasos de traza', async ({ page }) => {
  const failedRequests: string[] = [];
  page.on('requestfailed', (request) => failedRequests.push(request.url()));
  await page.goto('/#busqueda');

  await page.getByLabel('Patrón exacto').fill('artificial intelligence');
  await page.getByRole('button', { name: 'Buscar patrón y ver trazas' }).click();

  await expect(page.getByRole('heading', { name: 'Traza para “artificial intelligence”' }))
    .toBeVisible();
  await expect(page.getByRole('heading', { name: 'A01' })).toBeVisible();
  await page.getByRole('button', { name: 'Ver traza de A01' }).click();
  await page.getByText(/Pasos de los algoritmos/).click();
  await expect(page.getByText(/KMP · tablaLPS/)).toBeVisible();
  expect(failedRequests).toEqual([]);
});
