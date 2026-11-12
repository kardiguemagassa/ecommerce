import { act, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ORDER_POLL_MS } from '../api/orders';
import { notify } from '../app/notifications';
import { renderApp } from '../test/render';
import { alice, order, server, sessionOf } from '../test/server';

afterEach(() => {
  vi.useRealTimers();
});

describe('Order tracking page', () => {
  it('shows a confirmed order: status, steps, items and total', async () => {
    sessionOf(alice);
    renderApp('/commandes/1042');

    expect(await screen.findByRole('heading', { name: 'Commande n° 1042', level: 1 })).toBeInTheDocument();
    expect(screen.getByText(/^Passée le 5 novembre 2026 à/)).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Confirmée');
    expect(screen.getByText('Un e-mail de confirmation vous a été envoyé.')).toBeInTheDocument();
    const rows = within(screen.getByRole('table')).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('Clavier mécanique TKL2');
    expect(rows[1]).toHaveTextContent('179,80 €');
    expect(within(rows[3]!).getByRole('rowheader')).toHaveTextContent('Total');
    expect(rows[3]).toHaveTextContent('219,70 €');
    expect(screen.getByRole('link', { name: 'Toutes mes commandes' })).toHaveAttribute('href', '/commandes');
    await waitFor(() => expect(document.title).toBe('Commande n° 1042 · Comptoir'));
  });

  it('leads from each item of a confirmed order to its review: to give, or to change', async () => {
    sessionOf(alice);
    server.use(
      http.get('*/api/me/products/10/review', () =>
        HttpResponse.json({ allowed: true, review: { id: 5, rating: 4, author: 'Alice M.', createdAt: order.createdAt, updatedAt: order.createdAt } }),
      ),
      http.get('*/api/me/products/11/review', () => HttpResponse.json({ allowed: true })),
    );
    renderApp('/commandes/1042');

    expect(await screen.findByRole('link', { name: 'Modifier mon avis sur Clavier mécanique TKL' })).toHaveAttribute('href', '/produits/10#avis');
    expect(await screen.findByRole('link', { name: 'Donner mon avis sur Souris sans fil' })).toHaveAttribute('href', '/produits/11#avis');
  });

  it('offers no review while the order is pending', async () => {
    sessionOf(alice);
    server.use(
      http.get('*/api/me/orders/1042', () => HttpResponse.json({ ...order, status: 'PENDING' })),
      http.get('*/api/me/products/:id/review', () => HttpResponse.json({ allowed: true })),
    );
    renderApp('/commandes/1042');

    expect(await screen.findByText('En attente de confirmation…')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /mon avis/ })).not.toBeInTheDocument();
  });

  it('follows a pending order every 2 s until it is confirmed, then stops asking', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const success = vi.spyOn(notify, 'success');
    sessionOf(alice);
    let calls = 0;
    server.use(
      http.get('*/api/me/orders/1042', () => {
        calls += 1;
        return HttpResponse.json({ ...order, status: calls < 3 ? 'PENDING' : 'CONFIRMED' });
      }),
    );
    renderApp('/commandes/1042');

    expect(await screen.findByText('En attente de confirmation…')).toBeInTheDocument();
    expect(screen.getByText('Cette page se met à jour seule dès que la commande est confirmée.')).toBeInTheDocument();

    await act(() => vi.advanceTimersByTimeAsync(ORDER_POLL_MS));
    await act(() => vi.advanceTimersByTimeAsync(ORDER_POLL_MS));

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Confirmée'));
    // The toast itself is timed by the Snackbar, whose clock is faked here: the call is what matters.
    expect(success).toHaveBeenCalledWith('Commande n° 1042 confirmée');
    const confirmedAfter = calls;
    await act(() => vi.advanceTimersByTimeAsync(ORDER_POLL_MS * 3));
    expect(calls).toBe(confirmedAfter);
  });

  it('says an unknown order is not found, inside the page, without error toast', async () => {
    sessionOf(alice);
    renderApp('/commandes/999');

    expect(await screen.findByRole('heading', { name: 'Commande introuvable' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Toutes mes commandes' })).toHaveAttribute('href', '/commandes');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('treats a malformed address as an unknown order', async () => {
    sessionOf(alice);
    renderApp('/commandes/abc');

    expect(await screen.findByRole('heading', { name: 'Commande introuvable' })).toBeInTheDocument();
  });
});
