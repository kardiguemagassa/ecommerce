import { MutationCache, QueryCache, QueryClient, type Query } from '@tanstack/react-query';
import { ApiError } from '../api/problem';
import { sessionKey } from '../api/session';
import { notify } from './notifications';

/**
 * Server state (TanStack Query): cached, refreshed when the window gets focus again, retried once on network errors.
 * Every failed call is shown to the user (with its trace id); a 401 also refreshes the session, so the header switches
 * to "Se connecter" when the session has expired.
 */
export function createQueryClient(): QueryClient {
  const client: QueryClient = new QueryClient({
    queryCache: new QueryCache({
      onError: (error, query) => {
        if (!isQuietNotFound(error, query)) onError(client, error);
      },
    }),
    mutationCache: new MutationCache({ onError: (error) => onError(client, error) }),
    defaultOptions: {
      queries: {
        retry: (failures, error) => failures < 1 && !(error instanceof ApiError && error.status > 0),
      },
    },
  });
  return client;
}

declare module '@tanstack/react-query' {
  interface Register {
    queryMeta: {
      /** A 404 is part of the page (a product that no longer exists), not an error to toast. */
      quietNotFound?: boolean;
    };
  }
}

function isQuietNotFound(error: Error, query: Query<unknown, unknown, unknown, readonly unknown[]>) {
  return error instanceof ApiError && error.status === 404 && query.meta?.quietNotFound === true;
}

function onError(client: QueryClient, error: Error) {
  notify.error(error);
  if (error instanceof ApiError && error.status === 401) {
    void client.invalidateQueries({ queryKey: sessionKey });
  }
}
