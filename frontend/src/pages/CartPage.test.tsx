import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { renderApp } from '../test/render';
import { alice, cart, emptyCart, order, server, sessionOf } from '../test/server';

function aliceWithCart(current: object = cart) {
  sessionOf(alice);
  server.use(http.get('*/api/me/cart', () => HttpResponse.json(current)));
}

const quantityOf = (name: string) => screen.getByRole('group', { name: `Quantité de ${name}` });
const insufficientStock = { status: 409, title: 'Insufficient stock', detail: "Insufficient stock for 'Souris sans fil': requested 2, available 1" };

describe('Cart page', () => {
  it('shows the lines and the totals', async () => {
    aliceWithCart();
    renderApp('/panier');

    expect(await screen.findByRole('link', { name: 'Clavier mécanique TKL' })).toHaveAttribute('href', '/produits/10');
    expect(screen.getByText("89,90 € l'unité")).toBeInTheDocument();
    expect(screen.getByText('179,80 €')).toBeInTheDocument();
    expect(screen.getByText('3 articles')).toBeInTheDocument();
    expect(screen.getAllByText('219,70 €')).toHaveLength(2); // articles and total
    expect(within(quantityOf('Souris sans fil')).getByRole('status')).toHaveTextContent('1');
    await waitFor(() => expect(document.title).toBe('Mon panier · Comptoir'));
  });

  it('changes a quantity at once, then goes back with a toast when the stock is short', async () => {
    const user = userEvent.setup();
    aliceWithCart();
    let answer!: () => void;
    let sent: unknown;
    server.use(
      http.put('*/api/me/cart/items/11', async ({ request }) => {
        sent = await request.json();
        await new Promise<void>((resolve) => (answer = resolve));
        return HttpResponse.json(insufficientStock, { status: 409 });
      }),
    );
    renderApp('/panier');

    await user.click(within(await screen.findByRole('group', { name: 'Quantité de Souris sans fil' })).getByRole('button', { name: 'Augmenter la quantité' }));

    // Before the API answers: new quantity, new totals.
    expect(within(quantityOf('Souris sans fil')).getByRole('status')).toHaveTextContent('2');
    expect(screen.getAllByText('259,60 €')).toHaveLength(2);
    await waitFor(() => expect(sent).toEqual({ quantity: 2 }));

    answer();
    expect(await screen.findByText('Stock insuffisant pour la quantité demandée.')).toBeInTheDocument();
    expect(within(quantityOf('Souris sans fil')).getByRole('status')).toHaveTextContent('1');
    expect(screen.getAllByText('219,70 €')).toHaveLength(2);
  });

  it('keeps the cart answered by the API, header badge included', async () => {
    const user = userEvent.setup();
    aliceWithCart();
    const [keyboardLine, mouseLine] = cart.items;
    server.use(
      http.put('*/api/me/cart/items/10', () =>
        HttpResponse.json({ ...cart, items: [{ ...keyboardLine, quantity: 3, subtotal: 269.7 }, mouseLine], totalQuantity: 4, total: 309.6 }),
      ),
    );
    renderApp('/panier');

    await user.click(within(await screen.findByRole('group', { name: 'Quantité de Clavier mécanique TKL' })).getByRole('button', { name: 'Augmenter la quantité' }));

    expect(await screen.findByRole('link', { name: 'Panier, 4 articles' })).toBeInTheDocument();
    expect(screen.getByText('269,70 €')).toBeInTheDocument();
  });

  it('removes a line, and puts it back on "Annuler"', async () => {
    const user = userEvent.setup();
    aliceWithCart();
    let restored: unknown;
    server.use(
      http.delete('*/api/me/cart/items/11', () => HttpResponse.json({ ...cart, items: [cart.items[0]], totalQuantity: 2, total: 179.8 })),
      http.post('*/api/me/cart/items', async ({ request }) => {
        restored = await request.json();
        return HttpResponse.json(cart);
      }),
    );
    renderApp('/panier');

    await user.click(await screen.findByRole('button', { name: 'Retirer Souris sans fil du panier' }));

    expect(await screen.findByText('Souris sans fil retiré du panier')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('link', { name: 'Souris sans fil' })).not.toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: 'Annuler' }));

    expect(await screen.findByRole('link', { name: 'Souris sans fil' })).toBeInTheDocument();
    expect(restored).toEqual({ productId: 11, quantity: 1 });
  });

  it('empties the cart only once confirmed', async () => {
    const user = userEvent.setup();
    aliceWithCart();
    let cleared = false;
    server.use(
      http.delete('*/api/me/cart', () => {
        cleared = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderApp('/panier');

    await user.click(await screen.findByRole('button', { name: 'Vider le panier' }));
    const dialog = screen.getByRole('dialog', { name: 'Vider le panier ?' });
    expect(within(dialog).getByRole('button', { name: 'Annuler' })).toHaveFocus();
    expect(cleared).toBe(false);
    await user.click(within(dialog).getByRole('button', { name: 'Vider le panier' }));

    expect(await screen.findByRole('heading', { name: 'Votre panier est vide' })).toBeInTheDocument();
    expect(screen.getByText('Panier vidé')).toBeInTheDocument();
    expect(cleared).toBe(true);
  });

  it('places the order and shows its live tracking, with an empty cart', async () => {
    const user = userEvent.setup();
    aliceWithCart();
    const placed = { ...order, id: 77, status: 'PENDING' };
    server.use(
      http.post('*/api/me/orders', () => HttpResponse.json(placed, { status: 201 })),
      http.get('*/api/me/orders/77', () => HttpResponse.json(placed)),
    );
    const { router } = renderApp('/panier');

    await user.click(await screen.findByRole('button', { name: 'Passer la commande' }));

    expect(await screen.findByRole('heading', { name: 'Commande n° 77', level: 1 })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/commandes/77');
    expect(screen.getByText('Commande n° 77 passée')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Panier vide' })).toBeInTheDocument();
  });

  it('invites to browse the catalogue when the cart is empty', async () => {
    aliceWithCart(emptyCart);
    renderApp('/panier');

    expect(await screen.findByRole('heading', { name: 'Votre panier est vide', level: 2 })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Voir le catalogue' })).toHaveAttribute('href', '/');
  });

  it('asks an anonymous visitor to sign in, without calling the cart nor showing an error', async () => {
    let asked = false;
    server.use(
      http.get('*/api/me/cart', () => {
        asked = true;
        return HttpResponse.json({ status: 401, code: 'UNAUTHENTICATED' }, { status: 401 });
      }),
    );
    renderApp('/panier');

    expect(await screen.findByRole('heading', { name: 'Connexion requise' })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(asked).toBe(false);
  });
});
