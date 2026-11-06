import { expect, test, type Page } from '@playwright/test';
import { addToCart, adminApi, customerApi, emptyCart, placeOrder } from '../support/api';
import { ADMIN, CUSTOMER, ROOT } from '../support/env';

/**
 * Screenshots of the README (docs/screenshots), on the demo data of scripts/demo-data.py: npm run screenshots.
 * Not a test: never part of `npm run e2e`.
 */
const OUT = `${ROOT}/docs/screenshots`;

interface DemoProduct {
  id: number;
  name: string;
}

async function demo() {
  const api = await adminApi();
  const categories = (await (await api.get('categories')).json()) as { id: number; name: string }[];
  const computing = categories.find((category) => category.name === 'Informatique');
  const page = (await (await api.get('products', { params: { name: 'Clavier mécanique TKL', size: 5 } })).json()) as { content: DemoProduct[] };
  const keyboard = page.content.find((product) => product.name === 'Clavier mécanique TKL');
  const others = (await (await api.get('products', { params: { categoryId: computing?.id ?? 0, size: 20 } })).json()) as { content: DemoProduct[] };
  await api.dispose();
  if (!computing || !keyboard) throw new Error('No demo data: run ./scripts/demo-data.py first');
  return { computing, keyboard, mouse: others.content.find((product) => product.name === 'Souris sans fil') ?? keyboard };
}

/** Pictures loaded, fonts ready, no animation running: the screenshot shows the page at rest. */
async function settle(page: Page) {
  await page.waitForLoadState('networkidle');
  await page.evaluate(async () => {
    await document.fonts.ready;
    await Promise.all(Array.from(document.images).filter((image) => !image.complete).map((image) => new Promise((done) => image.addEventListener('load', done, { once: true }))));
  });
  await page.waitForFunction(() => document.getAnimations().every((animation) => animation.playState !== 'running'));
}

async function shot(page: Page, name: string) {
  await settle(page);
  await page.screenshot({ path: `${OUT}/${name}.png` });
}

test('visitor pages', async ({ browser }) => {
  const { computing, keyboard } = await demo();
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  await page.goto(`/?categorie=${computing.id}`);
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await shot(page, 'catalogue');

  await page.goto(`/produits/${keyboard.id}`);
  await expect(page.getByRole('heading', { name: keyboard.name })).toBeVisible();
  await shot(page, 'product');

  await page.goto('/oauth2/authorization/keycloak');
  await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible();
  await shot(page, 'login');
  await page.close();
});

test('customer pages', async ({ browser }) => {
  const { keyboard, mouse } = await demo();
  const alice = await customerApi();
  await emptyCart(alice);
  await addToCart(alice, keyboard.id, 1);
  const order = await placeOrder(alice);
  await addToCart(alice, keyboard.id, 1);
  await addToCart(alice, mouse.id, 2);
  await alice.dispose();

  const context = await browser.newContext({ storageState: CUSTOMER.state, viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  await page.goto('/panier');
  await expect(page.getByRole('button', { name: 'Passer la commande' })).toBeVisible();
  await shot(page, 'cart');

  await page.goto(`/commandes/${order.id}`);
  await expect(page.getByRole('status').filter({ hasText: /^Confirmée$/ })).toBeVisible({ timeout: 30_000 });
  await shot(page, 'order');
  await context.close();

  const phone = await browser.newContext({ storageState: CUSTOMER.state, viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
  const mobile = await phone.newPage();
  await mobile.goto(`/produits/${keyboard.id}`);
  await expect(mobile.getByRole('heading', { name: keyboard.name })).toBeVisible();
  await shot(mobile, 'mobile-product');
  await phone.close();
});

test('admin space', async ({ browser }) => {
  const { keyboard } = await demo();
  const context = await browser.newContext({ storageState: ADMIN.state, viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  await page.goto(`/admin/produits?q=${encodeURIComponent(keyboard.name)}&produit=${keyboard.id}`);
  await expect(page.getByRole('dialog', { name: `Modifier « ${keyboard.name} »` })).toBeVisible();
  await shot(page, 'admin');
  await context.close();
});
