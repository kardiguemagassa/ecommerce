import { screen, waitFor, within } from '@testing-library/react';
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

  it('invites to give the first review of a product that has none', async () => {
    renderApp('/produits/10');

    expect(await screen.findByText(/^Aucun avis ·/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Donner le premier avis' })).toHaveAttribute('href', '#avis');
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

  it('shows the views of a product: thumbnails, with the arrows of the keyboard', async () => {
    const user = userEvent.setup();
    const images = [1, 2, 3].map((id) => ({ id, url: `/media/products/lamp-${id}.jpg` }));
    server.use(
      http.get('*/api/products/13', () =>
        HttpResponse.json({ id: 13, name: 'Lampe', price: 34.5, stock: 5, categoryId: 1, categoryName: 'Informatique', imageUrl: images[0]!.url, images }),
      ),
    );
    renderApp('/produits/13');

    expect(await screen.findByRole('img', { name: 'Photo du produit Lampe, vue 1 sur 3' })).toBeInTheDocument();
    // Only the view on display is read: the others are hidden from screen readers.
    expect(screen.queryByRole('img', { name: 'Photo du produit Lampe, vue 2 sur 3' })).not.toBeInTheDocument();
    const thumbnails = screen.getByRole('group', { name: 'Vues du produit' });
    expect(within(thumbnails).getAllByRole('button')).toHaveLength(3);
    expect(within(thumbnails).getByRole('button', { name: 'Voir la vue 1 sur 3' })).toHaveAttribute('aria-pressed', 'true');

    await user.click(within(thumbnails).getByRole('button', { name: 'Voir la vue 2 sur 3' }));
    expect(within(thumbnails).getByRole('button', { name: 'Voir la vue 2 sur 3' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('img', { name: 'Photo du produit Lampe, vue 2 sur 3' })).toBeInTheDocument();

    await user.keyboard('{ArrowRight}');
    const third = within(thumbnails).getByRole('button', { name: 'Voir la vue 3 sur 3' });
    expect(third).toHaveAttribute('aria-pressed', 'true');
    expect(third).toHaveFocus();
    await user.keyboard('{Home}');
    expect(within(thumbnails).getByRole('button', { name: 'Voir la vue 1 sur 3' })).toHaveFocus();
  });

  it('shows a single photo full screen, and gives the focus back once closed', async () => {
    const user = userEvent.setup();
    renderApp('/produits/10');

    const zoom = await screen.findByRole('button', { name: 'Agrandir la photo' });
    await user.click(zoom);
    const dialog = await screen.findByRole('dialog', { name: 'Photos du produit Clavier mécanique TKL' });
    expect(within(dialog).getByRole('img', { name: 'Photo du produit Clavier mécanique TKL' })).toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: 'Vue suivante' })).not.toBeInTheDocument();

    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(zoom).toHaveFocus();
  });

  it('goes through the views full screen, zooms, and comes back on the view last seen', async () => {
    const user = userEvent.setup();
    const images = [1, 2, 3].map((id) => ({ id, url: `/media/products/lamp-${id}.jpg` }));
    server.use(
      http.get('*/api/products/13', () =>
        HttpResponse.json({ id: 13, name: 'Lampe', price: 34.5, stock: 5, categoryId: 1, categoryName: 'Informatique', imageUrl: images[0]!.url, images }),
      ),
    );
    renderApp('/produits/13');

    await user.click(await screen.findByRole('button', { name: 'Agrandir la vue 1 sur 3' }));
    const dialog = await screen.findByRole('dialog', { name: 'Photos du produit Lampe' });
    expect(within(dialog).getByText('1 / 3')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Vue précédente' })).toBeDisabled();

    await user.click(within(dialog).getByRole('button', { name: 'Vue suivante' }));
    expect(within(dialog).getByText('2 / 3')).toBeInTheDocument();
    expect(within(dialog).getByRole('img', { name: 'Photo du produit Lampe, vue 2 sur 3' })).toHaveAttribute('src', images[1]!.url);
    await user.keyboard('{ArrowRight}');
    expect(within(dialog).getByText('3 / 3')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Vue suivante' })).toBeDisabled();

    await user.click(within(dialog).getByRole('button', { name: 'Zoomer' }));
    expect(within(dialog).getByRole('button', { name: 'Dézoomer' })).toBeInTheDocument();
    await user.keyboard('-');
    expect(within(dialog).getByRole('button', { name: 'Zoomer' })).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: 'Fermer' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getByRole('button', { name: 'Voir la vue 3 sur 3' })).toHaveAttribute('aria-pressed', 'true');
  });
});
