import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { renderApp } from '../test/render';
import { alice, emptyCart, server, sessionOf } from '../test/server';

describe('Product page', () => {
  it('shows the product, names the browser tab after it and links back to its category', async () => {
    renderApp('/produits/10');

    expect(await screen.findByRole('heading', { name: 'Clavier mécanique TKL', level: 1 })).toBeInTheDocument();
    expect(screen.getByText('Interrupteurs linéaires, AZERTY.')).toBeInTheDocument();
    expect(screen.getByText('89,90 €')).toBeInTheDocument();
    expect(screen.getByText('En stock · 24 disponibles')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Photo du produit Clavier mécanique TKL' })).toHaveAttribute('loading', 'eager');
    const breadcrumb = screen.getByRole('navigation', { name: "Fil d'Ariane" });
    expect(breadcrumb).toHaveTextContent('Catalogue');
    expect(screen.getByRole('link', { name: 'Informatique' })).toHaveAttribute('href', '/?categorie=1');
    await waitFor(() => expect(document.title).toBe('Clavier mécanique TKL · Comptoir'));
  });

  it('keeps the quantity between 1 and the stock, and adds that quantity', async () => {
    const user = userEvent.setup();
    sessionOf(alice);
    let added: unknown;
    server.use(
      http.post('*/api/me/cart/items', async ({ request }) => {
        added = await request.json();
        return HttpResponse.json({ ...emptyCart, totalQuantity: 3 });
      }),
    );
    renderApp('/produits/11'); // stock 3

    const decrease = await screen.findByRole('button', { name: 'Diminuer la quantité' });
    const increase = screen.getByRole('button', { name: 'Augmenter la quantité' });
    expect(decrease).toBeDisabled();
    await user.click(increase);
    await user.click(increase);
    expect(increase).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Ajouter au panier' }));

    expect(added).toEqual({ productId: 11, quantity: 3 });
    expect(await screen.findByText('3 × Souris sans fil ajoutés au panier')).toBeInTheDocument();
  });

  it('offers no quantity for a product out of stock', async () => {
    renderApp('/produits/12');

    expect(await screen.findByText('Rupture de stock')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ajouter au panier' })).not.toBeInTheDocument();
  });

  it('says a removed product no longer exists, inside the page, without error toast', async () => {
    renderApp('/produits/999');

    expect(await screen.findByRole('heading', { name: "Ce produit n'existe plus" })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Retour au catalogue' })).toHaveAttribute('href', '/');
    expect(await screen.findByRole('button', { name: 'Se connecter' })).toBeInTheDocument(); // header kept
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('treats a malformed address as a missing product', async () => {
    renderApp('/produits/abc');

    expect(await screen.findByRole('heading', { name: "Ce produit n'existe plus" })).toBeInTheDocument();
  });
});
