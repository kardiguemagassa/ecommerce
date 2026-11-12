import { execFileSync } from 'node:child_process';
import { request, type APIRequestContext } from '@playwright/test';
import { ADMIN, BASE_URL, CUSTOMER, ROOT } from './env';

/**
 * The API as a user, with a bearer token (scripts/dev-token.sh, like the smoke test): sets up the data of a test
 * quickly, without going through the pages being tested.
 */
export async function apiAs(email: string): Promise<APIRequestContext> {
  const token = execFileSync('./scripts/dev-token.sh', [email], { cwd: ROOT, encoding: 'utf8' }).trim();
  return request.newContext({ baseURL: `${BASE_URL}/api/`, extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
}

export const adminApi = () => apiAs(ADMIN.email());
export const customerApi = () => apiAs(CUSTOMER.email);

async function json<T>(response: Awaited<ReturnType<APIRequestContext['get']>>, what: string): Promise<T> {
  if (!response.ok()) throw new Error(`${what}: HTTP ${response.status()} ${await response.text()}`);
  return (await response.json()) as T;
}

export interface Seeded {
  id: number;
  name: string;
}

export async function createCategory(api: APIRequestContext, name: string): Promise<Seeded> {
  return json(await api.post('categories', { data: { name } }), `category ${name}`);
}

export async function createProduct(
  api: APIRequestContext,
  product: { name: string; categoryId: number; price: number; stock: number; description?: string },
): Promise<Seeded> {
  return json(await api.post('products', { data: product }), `product ${product.name}`);
}

/** Alice's cart, emptied so that a test knows what it holds. */
export async function emptyCart(api: APIRequestContext): Promise<void> {
  const response = await api.delete('me/cart');
  if (!response.ok()) throw new Error(`empty cart: HTTP ${response.status()}`);
}

export async function addToCart(api: APIRequestContext, productId: number, quantity: number): Promise<void> {
  await json(await api.post('me/cart/items', { data: { productId, quantity } }), `add ${productId} to the cart`);
}

export async function placeOrder(api: APIRequestContext): Promise<Seeded> {
  return json(await api.post('me/orders'), 'order');
}

/** The order once confirmed: the confirmation runs after the commit, in the background (a few hundred ms). */
export async function waitConfirmed(api: APIRequestContext, orderId: number): Promise<void> {
  for (let attempt = 0; attempt < 40; attempt += 1) {
    const order = await json<{ status: string }>(await api.get(`me/orders/${orderId}`), `order ${orderId}`);
    if (order.status === 'CONFIRMED') return;
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`order ${orderId} not confirmed after 10 s`);
}

export async function findCustomer(api: APIRequestContext, email: string): Promise<Seeded> {
  const page = await json<{ content: { id: number; fullName: string; email: string }[] }>(
    await api.get('admin/customers', { params: { q: email } }),
    `customer ${email}`,
  );
  const customer = page.content.find((entry) => entry.email === email);
  if (!customer) throw new Error(`customer ${email} not found`);
  return { id: customer.id, name: customer.fullName };
}
