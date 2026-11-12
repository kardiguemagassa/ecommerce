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

/** Alice's cart: 2 keyboards and 1 mouse. */
export const cart = {
  customerId: 1,
  items: [
    { productId: 10, productName: keyboard.name, unitPrice: 89.9, quantity: 2, subtotal: 179.8, imageUrl: keyboard.imageUrl },
    { productId: 11, productName: mouse.name, unitPrice: 39.9, quantity: 1, subtotal: 39.9 },
  ],
  totalQuantity: 3,
  total: 219.7,
};

/** Noon UTC: the same day in every time zone a test may run in. */
export const order = {
  id: 1042, customerId: 1, status: 'CONFIRMED', totalAmount: 219.7, createdAt: '2026-11-05T12:00:00Z',
  items: [
    { productId: 10, productName: keyboard.name, unitPrice: 89.9, quantity: 2, subtotal: 179.8 },
    { productId: 11, productName: mouse.name, unitPrice: 39.9, quantity: 1, subtotal: 39.9 },
  ],
};
export const orderSummaries = [
  { id: 1042, status: 'PENDING', totalAmount: 219.7, createdAt: '2026-11-05T12:00:00Z' },
  { id: 1027, status: 'CONFIRMED', totalAmount: 99.8, createdAt: '2026-10-22T12:00:00Z' },
];

export const customers = [
  { id: 1, fullName: 'Alice Martin', email: 'alice@example.com', avatarUrl: '/media/avatars/alice.jpg' },
  { id: 2, fullName: 'Bob Durand', email: 'bob@example.com' },
];

export const noReviews = {
  rating: { count: 0 },
  distribution: [5, 4, 3, 2, 1].map((stars) => ({ stars, count: 0 })),
};

export const server = setupServer(
  http.get('*/api/me/session', () => HttpResponse.json(anonymous)),
  http.get('*/api/me/cart', () => HttpResponse.json(emptyCart)),
  http.get('*/api/me/orders', () => HttpResponse.json(pageOf(orderSummaries, orderSummaries.length, 10))),
  http.get('*/api/me/orders/:id', ({ params }) =>
    Number(params.id) === order.id
      ? HttpResponse.json(order)
      : HttpResponse.json({ status: 404, title: 'Not Found' }, { status: 404 }),
  ),
  http.get('*/api/categories', () => HttpResponse.json(categories)),
  http.get('*/api/admin/customers', () => HttpResponse.json(pageOf(customers, customers.length, 20))),
  http.get('*/api/admin/customers/:id', ({ params }) => HttpResponse.json(customers.find((customer) => customer.id === Number(params.id)))),
  http.get('*/api/admin/customers/:id/orders', () => HttpResponse.json(pageOf(orderSummaries, orderSummaries.length, 20))),
  http.get('*/api/admin/customers/:id/orders/:orderId', () => HttpResponse.json(order)),
  http.get('*/api/products/top-selling', () => HttpResponse.json([])),
  http.get('*/api/products/:id/reviews', () => HttpResponse.json(pageOf([], 0, 10))),
  http.get('*/api/products/:id/reviews/summary', () => HttpResponse.json(noReviews)),
  http.get('*/api/me/products/:id/review', () => HttpResponse.json({ allowed: false })),
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
