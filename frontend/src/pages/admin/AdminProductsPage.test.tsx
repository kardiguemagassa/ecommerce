import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { clearToasts } from '../../app/notifications';
import { renderApp } from '../../test/render';
import { admin, mouse, pageOf, products, server, sessionOf } from '../../test/server';

const rowOf = (name: string) => screen.getByRole('rowheader', { name }).closest('tr')!;

describe('Admin: products', () => {
  it('opens on the products, the current section marked, with the stock in words', async () => {
    sessionOf(admin);
    const { router } = renderApp('/admin');

    expect(await screen.findByRole('heading', { name: 'Administration', level: 1 })).toBeInTheDocument();
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/produits'));
    await waitFor(() => expect(screen.getByRole('link', { name: 'Produits' })).toHaveAttribute('aria-current', 'page'));
    expect(await screen.findByRole('rowheader', { name: 'Casque à réduction de bruit' })).toBeInTheDocument();
    expect(rowOf('Casque à réduction de bruit')).toHaveTextContent('Rupture');
    expect(rowOf('Souris sans fil')).toHaveTextContent('3 · stock bas');
    expect(rowOf('Clavier mécanique TKL')).toHaveTextContent('89,90 €');
    await waitFor(() => expect(document.title).toBe('Produits · Administration · Comptoir'));
  });

  it('checks the form in French, creates the product, then offers to add its images', async () => {
    const user = userEvent.setup();
    sessionOf(admin);
    let created: unknown;
    server.use(
      http.post('*/api/products', async ({ request }) => {
        created = await request.json();
        return HttpResponse.json({ id: 20, name: 'Lampe de bureau', price: 34.5, stock: 25, categoryId: 1, categoryName: 'Informatique' }, { status: 201 });
      }),
    );
    const { router } = renderApp('/admin/produits');

    await user.click(await screen.findByRole('button', { name: 'Nouveau produit' }));
    const drawer = await screen.findByRole('dialog', { name: 'Nouveau produit' });
    await user.click(within(drawer).getByRole('button', { name: 'Créer le produit' }));

    expect(within(drawer).getAllByText('Ce champ est obligatoire.')).toHaveLength(3); // name, category, price
    expect(await screen.findByText('Corrigez les champs signalés.')).toBeInTheDocument();
    expect(created).toBeUndefined();
    act(() => clearToasts());

    await user.type(within(drawer).getByRole('textbox', { name: 'Nom' }), 'Lampe de bureau');
    await user.click(within(drawer).getByRole('combobox', { name: 'Catégorie' }));
    await user.click(await screen.findByRole('option', { name: 'Informatique' }));
    await user.type(within(drawer).getByRole('textbox', { name: 'Prix (€)' }), '34,50');
    await user.clear(within(drawer).getByRole('textbox', { name: 'Stock' }));
    await user.type(within(drawer).getByRole('textbox', { name: 'Stock' }), '25');
    await user.click(within(drawer).getByRole('button', { name: 'Créer le produit' }));

    expect(await screen.findByRole('dialog', { name: 'Modifier « Lampe de bureau »' })).toBeInTheDocument();
    expect(created).toEqual({ name: 'Lampe de bureau', categoryId: 1, price: 34.5, stock: 25 });
    expect(router.state.location.search).toBe('?produit=20');
    expect(screen.getByRole('button', { name: 'Ajouter une image' })).toBeInTheDocument();
    expect(screen.getByText('Lampe de bureau créé')).toBeInTheDocument();
  });

  it('saves a change, closes the panel and refreshes the table', async () => {
    const user = userEvent.setup();
    sessionOf(admin);
    let saved: unknown;
    server.use(
      http.put('*/api/products/11', async ({ request }) => {
        saved = await request.json();
        return HttpResponse.json({ ...mouse, stock: 10 });
      }),
      http.get('*/api/products', () =>
        HttpResponse.json(pageOf(saved ? products.map((product) => (product.id === 11 ? { ...mouse, stock: 10 } : product)) : products, 3, 20)),
      ),
    );
    renderApp('/admin/produits');

    await user.click(await screen.findByRole('button', { name: 'Modifier Souris sans fil' }));
    const drawer = await screen.findByRole('dialog', { name: 'Modifier « Souris sans fil »' });
    const stock = await within(drawer).findByRole('textbox', { name: 'Stock' });
    expect(within(drawer).getByRole('textbox', { name: 'Prix (€)' })).toHaveValue('39,90');
    await user.clear(stock);
    await user.type(stock, '10');
    await user.click(within(drawer).getByRole('button', { name: 'Enregistrer' }));

    expect(await screen.findByText('Souris sans fil enregistré')).toBeInTheDocument();
    expect(saved).toEqual({ name: 'Souris sans fil', categoryId: 1, price: 39.9, stock: 10 });
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await waitFor(() => expect(rowOf('Souris sans fil')).not.toHaveTextContent('stock bas'));
  });

  it('asks before closing a panel with unsaved changes', async () => {
    const user = userEvent.setup();
    sessionOf(admin);
    renderApp('/admin/produits?produit=11');

    const name = await screen.findByRole('textbox', { name: 'Nom' });
    await user.type(name, ' Pro');
    await user.keyboard('{Escape}');

    const confirm = await screen.findByRole('dialog', { name: 'Abandonner les modifications ?' });
    await user.click(within(confirm).getByRole('button', { name: 'Abandonner' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('refuses an image that is too heavy without sending it, and sends a valid one', async () => {
    const user = userEvent.setup();
    sessionOf(admin);
    let uploads = 0;
    server.use(
      http.post('*/api/products/11/images', () => {
        uploads += 1;
        return HttpResponse.json({ ...mouse, imageUrl: '/media/products/mouse.jpg', images: [{ id: 1, url: '/media/products/mouse.jpg' }] });
      }),
    );
    renderApp('/admin/produits?produit=11');
    const input = await screen.findByLabelText('Ajouter une image');

    await user.upload(input, new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'big.png', { type: 'image/png' }));
    expect(await screen.findByText("L'image est trop lourde : 2 Mo maximum.")).toBeInTheDocument();
    expect(uploads).toBe(0);

    await user.upload(input, new File([new Uint8Array(1024)], 'mouse.png', { type: 'image/png' }));
    await waitFor(() => expect(uploads).toBe(1));
    expect(await screen.findByRole('img', { name: 'Vue 1' })).toBeInTheDocument();
    expect(screen.getByText('Principale')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retirer la vue 1' })).toBeInTheDocument();
  });

  it('moves a view before the main one, and removes a view once confirmed', async () => {
    const user = userEvent.setup();
    sessionOf(admin);
    const front = { id: 1, url: '/media/products/front.jpg' };
    const back = { id: 2, url: '/media/products/back.jpg' };
    let order: unknown;
    let removed: string | undefined;
    server.use(
      http.get('*/api/products/11', () => HttpResponse.json({ ...mouse, imageUrl: front.url, images: [front, back] })),
      http.put('*/api/products/11/images/order', async ({ request }) => {
        order = await request.json();
        return HttpResponse.json({ ...mouse, imageUrl: back.url, images: [back, front] });
      }),
      http.delete('*/api/products/11/images/:imageId', ({ params }) => {
        removed = params.imageId as string;
        return HttpResponse.json({ ...mouse, imageUrl: front.url, images: [front] });
      }),
    );
    renderApp('/admin/produits?produit=11');

    expect(await screen.findByText('2 sur 4 · la première est l\'image principale', { exact: false })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Placer la vue 1 avant' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Placer la vue 2 après' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Placer la vue 2 avant' }));
    await waitFor(() => expect(order).toEqual({ imageIds: [2, 1] }));
    expect(await screen.findByText('Ordre des images enregistré')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Vue 1' })).toHaveAttribute('src', back.url);

    await user.click(screen.getByRole('button', { name: 'Retirer la vue 2' }));
    const confirm = await screen.findByRole('dialog', { name: 'Retirer cette image ?' });
    await user.click(within(confirm).getByRole('button', { name: 'Retirer' }));
    await waitFor(() => expect(removed).toBe('1'));
  });

  it('offers no new view once the product has four', async () => {
    sessionOf(admin);
    const images = [1, 2, 3, 4].map((id) => ({ id, url: `/media/products/view-${id}.jpg` }));
    server.use(http.get('*/api/products/11', () => HttpResponse.json({ ...mouse, imageUrl: images[0]!.url, images })));
    renderApp('/admin/produits?produit=11');

    expect(await screen.findByRole('img', { name: 'Vue 4' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ajouter une image' })).not.toBeInTheDocument();
  });

  it('explains why a product already ordered cannot be deleted', async () => {
    const user = userEvent.setup();
    sessionOf(admin);
    server.use(
      http.delete('*/api/products/11', () =>
        HttpResponse.json({ status: 409, title: 'Data integrity violation' }, { status: 409 }),
      ),
    );
    renderApp('/admin/produits');

    await user.click(await screen.findByRole('button', { name: 'Supprimer Souris sans fil' }));
    const confirm = screen.getByRole('dialog', { name: 'Supprimer « Souris sans fil » ?' });
    expect(confirm).toHaveTextContent('mettez son stock à 0');
    await user.click(within(confirm).getByRole('button', { name: 'Supprimer' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Cet élément est lié à des commandes ou à des paniers');
    expect(screen.getByRole('rowheader', { name: 'Souris sans fil' })).toBeInTheDocument();
  });
});
