import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { Layout } from '../components/Layout';
import { RouteError } from '../components/RouteError';
import { isStaleChunkError } from '../components/staleChunk';
import { renderApp } from '../test/render';
import { alice, sessionOf } from '../test/server';
import { App } from './App';
import { createQueryClient } from './queryClient';

describe('Routes', () => {
  it('load each page on demand and name the browser tab after it', async () => {
    sessionOf(alice);
    renderApp('/commandes');

    expect(await screen.findByRole('heading', { name: 'Mes commandes' })).toBeInTheDocument();
    await waitFor(() => expect(document.title).toBe('Mes commandes · Comptoir'));
  });

  it('give the focus to the new page after a navigation, for keyboard and screen-reader users', async () => {
    const user = userEvent.setup();
    sessionOf(alice);
    renderApp('/');
    await screen.findByRole('heading', { name: 'Catalogue' });

    await user.click(await screen.findByRole('button', { name: /Menu du compte/ }));
    await user.click(screen.getByRole('menuitem', { name: 'Mon profil' }));

    expect(await screen.findByRole('heading', { name: 'Mon profil' })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('main')).toHaveFocus());
  });

  it('offer a skip link to the content', async () => {
    renderApp('/');

    expect(await screen.findByRole('link', { name: 'Aller au contenu' })).toHaveAttribute('href', '#contenu');
  });

  it('keep the header when a page fails, and offer to reload when the site was deployed again', async () => {
    const staleChunk = () => Promise.reject(new TypeError('Failed to fetch dynamically imported module: /assets/CatalogPage-1a2b.js'));
    const router = createMemoryRouter([
      { element: <Layout />, children: [{ errorElement: <RouteError />, children: [{ index: true, lazy: staleChunk }] }] },
    ]);

    render(<App router={router} queryClient={createQueryClient()} />);

    expect(await screen.findByRole('heading', { name: 'Une nouvelle version du site est disponible' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Recharger' })).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Se connecter' })).toBeInTheDocument();
  });

  it('recognise the errors of a stale chunk in every browser', () => {
    expect(isStaleChunkError(new TypeError('Failed to fetch dynamically imported module: /assets/x.js'))).toBe(true); // Chrome
    expect(isStaleChunkError(new TypeError('error loading dynamically imported module'))).toBe(true); // Firefox
    expect(isStaleChunkError(new TypeError('Importing a module script failed.'))).toBe(true); // Safari
    expect(isStaleChunkError(new Error('Cannot read properties of undefined'))).toBe(false);
  });
});
