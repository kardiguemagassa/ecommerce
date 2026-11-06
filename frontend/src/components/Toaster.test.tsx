import { act, render, screen, waitForElementToBeRemoved } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { durationOf, notify } from '../app/notifications';
import { Toaster } from './Toaster';

describe('Toasts', () => {
  it('are announced politely for a success, at once for an error', async () => {
    render(<Toaster />);

    act(() => notify.success('Ajouté au panier'));
    expect(await screen.findByRole('status')).toHaveTextContent('Ajouté au panier');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('show one message at a time, in order of arrival', async () => {
    const user = userEvent.setup();
    render(<Toaster />);

    act(() => {
      notify.success('Photo enregistrée');
      notify.warning('Plus que 2 en stock');
    });

    expect(await screen.findByText('Photo enregistrée')).toBeInTheDocument();
    expect(screen.queryByText('Plus que 2 en stock')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Fermer' }));
    expect(await screen.findByText('Plus que 2 en stock')).toBeInTheDocument();
    expect(screen.queryByText('Photo enregistrée')).not.toBeInTheDocument();
  });

  it('close with the Escape key', async () => {
    const user = userEvent.setup();
    render(<Toaster />);
    act(() => notify.info('Recherche enregistrée'));
    await screen.findByText('Recherche enregistrée');

    await user.keyboard('{Escape}');

    await waitForElementToBeRemoved(() => screen.queryByText('Recherche enregistrée'));
  });

  it('disappear by themselves after their display time', async () => {
    render(<Toaster />);

    act(() => notify.success('Ajouté au panier'));
    await screen.findByText('Ajouté au panier');

    await waitForElementToBeRemoved(() => screen.queryByText('Ajouté au panier'), { timeout: 6_000 });
  });

  it('show an identical message only once', async () => {
    const user = userEvent.setup();
    render(<Toaster />);

    act(() => {
      notify.error('Le service est momentanément indisponible.');
      notify.error('Le service est momentanément indisponible.');
    });
    await user.click(await screen.findByRole('button', { name: 'Fermer' }));

    await waitForElementToBeRemoved(() => screen.queryByRole('alert'));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('last 4 s for a success, 6 s for a warning, until closed for an error with a trace id', () => {
    expect(durationOf({ id: 1, severity: 'success', message: '' })).toBe(4000);
    expect(durationOf({ id: 1, severity: 'info', message: '' })).toBe(4000);
    expect(durationOf({ id: 1, severity: 'warning', message: '' })).toBe(6000);
    expect(durationOf({ id: 1, severity: 'error', message: '' })).toBe(8000);
    expect(durationOf({ id: 1, severity: 'error', message: '', traceId: 'abc' })).toBeNull();
  });
});
