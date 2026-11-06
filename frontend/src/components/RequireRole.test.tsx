import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderApp } from '../test/render';
import { admin, alice, sessionOf } from '../test/server';

describe('Protected pages', () => {
  it('ask an anonymous visitor to sign in', async () => {
    renderApp('/admin');

    expect(await screen.findByRole('heading', { name: 'Connexion requise' })).toBeInTheDocument();
  });

  it('refuse the admin space to a customer', async () => {
    sessionOf(alice);

    renderApp('/admin');

    expect(await screen.findByRole('heading', { name: 'Accès refusé' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Administration' })).not.toBeInTheDocument();
  });

  it('open the admin space to an administrator', async () => {
    sessionOf(admin);

    renderApp('/admin');

    expect(await screen.findByRole('heading', { name: 'Administration' })).toBeInTheDocument();
  });

  it('open the cart to a customer', async () => {
    sessionOf(alice);

    renderApp('/panier');

    expect(await screen.findByRole('heading', { name: 'Mon panier' })).toBeInTheDocument();
  });

  it('answer an unknown address with a not-found page', async () => {
    renderApp('/nulle-part');

    expect(await screen.findByRole('heading', { name: 'Page introuvable' })).toBeInTheDocument();
  });
});
