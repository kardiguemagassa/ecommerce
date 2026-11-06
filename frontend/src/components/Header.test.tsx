import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { renderApp } from '../test/render';
import { admin, alice, server, sessionOf } from '../test/server';

describe('Header', () => {
  it('offers to sign in to an anonymous visitor, without cart', async () => {
    renderApp();

    expect(await screen.findByRole('button', { name: 'Se connecter' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Panier/ })).not.toBeInTheDocument();
  });

  it('shows the photo and name of a signed-in customer, and the number of items in the cart', async () => {
    sessionOf({ ...alice, avatarUrl: 'http://localhost:8333/ecommerce-media/avatars/alice.jpg' });
    server.use(http.get('*/api/me/cart', () => HttpResponse.json({ customerId: 1, items: [], totalQuantity: 3, total: 99 })));

    renderApp();

    const account = await screen.findByRole('button', { name: 'Menu du compte : Alice Martin' });
    expect(within(account).getByText('Alice Martin')).toBeInTheDocument();
    expect(account.querySelector('img')).toHaveAttribute('src', 'http://localhost:8333/ecommerce-media/avatars/alice.jpg');
    expect(await screen.findByRole('link', { name: 'Panier, 3 articles' })).toBeInTheDocument();
  });

  it('shows the initials when the customer has no photo', async () => {
    sessionOf(alice);

    renderApp();

    const account = await screen.findByRole('button', { name: 'Menu du compte : Alice Martin' });
    expect(within(account).getByText('AM')).toBeInTheDocument();
  });

  it('lists the admin space in the menu of administrators only', async () => {
    const user = userEvent.setup();
    sessionOf(alice);
    const { unmount } = renderApp();
    await user.click(await screen.findByRole('button', { name: /Menu du compte/ }));
    expect(screen.getByRole('menuitem', { name: 'Mon profil' })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: 'Administration' })).not.toBeInTheDocument();
    unmount();

    sessionOf(admin);
    renderApp();
    await user.click(await screen.findByRole('button', { name: /Menu du compte/ }));
    expect(screen.getByRole('menuitem', { name: 'Administration' })).toBeInTheDocument();
  });

  it('puts the search term in the URL', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/panier');

    await user.type(await screen.findByRole('searchbox', { name: 'Rechercher un produit' }), 'clavier{Enter}');

    expect(router.state.location.pathname).toBe('/');
    expect(router.state.location.search).toBe('?q=clavier');
  });
});
