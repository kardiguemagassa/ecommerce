import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { renderApp } from '../test/render';
import { alice, emptyCart, keyboard, mouse, pageOf, products, server, sessionOf } from '../test/server';

/** Records the query string of every product search the page sends. */
function recordSearches(respond = () => pageOf(products)) {
  const searches: URLSearchParams[] = [];
  server.use(
    http.get('*/api/products', ({ request }) => {
      searches.push(new URL(request.url).searchParams);
      return HttpResponse.json(respond());
    }),
  );
  return searches;
}

describe('Catalogue', () => {
  it('lists the products with their category, price in euros and availability', async () => {
    renderApp('/');

    const card = (await screen.findByRole('link', { name: 'Clavier mécanique TKL' })).closest('article')!;
    expect(within(card).getByText('Informatique')).toBeInTheDocument();
    expect(within(card).getByText('89,90 €')).toBeInTheDocument();
    expect(within(card).getByText('En stock')).toBeInTheDocument();
    expect(within(card).getByRole('img', { hidden: true })).toHaveAttribute('src', '/media/products/keyboard.jpg');
    expect(screen.getByText('Plus que 3')).toBeInTheDocument();
    expect(screen.getByText('Rupture de stock')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ajouter Casque à réduction de bruit au panier' })).toBeDisabled();
    expect(screen.getByRole('status')).toHaveTextContent('3 produits');
  });

  it('sends the search of the URL to the API: name, category, order, page (from 0), 12 per page', async () => {
    const searches = recordSearches(() => pageOf([mouse], 30, 12, 1));

    renderApp('/?q=souris&categorie=1&tri=prix-decroissant&page=2');

    expect(await screen.findByRole('heading', { name: 'Résultats pour « souris »' })).toBeInTheDocument();
    const search = searches[0]!;
    expect(Object.fromEntries(search)).toEqual({ name: 'souris', categoryId: '1', sort: 'price,desc', page: '1', size: '12' });
  });

  it('filters by category and sorts through the URL, back to the first page', async () => {
    const user = userEvent.setup();
    const searches = recordSearches();
    const { router } = renderApp('/?page=2');

    await user.click(await screen.findByRole('button', { name: 'Audio' }));
    await waitFor(() => expect(router.state.location.search).toBe('?categorie=2'));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Audio' })).toHaveAttribute('aria-pressed', 'true'));

    await user.selectOptions(screen.getByLabelText('Trier par'), 'Prix croissant');
    await waitFor(() => expect(router.state.location.search).toBe('?categorie=2&tri=prix-croissant'));
    await waitFor(() => expect(searches.at(-1)!.get('sort')).toBe('price,asc'));
  });

  it('shows the best sellers on the plain catalogue only', async () => {
    server.use(
      http.get('*/api/products/top-selling', () =>
        HttpResponse.json([{ productId: 10, productName: 'Clavier mécanique TKL', totalQuantity: 128 }]),
      ),
    );
    const { unmount } = renderApp('/');

    const ranking = await screen.findByRole('region', { name: 'Meilleures ventes' });
    expect(within(ranking).getByRole('link', { name: 'Clavier mécanique TKL' })).toHaveAttribute('href', '/produits/10');
    expect(within(ranking).getByText('128 vendus')).toBeInTheDocument();
    unmount();

    renderApp('/?q=clavier');
    await screen.findByRole('heading', { name: 'Résultats pour « clavier »' });
    expect(screen.queryByRole('region', { name: 'Meilleures ventes' })).not.toBeInTheDocument();
  });

  it('paginates with real links, and offers to clear the filters when nothing matches', async () => {
    const user = userEvent.setup();
    recordSearches(() => pageOf([], 0));
    const { router } = renderApp('/?q=introuvable&categorie=2');

    expect(await screen.findByRole('heading', { name: 'Aucun produit ne correspond à votre recherche' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Effacer les filtres' }));
    await waitFor(() => expect(router.state.location.search).toBe(''));
  });

  it('links the pages of results', async () => {
    recordSearches(() => pageOf(products, 30));
    renderApp('/?categorie=1');

    const pagination = await screen.findByRole('navigation', { name: /pagination/i });
    expect(within(pagination).getByRole('link', { name: 'Aller à la page 2' })).toHaveAttribute('href', '/?categorie=1&page=2');
    expect(within(pagination).getByRole('link', { name: 'Page 1, page actuelle' })).toHaveAttribute('aria-current', 'page');
  });
});

describe('Adding to the cart', () => {
  it('updates the cart badge at once and confirms with a toast', async () => {
    const user = userEvent.setup();
    sessionOf(alice);
    let added: unknown;
    server.use(
      http.post('*/api/me/cart/items', async ({ request }) => {
        added = await request.json();
        return HttpResponse.json({ ...emptyCart, items: [], totalQuantity: 1, total: 89.9 });
      }),
    );
    renderApp('/');

    await user.click(await screen.findByRole('button', { name: `Ajouter ${keyboard.name} au panier` }));

    expect(await screen.findByText('Clavier mécanique TKL ajouté au panier')).toBeInTheDocument();
    expect(added).toEqual({ productId: 10, quantity: 1 });
    expect(await screen.findByRole('link', { name: 'Panier, 1 article' })).toBeInTheDocument();
  });

  it('offers an anonymous visitor to sign in instead', async () => {
    const user = userEvent.setup();
    let posted = false;
    server.use(http.post('*/api/me/cart/items', () => { posted = true; return HttpResponse.json(emptyCart); }));
    renderApp('/');

    await user.click(await screen.findByRole('button', { name: `Ajouter ${keyboard.name} au panier` }));

    expect(await screen.findByText('Connectez-vous pour ajouter des produits au panier.')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Se connecter' })).toHaveLength(2); // header and toast
    expect(posted).toBe(false);
  });

  it('explains an insufficient stock in French', async () => {
    const user = userEvent.setup();
    sessionOf(alice);
    server.use(
      http.post('*/api/me/cart/items', () =>
        HttpResponse.json({ status: 409, title: 'Insufficient stock', traceId: 'abc123' }, { status: 409 }),
      ),
    );
    renderApp('/');

    await user.click(await screen.findByRole('button', { name: `Ajouter ${mouse.name} au panier` }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Stock insuffisant pour la quantité demandée.');
  });
});
