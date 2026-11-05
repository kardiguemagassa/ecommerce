import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';

/** The API as the tests see it: anonymous visitor and empty cart unless a test says otherwise. */
export const anonymous = { authenticated: false, roles: [] };
export const alice = { authenticated: true, name: 'Alice Martin', email: 'alice@example.com', roles: ['CUSTOMER'] };
export const admin = { authenticated: true, name: 'Kardigué Magassa', email: 'admin@example.com', roles: ['ADMIN', 'CUSTOMER'] };

export const server = setupServer(
  http.get('*/api/me/session', () => HttpResponse.json(anonymous)),
  http.get('*/api/me/cart', () => HttpResponse.json({ customerId: 1, items: [], totalQuantity: 0, total: 0 })),
);

export function sessionOf(session: object) {
  server.use(http.get('*/api/me/session', () => HttpResponse.json(session)));
}
