import { adminApi, createCategory, type Seeded } from '../support/api';
import { ADMIN, CUSTOMER, RUN } from '../support/env';
import { expect, expectAccessible, test } from '../support/fixtures';
import { pngFile } from '../support/png';

test.use({ storageState: ADMIN.state });

let category: Seeded;

test.beforeAll(async () => {
  const api = await adminApi();
  category = await createCategory(api, `E2E Admin ${RUN}`);
  await api.dispose();
});

test('an administrator creates a product with two views, and the shop shows them at once, full screen too', async ({ page }) => {
  const name = `E2E Bouilloire ${RUN}`;
  await page.goto('/admin');
  await expect(page).toHaveURL(/\/admin\/produits$/);

  await page.getByRole('button', { name: 'Nouveau produit' }).click();
  const drawer = page.getByRole('dialog', { name: 'Nouveau produit' });
  await drawer.getByRole('textbox', { name: 'Nom' }).fill(name);
  await drawer.getByRole('combobox', { name: 'Catégorie' }).click();
  await page.getByRole('option', { name: category.name }).click();
  await drawer.getByRole('textbox', { name: 'Prix (€)' }).fill('39,00');
  await drawer.getByRole('textbox', { name: 'Stock' }).fill('14');
  await drawer.getByRole('button', { name: 'Créer le produit' }).click();

  const editing = page.getByRole('dialog', { name: `Modifier « ${name} »` });
  await expect(editing).toBeVisible();
  const addView = editing.getByLabel('Ajouter une image');
  await addView.setInputFiles(pngFile('bouilloire.png', 800, 600));
  await expect(editing.getByRole('img', { name: 'Vue 1' })).toBeVisible();
  await addView.setInputFiles(pngFile('bouilloire-dos.png', 600, 800));
  await expect(editing.getByRole('img', { name: 'Vue 2' })).toBeVisible();
  await expect(editing.getByText('Principale', { exact: true })).toBeVisible();
  await expect(editing.getByText(/^2 sur 4/)).toBeVisible();
  await editing.getByRole('button', { name: 'Fermer' }).click();

  await page.goto(`/?q=${encodeURIComponent(name)}`);
  // The image of a card is hidden from screen readers (the name is read once, by the title link): found by its alt.
  const image = page.locator(`img[alt="Photo du produit ${name}"]`).first();
  await expect(image).toHaveAttribute('src', /\/media\/products\/.+\.jpg$/);
  await expect(image).toHaveJSProperty('complete', true);
  expect(await image.evaluate((element: HTMLImageElement) => element.naturalWidth)).toBeGreaterThan(0); // really served
  await expect(page.getByText('39,00 €').first()).toBeVisible();

  // The product page: the main image, a thumbnail for each view, the second one shown on demand.
  await page.getByRole('link', { name }).first().click();
  const views = page.getByRole('group', { name: 'Vues du produit' });
  await expect(views.getByRole('button')).toHaveCount(2);
  await expect(page.getByRole('img', { name: `Photo du produit ${name}, vue 1 sur 2` })).toBeVisible();
  await views.getByRole('button', { name: 'Voir la vue 2 sur 2' }).click();
  await expect(views.getByRole('button', { name: 'Voir la vue 2 sur 2' })).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByRole('img', { name: `Photo du produit ${name}, vue 2 sur 2` })).toBeVisible();
  await expectAccessible(page, 'the product page with its views');

  // Full screen: the views one by one, then back on the page with Escape.
  await page.getByRole('button', { name: 'Agrandir la vue 2 sur 2' }).click();
  const zoom = page.getByRole('dialog', { name: `Photos du produit ${name}` });
  await expect(zoom.getByText('2 / 2')).toBeVisible();
  await zoom.getByRole('button', { name: 'Vue précédente' }).click();
  await expect(zoom.getByText('1 / 2')).toBeVisible();
  await expectAccessible(page, 'the full-screen views');
  await page.keyboard.press('Escape');
  await expect(zoom).toBeHidden();
  await expect(views.getByRole('button', { name: 'Voir la vue 1 sur 2' })).toHaveAttribute('aria-pressed', 'true');
});

test('an administrator renames a category with the keyboard', async ({ page }) => {
  const renamed = `E2E Renommée ${RUN}`;
  await page.goto('/admin/categories');
  await page.getByRole('button', { name: `Renommer ${category.name}` }).click();
  const field = page.getByRole('textbox', { name: `Nouveau nom de ${category.name}` });
  await field.fill(renamed);
  await field.press('Enter');

  await expect(page.getByText(`Catégorie renommée en ${renamed}`)).toBeVisible();
  await expect(page.getByRole('button', { name: `Renommer ${renamed}` })).toBeVisible();
  category = { ...category, name: renamed };
});

test('an administrator finds a customer and their orders', async ({ page }) => {
  await page.goto('/admin/clients');
  await page.getByRole('searchbox', { name: 'Rechercher un client (nom ou e-mail)' }).fill('alice');
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\?q=alice$/);

  await page.getByRole('button', { name: `Voir le client ${CUSTOMER.name}` }).click();
  const drawer = page.getByRole('dialog', { name: CUSTOMER.name });
  await expect(drawer.getByText(CUSTOMER.email)).toBeVisible();
  await expect(drawer.getByRole('heading', { name: 'Commandes' })).toBeVisible();
});
