import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { renderApp } from '../../test/render';
import { admin, server, sessionOf } from '../../test/server';

describe('Admin: categories', () => {
  it('adds a category', async () => {
    const user = userEvent.setup();
    sessionOf(admin);
    let sent: unknown;
    server.use(
      http.post('*/api/categories', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json({ id: 9, name: 'Jardin' }, { status: 201 });
      }),
    );
    renderApp('/admin/categories');

    await user.type(await screen.findByRole('textbox', { name: 'Nouvelle catégorie' }), '  Jardin {Enter}');

    expect(await screen.findByText('Catégorie Jardin créée')).toBeInTheDocument();
    expect(sent).toEqual({ name: 'Jardin' });
    expect(screen.getByRole('textbox', { name: 'Nouvelle catégorie' })).toHaveValue('');
  });

  it('renames in place with the keyboard: Escape cancels, Enter saves', async () => {
    const user = userEvent.setup();
    sessionOf(admin);
    let sent: unknown;
    server.use(
      http.put('*/api/categories/2', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json({ id: 2, name: 'Son' });
      }),
    );
    renderApp('/admin/categories');

    await user.click(await screen.findByRole('button', { name: 'Renommer Audio' }));
    const field = screen.getByRole('textbox', { name: 'Nouveau nom de Audio' });
    expect(field).toHaveFocus();
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('textbox', { name: 'Nouveau nom de Audio' })).not.toBeInTheDocument();
    expect(sent).toBeUndefined();

    await user.click(screen.getByRole('button', { name: 'Renommer Audio' }));
    await user.clear(screen.getByRole('textbox', { name: 'Nouveau nom de Audio' }));
    await user.keyboard('Son{Enter}');

    expect(await screen.findByText('Catégorie renommée en Son')).toBeInTheDocument();
    expect(sent).toEqual({ name: 'Son' });
  });

  it('explains that a category holding products cannot be deleted', async () => {
    const user = userEvent.setup();
    sessionOf(admin);
    server.use(
      http.delete('*/api/categories/1', () => HttpResponse.json({ status: 409, title: 'Resource in use' }, { status: 409 })),
    );
    renderApp('/admin/categories');

    await user.click(await screen.findByRole('button', { name: 'Supprimer Informatique' }));
    const confirm = screen.getByRole('dialog', { name: 'Supprimer la catégorie « Informatique » ?' });
    await user.click(within(confirm).getByRole('button', { name: 'Supprimer' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Cet élément est encore utilisé et ne peut pas être supprimé.');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getByText('Informatique')).toBeInTheDocument();
  });
});
