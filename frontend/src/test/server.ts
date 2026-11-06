import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';

/** The API as the tests see it: anonymous visitor, empty cart, a small catalogue, unless a test says otherwise. */
export const anonymous = { authenticated: false, roles: [] };
export const alice = { authenticated: true, name: 'Alice Martin', email: 'alice@example.com', roles: ['CUSTOMER'] };
export const admin = { authenticated: true, name: 'Kardigué Magassa', email: 'admin@example.com', roles: ['ADMIN', 'CUSTOMER'] };

export const categories = [
  { id: 1, name: 'Informatique' },
  { id: 2, name: 'Audio' },
];

export const keyboard = {
  id: 10, name: 'Clavier mécanique TKL', description: 'Interrupteurs linéaires, AZERTY.', price: 89.9, stock: 24,
  categoryId: 1, categoryName: 'Informatique', imageUrl: '/media/products/keyboard.jpg',
};
export const mouse = { id: 11, name: 'Souris sans fil', price: 39.9, stock: 3, categoryId: 1, categoryName: 'Informatique' };
export const headset = { id: 12, name: 'Casque à réduction de bruit', price: 149, stock: 0, categoryId: 2, categoryName: 'Audio' };
export const products = [keyboard, mouse, headset];

export function pageOf<T>(content: T[], total = content.length, size = 12, number = 0) {
  return { content, page: { size, number, totalElements: total, totalPages: Math.ceil(total / size) } };
}

export const emptyCart = { customerId: 1, items: [], totalQuantity: 0, total: 0 };

export const server = setupServer(
  http.get('*/api/me/session', () => HttpResponse.json(anonymous)),
  http.get('*/api/me/cart', () => HttpResponse.json(emptyCart)),
  http.get('*/api/categories', () => HttpResponse.json(categories)),
  http.get('*/api/products/top-selling', () => HttpResponse.json([])),
  http.get('*/api/products/:id', ({ params }) => {
    const product = products.find((item) => item.id === Number(params.id));
    return product
      ? HttpResponse.json(product)
      : HttpResponse.json({ status: 404, title: 'Not Found' }, { status: 404 });
  }),
  http.get('*/api/products', () => HttpResponse.json(pageOf(products))),
);

export function sessionOf(session: object) {
  server.use(http.get('*/api/me/session', () => HttpResponse.json(session)));
}
