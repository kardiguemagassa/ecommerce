import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { renderApp } from '../../test/render';
import { admin, customers, pageOf, server, sessionOf } from '../../test/server';

describe('Admin: customers and best sellers', () => {
  it('searches the customers by name or e-mail, through the address', async () => {
    const user = userEvent.setup();
    sessionOf(admin);
    let asked: URLSearchParams | undefined;
    server.use(
      http.get('*/api/admin/customers', ({ request }) => {
        asked = new URL(request.url).searchParams;
        return HttpResponse.json(pageOf(asked.get('q') ? [customers[1]] : customers, asked.get('q') ? 1 : 2, 20));
      }),
    );
    const { router } = renderApp('/admin/clients');

    expect(await screen.findByRole('rowheader', { name: 'Alice Martin' })).toBeInTheDocument();
    await user.type(screen.getByRole('searchbox', { name: 'Rechercher un client (nom ou e-mail)' }), 'bob{Enter}');

    await waitFor(() => expect(screen.queryByRole('rowheader', { name: 'Alice Martin' })).not.toBeInTheDocument());
    expect(router.state.location.search).toBe('?q=bob');
    expect(asked?.get('q')).toBe('bob');
    expect(asked?.get('page')).toBe('0');
    expect(screen.getByText('1 résultat · page 1 sur 1')).toBeInTheDocument();
  });

  it("shows a customer's orders, each unfolding to its items", async () => {
    const user = userEvent.setup();
    sessionOf(admin);
    renderApp('/admin/clients');

    await user.click(await screen.findByRole('button', { name: 'Voir le client Alice Martin' }));
    const drawer = await screen.findByRole('dialog', { name: 'Alice Martin' });
    expect(within(drawer).getByText('alice@example.com')).toBeInTheDocument();

    await user.click(await within(drawer).findByRole('button', { name: 'Commande n° 1042' }));
    expect(await within(drawer).findByRole('rowheader', { name: 'Clavier mécanique TKL' })).toBeInTheDocument();
    expect(within(drawer).getByText('179,80 €')).toBeInTheDocument();
  });

  it('ranks the best sellers', async () => {
    sessionOf(admin);
    let limit: string | null = null;
    server.use(
      http.get('*/api/products/top-selling', ({ request }) => {
        limit = new URL(request.url).searchParams.get('limit');
        return HttpResponse.json([
          { productId: 10, productName: 'Clavier mécanique TKL', totalQuantity: 10 },
          { productId: 11, productName: 'Souris sans fil', totalQuantity: 4 },
        ]);
      }),
    );
    renderApp('/admin/ventes');

    const rows = within(await screen.findByRole('table')).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('1Clavier mécanique TKL10');
    expect(rows[2]).toHaveTextContent('2Souris sans fil4');
    expect(limit).toBe('10');
    await waitFor(() => expect(document.title).toBe('Meilleures ventes · Administration · Comptoir'));
  });
});
