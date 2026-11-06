import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { renderApp } from '../test/render';
import { alice, orderSummaries, pageOf, server, sessionOf } from '../test/server';

describe('My orders page', () => {
  it('lists the orders, most recent first, each leading to its tracking', async () => {
    sessionOf(alice);
    renderApp('/commandes');

    const rows = within(await screen.findByRole('table')).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('1042');
    expect(rows[1]).toHaveTextContent('En attente');
    expect(rows[1]).toHaveTextContent('219,70 €');
    expect(rows[2]).toHaveTextContent('Confirmée');
    expect(screen.getByRole('link', { name: 'Suivre la commande n° 1042' })).toHaveAttribute('href', '/commandes/1042');
    expect(screen.getByRole('link', { name: 'Détail de la commande n° 1027' })).toHaveAttribute('href', '/commandes/1027');
    expect(screen.getByText('2 commandes · page 1 sur 1')).toBeInTheDocument();
  });

  it('takes the page from the address (pages from 1 for the user, from 0 for the API)', async () => {
    sessionOf(alice);
    let asked: URLSearchParams | undefined;
    server.use(
      http.get('*/api/me/orders', ({ request }) => {
        asked = new URL(request.url).searchParams;
        return HttpResponse.json(pageOf(orderSummaries, 12, 10, 1));
      }),
    );
    renderApp('/commandes?page=2');

    expect(await screen.findByText('12 commandes · page 2 sur 2')).toBeInTheDocument();
    expect(asked?.get('page')).toBe('1');
    expect(asked?.get('size')).toBe('10');
    expect(screen.getByRole('link', { name: 'Aller à la page 1' })).toHaveAttribute('href', '/commandes');
  });

  it('says there is no order yet', async () => {
    sessionOf(alice);
    server.use(http.get('*/api/me/orders', () => HttpResponse.json(pageOf([], 0, 10))));
    renderApp('/commandes');

    expect(await screen.findByRole('heading', { name: "Vous n'avez pas encore passé de commande" })).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});
