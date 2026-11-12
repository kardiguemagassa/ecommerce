import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { clearToasts } from '../../app/notifications';
import { renderApp } from '../../test/render';
import { admin, pageOf, server, sessionOf } from '../../test/server';

const moderated = (id: number, hidden: boolean, comment?: string) => ({
  id, productId: 10, productName: 'Clavier mécanique TKL', authorName: 'Alice Martin', authorEmail: 'alice@example.com',
  rating: id === 1 ? 1 : 5, comment, hidden, createdAt: '2026-11-05T12:00:00Z',
});

describe('Admin: reviews moderation', () => {
  it('hides a review, and shows it again', async () => {
    const user = userEvent.setup();
    sessionOf(admin);
    const sent: { id: string; hidden: boolean }[] = [];
    let hidden = false;
    server.use(
      http.get('*/api/admin/reviews', () => HttpResponse.json(pageOf([moderated(1, hidden, 'Hors sujet'), moderated(2, false)], 2, 20))),
      http.put('*/api/admin/reviews/:id/visibility', async ({ params, request }) => {
        const body = (await request.json()) as { hidden: boolean };
        sent.push({ id: String(params.id), hidden: body.hidden });
        hidden = body.hidden;
        return HttpResponse.json(moderated(1, hidden, 'Hors sujet'));
      }),
    );
    renderApp('/admin/avis');

    const row = (await screen.findByText('Hors sujet')).closest('tr')!;
    expect(within(row).getByRole('rowheader', { name: 'Clavier mécanique TKL' })).toBeInTheDocument();
    expect(within(row).getByText('alice@example.com')).toBeInTheDocument();
    expect(within(row).getByText('Visible')).toBeInTheDocument();
    expect(screen.getByText('Sans commentaire')).toBeInTheDocument();

    await user.click(within(row).getByRole('button', { name: "Masquer l'avis de Alice Martin sur Clavier mécanique TKL" }));
    expect(await screen.findByText(/Avis masqué/)).toBeInTheDocument();
    await waitFor(() => expect(within(screen.getByText('Hors sujet').closest('tr')!).getByText('Masqué')).toBeInTheDocument());

    act(() => clearToasts());
    await user.click(screen.getByRole('button', { name: "Afficher l'avis de Alice Martin sur Clavier mécanique TKL" }));
    expect(await screen.findByText('Avis de nouveau visible dans la boutique')).toBeInTheDocument();
    expect(sent).toEqual([{ id: '1', hidden: true }, { id: '1', hidden: false }]);
  });

  it('keeps only the hidden reviews, through the address', async () => {
    const user = userEvent.setup();
    sessionOf(admin);
    let asked: URLSearchParams | undefined;
    server.use(
      http.get('*/api/admin/reviews', ({ request }) => {
        asked = new URL(request.url).searchParams;
        return HttpResponse.json(asked.get('hidden') ? pageOf([], 0, 20) : pageOf([moderated(2, false, 'Top')], 1, 20));
      }),
    );
    const { router } = renderApp('/admin/avis');

    expect(await screen.findByText('Top')).toBeInTheDocument();
    expect(asked?.get('hidden')).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Masqués' }));

    expect(await screen.findByText('Aucun avis masqué.')).toBeInTheDocument();
    expect(router.state.location.search).toBe('?statut=masques');
    expect(asked?.get('hidden')).toBe('true');
    await waitFor(() => expect(document.title).toBe('Avis · Administration · Comptoir'));
  });
});
