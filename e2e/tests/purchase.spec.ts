import { addToCart, adminApi, createCategory, createProduct, customerApi, emptyCart, type Seeded } from '../support/api';
import { CUSTOMER, RUN } from '../support/env';
import { expect, orderStatus, test } from '../support/fixtures';

test.use({ storageState: CUSTOMER.state });

let product: Seeded;
let scarce: Seeded; // its own stock: the first test orders some of the other one

test.beforeAll(async () => {
  const admin = await adminApi();
  const category = await createCategory(admin, `E2E Achat ${RUN}`);
  product = await createProduct(admin, { name: `E2E Lampe ${RUN}`, categoryId: category.id, price: 19.9, stock: 5 });
  scarce = await createProduct(admin, { name: `E2E Haltères ${RUN}`, categoryId: category.id, price: 119, stock: 2 });
  await admin.dispose();
});

test.beforeEach(async () => {
  const alice = await customerApi();
  await emptyCart(alice);
  await alice.dispose();
});

test('a customer adds a product, changes the quantity, orders and follows the order until it is confirmed', async ({ page }) => {
  await page.goto(`/produits/${product.id}`);
  await page.getByRole('button', { name: 'Ajouter au panier' }).click();
  await expect(page.getByText(`${product.name} ajouté au panier`)).toBeVisible();
  await expect(page.getByRole('link', { name: 'Panier, 1 article' })).toBeVisible();

  await page.getByRole('link', { name: 'Panier, 1 article' }).click();
  const quantity = page.getByRole('group', { name: `Quantité de ${product.name}` });
  await quantity.getByRole('button', { name: 'Augmenter la quantité' }).click();
  await expect(quantity.getByRole('status')).toHaveText('2');
  await expect(page.getByRole('link', { name: 'Panier, 2 articles' })).toBeVisible(); // the API's answer

  await page.getByRole('button', { name: 'Passer la commande' }).click();

  await expect(page).toHaveURL(/\/commandes\/\d+$/);
  const orderId = page.url().split('/').pop()!;
  await expect(page.getByRole('heading', { name: `Commande n° ${orderId}`, level: 1 })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Panier vide' })).toBeVisible();
  await expect(page.getByRole('row', { name: new RegExp(product.name) })).toContainText('39,80 €');
  // Confirmed asynchronously by the API (stock reserved, e-mail sent); the page polls until then.
  await expect(orderStatus(page)).toHaveText('Confirmée', { timeout: 30_000 });

  await page.getByRole('link', { name: 'Toutes mes commandes' }).click();
  await expect(page.getByRole('rowheader', { name: orderId })).toBeVisible();
});

test('a quantity above the stock is refused, and the cart goes back to what it was', async ({ page }) => {
  const alice = await customerApi();
  await addToCart(alice, scarce.id, 2); // the whole stock
  await alice.dispose();

  await page.goto('/panier');
  const quantity = page.getByRole('group', { name: `Quantité de ${scarce.name}` });
  await expect(quantity.getByRole('status')).toHaveText('2');
  await quantity.getByRole('button', { name: 'Augmenter la quantité' }).click();

  await expect(page.getByText('Stock insuffisant pour la quantité demandée.')).toBeVisible();
  await expect(quantity.getByRole('status')).toHaveText('2');
});
