import { addToCart, adminApi, createCategory, createProduct, customerApi, emptyCart, placeOrder, waitConfirmed, writeReview, type Seeded } from '../support/api';
import { ADMIN, BASE_URL, CUSTOMER, RUN } from '../support/env';
import { expect, expectAccessible, test } from '../support/fixtures';

let bought: Seeded;
let notBought: Seeded;
let orderId: number;

test.beforeAll(async () => {
  const admin = await adminApi();
  const category = await createCategory(admin, `E2E Avis ${RUN}`);
  bought = await createProduct(admin, { name: `E2E Théière ${RUN}`, categoryId: category.id, price: 29, stock: 10 });
  notBought = await createProduct(admin, { name: `E2E Tasse ${RUN}`, categoryId: category.id, price: 9, stock: 10 });
  await admin.dispose();

  const alice = await customerApi();
  await emptyCart(alice);
  await addToCart(alice, bought.id, 1);
  orderId = (await placeOrder(alice)).id;
  await waitConfirmed(alice, orderId);
  await alice.dispose();
});

test.describe('a customer', () => {
  test.use({ storageState: CUSTOMER.state });

  test('goes from their confirmed order to the review form of what they bought', async ({ page }) => {
    await page.goto(`/commandes/${orderId}`);
    await page.getByRole('link', { name: `Donner mon avis sur ${bought.name}` }).click();
    await expect(page).toHaveURL(new RegExp(`/produits/${bought.id}#avis$`));
    await expect(page.getByRole('heading', { name: 'Avis clients' })).toBeInViewport(); // scrolled to the section
    await expect(page.getByRole('form', { name: 'Donner mon avis' })).toBeVisible();
  });

  test('reviews a product they bought, and the shop shows it with its stars', async ({ page }) => {
    await page.goto(`/produits/${bought.id}`);
    const form = page.getByRole('form', { name: 'Donner mon avis' });
    // The radio is visually hidden: a click on its star (its label) chooses it, as a customer does
    const fourStars = form.getByRole('radio', { name: '4 étoiles' });
    await form.locator(`label[for="${await fourStars.getAttribute('id')}"]`).click();
    await expect(fourStars).toBeChecked();
    await form.getByRole('textbox', { name: 'Commentaire (facultatif)' }).fill('Verse sans goutter.');
    await expectAccessible(page, 'the product page with the review form');
    await form.getByRole('button', { name: 'Publier mon avis' }).click();
    await expect(page.getByText('Merci, votre avis est publié')).toBeVisible();

    // The published review, in the list (the form below keeps the same comment in its text field)
    const published = page.getByRole('region', { name: 'Avis clients' }).getByRole('listitem').filter({ hasText: 'Verse sans goutter.' });
    await expect(published).toBeVisible();
    await expect(published.getByText('Alice M.')).toBeVisible();
    await expect(published.getByText('Achat vérifié')).toBeVisible();
    await expect(page.getByRole('link', { name: '1 avis' })).toBeVisible();
    await expect(page.getByRole('form', { name: 'Mon avis' })).toBeVisible();

    await page.goto(`/?q=${encodeURIComponent(bought.name)}`);
    await expect(page.getByRole('img', { name: 'Note : 4 sur 5, 1 avis' })).toBeVisible();
  });

  test('is told why they cannot review a product they did not buy', async ({ page }) => {
    await page.goto(`/produits/${notBought.id}`);
    await expect(page.getByText(/Seuls les clients ayant commandé ce produit/)).toBeVisible();
    await expect(page.getByRole('link', { name: 'Donner le premier avis' })).toHaveAttribute('href', '#avis');
    await expect(page.getByRole('button', { name: 'Publier mon avis' })).toHaveCount(0);
  });
});

test('a visitor reads the reviews, and is offered to sign in to write one', async ({ page }) => {
  await page.goto(`/produits/${bought.id}`);
  await expect(page.getByRole('button', { name: 'Se connecter pour donner mon avis' })).toBeVisible();
  await expectAccessible(page, 'the product page of a visitor, with its reviews');
});

test.describe('the admin', () => {
  test.use({ storageState: ADMIN.state });

  test('hides a review: the shop no longer shows it, nor counts it in the rating', async ({ page, browser }) => {
    // The review written through the pages above, written again here: this test also runs alone (a retry)
    const alice = await customerApi();
    await writeReview(alice, bought.id, 4, 'Verse sans goutter.');
    await alice.dispose();

    await page.goto('/admin/avis');
    const row = page.getByRole('row').filter({ hasText: bought.name });
    await expect(row.getByText('Verse sans goutter.')).toBeVisible();
    await expectAccessible(page, 'the reviews moderation table');
    await row.getByRole('button', { name: `Masquer l'avis de ${CUSTOMER.name} sur ${bought.name}` }).click();
    await expect(page.getByText(/Avis masqué/)).toBeVisible();
    await expect(row.getByText('Masqué', { exact: true })).toBeVisible();

    await page.getByRole('button', { name: 'Masqués' }).click();
    await expect(page).toHaveURL(/statut=masques/);
    await expect(page.getByRole('row').filter({ hasText: bought.name })).toBeVisible();

    // A visitor: an empty state, explicitly (browser.newContext() would take the admin session of test.use)
    const visitor = await browser.newContext({ baseURL: BASE_URL, storageState: { cookies: [], origins: [] } });
    const shop = await visitor.newPage();
    await shop.goto(`/produits/${bought.id}`);
    await expect(shop.getByRole('button', { name: 'Se connecter pour donner mon avis' })).toBeVisible();
    await expect(shop.getByText('Verse sans goutter.')).toHaveCount(0);
    await expect(shop.getByRole('link', { name: '1 avis' })).toHaveCount(0);
    await expect(shop.getByRole('link', { name: 'Donner le premier avis' })).toBeVisible(); // its only review is hidden
    await visitor.close();
  });
});
