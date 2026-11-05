import { useSyncExternalStore } from 'react';
import { ApiError } from '../api/problem';
import { fr } from '../i18n/fr';

/** An error shown to the user: the message, and the trace id of the request when the API sent one. */
export interface Notification {
  id: number;
  message: string;
  traceId?: string;
}

let current: Notification | null = null;
let nextId = 1;
const listeners = new Set<() => void>();

function emit() {
  listeners.forEach((listener) => listener());
}

/** Shows an error (API call or anything else); the latest one replaces the previous. */
export function notifyError(error: unknown): void {
  current =
    error instanceof ApiError
      ? { id: nextId++, message: error.message, traceId: error.traceId }
      : { id: nextId++, message: fr.errors.unexpected };
  emit();
}

export function dismissNotification(): void {
  current = null;
  emit();
}

export function useNotification(): Notification | null {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => current,
  );
}
