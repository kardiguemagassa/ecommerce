import { useSyncExternalStore } from 'react';
import { ApiError } from '../api/problem';
import { fr } from '../i18n/fr';

export type Severity = 'success' | 'info' | 'warning' | 'error';

/** A button of a toast: "Se connecter", "Voir le panier"... */
export interface ToastAction {
  label: string;
  onClick: () => void;
}

/** A message shown to the user as a toast; errors carry the trace id of the request when the API sent one. */
export interface Toast {
  id: number;
  severity: Severity;
  message: string;
  traceId?: string;
  action?: ToastAction;
}

/** Display time; null keeps the toast until closed (an error to quote to the support must not vanish). */
export function durationOf(toast: Toast): number | null {
  if (toast.severity === 'error') return toast.traceId ? null : 8000;
  return toast.severity === 'warning' ? 6000 : 4000;
}

const MAX_QUEUED = 5;

let queue: readonly Toast[] = [];
let nextId = 1;
const listeners = new Set<() => void>();

function publish(next: readonly Toast[]) {
  queue = next;
  listeners.forEach((listener) => listener());
}

function push(severity: Severity, message: string, traceId?: string, action?: ToastAction) {
  // The same message twice in a row (several queries failing for one reason) is shown once.
  if (queue.some((toast) => toast.message === message && toast.traceId === traceId)) return;
  publish([...queue, { id: nextId++, severity, message, traceId, action }].slice(-MAX_QUEUED));
}

/**
 * Toasts, one at a time in order of arrival. Callable from anywhere: components, mutation callbacks, the query
 * cache. Errors accept anything thrown: an ApiError shows its French message and its trace id.
 */
export const notify = {
  success: (message: string, action?: ToastAction) => push('success', message, undefined, action),
  info: (message: string, action?: ToastAction) => push('info', message, undefined, action),
  warning: (message: string, action?: ToastAction) => push('warning', message, undefined, action),
  error: (error: unknown) => {
    if (error instanceof ApiError) push('error', error.message, error.traceId);
    else push('error', typeof error === 'string' ? error : fr.errors.unexpected);
  },
};

/** Removes a toast (closed, expired); the next one in the queue shows up. */
export function dismissToast(id: number): void {
  publish(queue.filter((toast) => toast.id !== id));
}

/** Empties the queue (tests). */
export function clearToasts(): void {
  publish([]);
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** The toast to show now: the oldest of the queue. */
export function useCurrentToast(): Toast | undefined {
  return useSyncExternalStore(subscribe, () => queue[0]);
}
