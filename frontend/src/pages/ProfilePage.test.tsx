import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { clearToasts } from '../app/notifications';
import { renderApp } from '../test/render';
import { alice, server, sessionOf } from '../test/server';

const accountUrl = 'http://localhost:8180/realms/ecommerce/account';
const png = (bytes = 1024, name = 'moi.png') => new File([new Uint8Array(bytes)], name, { type: 'image/png' });
const headerPhoto = () => screen.getByRole('button', { name: /Menu du compte/ }).querySelector('img');

describe('Profile page', () => {
  it('shows the identity and leads to the Keycloak account console, in a new tab', async () => {
    sessionOf({ ...alice, accountUrl });
    renderApp('/profil');

    expect(await screen.findByText('alice@example.com')).toBeInTheDocument();
    const account = screen.getByRole('link', { name: /Modifier mon nom, e-mail ou mot de passe/ });
    expect(account).toHaveAttribute('href', accountUrl);
    expect(account).toHaveAttribute('target', '_blank');
    expect(account).toHaveAccessibleName(/nouvel onglet/);
  });

  it('refuses a file that is not an image, or too heavy, without sending it', async () => {
    const user = userEvent.setup({ applyAccept: false });
    sessionOf(alice);
    let sent = false;
    server.use(
      http.put('*/api/me/avatar', () => {
        sent = true;
        return HttpResponse.json({});
      }),
    );
    renderApp('/profil');
    const input = await screen.findByLabelText('Changer la photo');

    await user.upload(input, new File(['GIF89a'], 'anim.gif', { type: 'image/gif' }));
    expect(await screen.findByText('Format non accepté : JPEG, PNG ou WebP uniquement.')).toBeInTheDocument();
    act(() => clearToasts());
    await user.upload(input, png(1024 * 1024 + 1));
    expect(await screen.findByText("L'image est trop lourde : 1 Mo maximum.")).toBeInTheDocument();

    expect(screen.queryByRole('img', { name: 'Aperçu de la nouvelle photo' })).not.toBeInTheDocument();
    expect(sent).toBe(false);
  });

  it('previews the chosen photo, sends it on "Enregistrer", and shows it in the header at once', async () => {
    const user = userEvent.setup();
    sessionOf(alice);
    let received: { contentType: string | null; body: string } | undefined;
    server.use(
      http.put('*/api/me/avatar', async ({ request }) => {
        // Read as sent (raw multipart): jsdom's File is not the File class Node's form-data parser expects.
        received = { contentType: request.headers.get('content-type'), body: await request.text() };
        return HttpResponse.json({ id: 1, fullName: alice.name, email: alice.email, avatarUrl: '/media/avatars/alice-2.jpg' });
      }),
    );
    renderApp('/profil');

    await user.upload(await screen.findByLabelText('Changer la photo'), png());
    expect(screen.getByRole('img', { name: 'Aperçu de la nouvelle photo' })).toHaveAttribute('src', expect.stringMatching(/^blob:/));
    expect(received).toBeUndefined(); // nothing sent before "Enregistrer"
    await user.click(screen.getByRole('button', { name: 'Enregistrer la photo' }));

    expect(await screen.findByText('Photo de profil mise à jour')).toBeInTheDocument();
    expect(received?.contentType).toMatch(/^multipart\/form-data; boundary=/);
    expect(received?.body).toMatch(/Content-Disposition: form-data; name="file"; filename="[^"]+"\r\nContent-Type: image\/png/);
    await waitFor(() => expect(headerPhoto()).toHaveAttribute('src', '/media/avatars/alice-2.jpg'));
    expect(screen.queryByRole('img', { name: 'Aperçu de la nouvelle photo' })).not.toBeInTheDocument();
  });

  it('deletes the photo once confirmed; the initials come back in the header', async () => {
    const user = userEvent.setup();
    sessionOf({ ...alice, avatarUrl: '/media/avatars/alice.jpg' });
    let deleted = false;
    server.use(
      http.delete('*/api/me/avatar', () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderApp('/profil');

    await user.click(await screen.findByRole('button', { name: 'Supprimer la photo' }));
    const dialog = screen.getByRole('dialog', { name: 'Supprimer la photo de profil ?' });
    await user.click(within(dialog).getByRole('button', { name: 'Supprimer la photo' }));

    expect(await screen.findByText('Photo de profil supprimée')).toBeInTheDocument();
    expect(deleted).toBe(true);
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(headerPhoto()).toBeNull();
    expect(screen.getByRole('button', { name: /Menu du compte/ })).toHaveTextContent('AM');
  });
});
