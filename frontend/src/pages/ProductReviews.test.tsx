import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { clearToasts } from '../app/notifications';
import { renderApp } from '../test/render';
import { alice, keyboard, pageOf, products, server, sessionOf } from '../test/server';

const rated = { ...keyboard, rating: { average: 4.5, count: 12 } };
const review = (id: number, rating: number, author: string, comment?: string) => ({
  id, rating, author, comment, createdAt: '2026-11-05T12:00:00Z', updatedAt: '2026-11-05T12:00:00Z',
});

function withReviews() {
  server.use(
    http.get('*/api/products/10', () => HttpResponse.json(rated)),
    http.get('*/api/products/10/reviews/summary', () =>
      HttpResponse.json({
        rating: { average: 4.5, count: 12 },
        distribution: [
          { stars: 5, count: 8 }, { stars: 4, count: 2 }, { stars: 3, count: 1 }, { stars: 2, count: 1 }, { stars: 1, count: 0 },
        ],
      }),
    ),
    http.get('*/api/products/10/reviews', ({ request }) => {
      const page = Number(new URL(request.url).searchParams.get('page'));
      return HttpResponse.json(
        page === 0
          ? { content: [review(1, 5, 'Alice M.', 'Frappe agréable.\nSilencieux.'), review(2, 4, 'Bob D.')], page: { size: 10, number: 0, totalElements: 12, totalPages: 2 } }
          : { content: [review(3, 3, 'Carol')], page: { size: 10, number: 1, totalElements: 12, totalPages: 2 } },
      );
    }),
  );
}

describe('Reviews of a product', () => {
  it('shows the rating, its spread, the verified reviews, and loads more', async () => {
    const user = userEvent.setup();
    withReviews();
    renderApp('/produits/10');

    expect(await screen.findByRole('link', { name: '12 avis' })).toHaveAttribute('href', '#avis');
    const section = await screen.findByRole('region', { name: 'Avis clients' });
    expect(within(section).getByText('4,5')).toBeInTheDocument();
    expect(within(section).getByRole('list', { name: 'Répartition des notes' })).toHaveTextContent('5 étoiles : 8 avis');
    expect(await within(section).findByText('Alice M.')).toBeInTheDocument();
    expect(within(section).getAllByText('Achat vérifié')).toHaveLength(2);
    expect(within(section).getAllByRole('img', { name: 'Note : 5 sur 5' }).length).toBeGreaterThan(0);

    await user.click(within(section).getByRole('button', { name: "Voir plus d'avis" }));
    expect(await within(section).findByText('Carol')).toBeInTheDocument();
    expect(within(section).queryByRole('button', { name: "Voir plus d'avis" })).not.toBeInTheDocument();
  });

  it('describes the product for search engines, rating included', async () => {
    withReviews();
    renderApp('/produits/10');

    await screen.findByRole('heading', { name: keyboard.name, level: 1 });
    const data = JSON.parse(document.querySelector('script[type="application/ld+json"]')!.textContent!);
    expect(data).toMatchObject({
      '@type': 'Product',
      name: keyboard.name,
      offers: { price: '89.90', priceCurrency: 'EUR', availability: 'https://schema.org/InStock' },
      aggregateRating: { ratingValue: 4.5, reviewCount: 12 },
    });
  });

  it('offers a visitor to sign in to write a review', async () => {
    renderApp('/produits/10');
    expect(await screen.findByRole('button', { name: 'Se connecter pour donner mon avis' })).toBeInTheDocument();
  });

  it('tells a customer who did not buy the product why there is no form', async () => {
    sessionOf(alice);
    renderApp('/produits/10');
    expect(await screen.findByText(/Seuls les clients ayant commandé ce produit/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Publier mon avis' })).not.toBeInTheDocument();
  });

  it('lets a buyer publish a review: a rating is required', async () => {
    const user = userEvent.setup();
    sessionOf(alice);
    let sent: unknown;
    server.use(
      http.get('*/api/me/products/10/review', () => HttpResponse.json({ allowed: true })),
      http.put('*/api/me/products/10/review', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json(review(9, 4, 'Alice M.', 'Très bien'));
      }),
    );
    renderApp('/produits/10');

    const form = await screen.findByRole('form', { name: 'Donner mon avis' });
    await user.click(within(form).getByRole('button', { name: 'Publier mon avis' }));
    expect(await within(form).findByText('Choisissez une note de 1 à 5 étoiles.')).toBeInTheDocument();
    expect(sent).toBeUndefined();
    act(() => clearToasts()); // the error toast would hold the next one in the queue

    // A click without the pointer moving over the stars first: jsdom lays nothing out, MUI would read a hover of 0.
    fireEvent.click(within(form).getByRole('radio', { name: '4 étoiles' }));
    expect(within(form).getByRole('radio', { name: '4 étoiles' })).toBeChecked();
    await user.type(within(form).getByRole('textbox', { name: 'Commentaire (facultatif)' }), '  Très bien ');
    await user.click(within(form).getByRole('button', { name: 'Publier mon avis' }));
    await waitFor(() => expect(sent).toEqual({ rating: 4, comment: 'Très bien' }));
    expect(await screen.findByText('Merci, votre avis est publié')).toBeInTheDocument();
  });

  it('lets a buyer change or delete their review', async () => {
    const user = userEvent.setup();
    sessionOf(alice);
    let deleted = false;
    server.use(
      http.get('*/api/me/products/10/review', () => HttpResponse.json({ allowed: true, review: review(9, 2, 'Alice M.', 'Bof') })),
      http.delete('*/api/me/products/10/review', () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderApp('/produits/10');

    const form = await screen.findByRole('form', { name: 'Mon avis' });
    expect(within(form).getByRole('radio', { name: '2 étoiles' })).toBeChecked();
    expect(within(form).getByRole('textbox', { name: 'Commentaire (facultatif)' })).toHaveValue('Bof');
    expect(within(form).getByRole('button', { name: 'Mettre à jour mon avis' })).toBeInTheDocument();

    await user.click(within(form).getByRole('button', { name: 'Supprimer mon avis' }));
    const confirm = await screen.findByRole('dialog', { name: 'Supprimer votre avis ?' });
    await user.click(within(confirm).getByRole('button', { name: 'Supprimer mon avis' }));
    await waitFor(() => expect(deleted).toBe(true));
  });

  it('tells a buyer that the moderation hid their review', async () => {
    sessionOf(alice);
    server.use(
      http.get('*/api/me/products/10/review', () =>
        HttpResponse.json({ allowed: true, hidden: true, review: review(9, 1, 'Alice M.', 'Hors sujet') }),
      ),
    );
    renderApp('/produits/10');

    const form = await screen.findByRole('form', { name: 'Mon avis' });
    expect(within(form).getByText(/La modération a masqué votre avis/)).toBeInTheDocument();
  });

  it('shows the stars of a product on its catalogue card', async () => {
    server.use(http.get('*/api/products', () => HttpResponse.json(pageOf([rated, ...products.slice(1)]))));
    renderApp('/');

    expect(await screen.findByRole('img', { name: 'Note : 4,5 sur 5, 12 avis' })).toBeInTheDocument();
  });
});
