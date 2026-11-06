import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { renderApp } from '../test/render';
import { alice, server, sessionOf } from '../test/server';

describe('Failed API calls', () => {
  it('show the French message and the trace id, which the user can copy', async () => {
    const user = userEvent.setup();
    const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue();
    sessionOf(alice);
    server.use(
      http.get('*/api/me/cart', () =>
        HttpResponse.json(
          { title: 'Internal server error', status: 500, traceId: '4bf92f3577b34da6a3ce929d0e0e4736' },
          { status: 500 },
        ),
      ),
    );

    renderApp();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Le service est momentanément indisponible.');
    expect(alert).toHaveTextContent('4bf92f3577b34da6a3ce929d0e0e4736');
    await user.click(screen.getByRole('button', { name: 'Copier' }));
    expect(writeText).toHaveBeenCalledWith('4bf92f3577b34da6a3ce929d0e0e4736');
    expect(screen.getByRole('button', { name: 'Copié' })).toBeInTheDocument();
  });

  it('switch the header back to "Se connecter" when the session has expired', async () => {
    sessionOf(alice);
    server.use(
      http.get('*/api/me/cart', () => {
        sessionOf({ authenticated: false, roles: [] });
        return HttpResponse.json({ status: 401, code: 'UNAUTHENTICATED' }, { status: 401 });
      }),
    );

    renderApp();

    expect(await screen.findByRole('alert')).toHaveTextContent('Votre session a expiré. Reconnectez-vous.');
    expect(await screen.findByRole('button', { name: 'Se connecter' })).toBeInTheDocument();
  });
});
