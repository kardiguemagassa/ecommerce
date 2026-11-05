import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../test/server';
import { api } from './client';
import { ApiError, unwrap } from './problem';

describe('API client', () => {
  it('sends the CSRF token of the cookie with every write', async () => {
    let header: string | null = 'unset';
    server.use(
      http.post('*/api/me/cart/items', ({ request }) => {
        header = request.headers.get('X-XSRF-TOKEN');
        return HttpResponse.json({ customerId: 1, items: [], totalQuantity: 1, total: 10 });
      }),
    );
    document.cookie = 'XSRF-TOKEN=token-123; path=/';

    await unwrap(api.POST('/api/me/cart/items', { body: { productId: 1, quantity: 1 } }));

    expect(header).toBe('token-123');
  });

  it('does not send it with reads', async () => {
    let header: string | null = 'unset';
    server.use(
      http.get('*/api/categories', ({ request }) => {
        header = request.headers.get('X-XSRF-TOKEN');
        return HttpResponse.json([]);
      }),
    );
    document.cookie = 'XSRF-TOKEN=token-123; path=/';

    await unwrap(api.GET('/api/categories'));

    expect(header).toBeNull();
  });

  it('turns an RFC 9457 problem into an ApiError with a French message and the trace id', async () => {
    server.use(
      http.post('*/api/me/orders', () =>
        HttpResponse.json(
          { title: 'Insufficient stock', status: 409, detail: "Insufficient stock for 'Mouse'", traceId: 'abc123' },
          { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    const error = await unwrap(api.POST('/api/me/orders')).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(409);
    expect((error as ApiError).message).toBe('Stock insuffisant pour la quantité demandée.');
    expect((error as ApiError).traceId).toBe('abc123');
  });

  it('maps image errors by their code', async () => {
    server.use(
      http.put('*/api/me/avatar', () =>
        HttpResponse.json({ status: 415, code: 'UNSUPPORTED_IMAGE' }, { status: 415 }),
      ),
    );
    const body = new FormData();
    body.append('file', new Blob(['not an image']), 'notes.txt');

    const error = await unwrap(
      api.PUT('/api/me/avatar', { body: { file: 'notes.txt' }, bodySerializer: () => body }),
    ).catch((e: unknown) => e);

    expect((error as ApiError).message).toBe('Format non accepté : JPEG, PNG ou WebP uniquement.');
  });

  it('reports an unreachable API as unavailable', async () => {
    server.use(http.get('*/api/categories', () => HttpResponse.error()));

    const error = await unwrap(api.GET('/api/categories')).catch((e: unknown) => e);

    expect((error as ApiError).status).toBe(0);
    expect((error as ApiError).message).toBe('Le service est momentanément indisponible. Réessayez dans un instant.');
  });
});
