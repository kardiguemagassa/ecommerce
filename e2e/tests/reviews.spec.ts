import { addToCart, adminApi, createCategory, createProduct, customerApi, emptyCart, placeOrder, waitConfirmed, type Seeded } from '../support/api';
import { CUSTOMER, RUN } from '../support/env';
import { expect, expectAccessible, test } from '../support/fixtures';

let bought: Seeded;
let notBought: Seeded;

test.beforeAll(async () => {
  const admin = await adminApi();
  const category = await createCategory(admin, `E2E Avis ${RUN}`);
  bought = await createProduct(admin, { name: `E2E Théière ${RUN}`, categoryId: category.id, price: 29, stock: 10 });
  notBought = await createProduct(admin, { name: `E2E Tasse ${RUN}`, categoryId: category.id, price: 9, stock: 10 });
  await admin.dispose();

  const alice = await customerApi();
  await emptyCart(alice);
  await addToCart(alice, bought.id, 1);
  await waitConfirmed(alice, (await placeOrder(alice)).id);
  await alice.dispose();
});

test.describe('a customer', () => {
  test.use({ storageState: CUSTOMER.state });

  test('reviews a product they bought, and the shop shows it with its stars', async ({ page }) => {
    await page.goto(`/produits/${bought.id}`);
    const form = page.getByRole('form', { name: 'Donner mon avis' });
    await form.getByRole('radio', { name: '4 étoiles' }).check({ force: true }); // the radio is visually hidden under its star
    await form.getByRole('textbox', { name: 'Commentaire (facultatif)' }).fill('Verse sans goutter.');
    await expectAccessible(page, 'the product page with the review form');
    await form.getByRole('button', { name: 'Publier mon avis' }).click();
    await expect(page.getByText('Merci, votre avis est publié')).toBeVisible();

    const reviews = page.getByRole('region', { name: 'Avis clients' });
    await expect(reviews.getByText('Verse sans goutter.')).toBeVisible();
    await expect(reviews.getByText('Alice M.')).toBeVisible();
    await expect(reviews.getByText('Achat vérifié')).toBeVisible();
    await expect(page.getByRole('link', { name: '1 avis' })).toBeVisible();
    await expect(page.getByRole('form', { name: 'Mon avis' })).toBeVisible();

    await page.goto(`/?q=${encodeURIComponent(bought.name)}`);
    await expect(page.getByRole('img', { name: 'Note : 4 sur 5, 1 avis' })).toBeVisible();
  });

  test('is told why they cannot review a product they did not buy', async ({ page }) => {
    await page.goto(`/produits/${notBought.id}`);
    await expect(page.getByText(/Seuls les clients ayant commandé ce produit/)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Publier mon avis' })).toHaveCount(0);
  });
});

test('a visitor reads the reviews, and is offered to sign in to write one', async ({ page }) => {
  await page.goto(`/produits/${bought.id}`);
  await expect(page.getByRole('button', { name: 'Se connecter pour donner mon avis' })).toBeVisible();
  await expectAccessible(page, 'the product page of a visitor, with its reviews');
});
