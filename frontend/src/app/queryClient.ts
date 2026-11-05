import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query';
import { ApiError } from '../api/problem';
import { sessionKey } from '../api/session';
import { notifyError } from './notifications';

/**
 * Server state (TanStack Query): cached, refreshed when the window gets focus again, retried once on network errors.
 * Every failed call is shown to the user (with its trace id); a 401 also refreshes the session, so the header switches
 * to "Se connecter" when the session has expired.
 */
export function createQueryClient(): QueryClient {
  const client: QueryClient = new QueryClient({
    queryCache: new QueryCache({ onError: (error) => onError(client, error) }),
    mutationCache: new MutationCache({ onError: (error) => onError(client, error) }),
    defaultOptions: {
      queries: {
        retry: (failures, error) => failures < 1 && !(error instanceof ApiError && error.status > 0),
      },
    },
  });
  return client;
}

function onError(client: QueryClient, error: Error) {
  notifyError(error);
  if (error instanceof ApiError && error.status === 401) {
    void client.invalidateQueries({ queryKey: sessionKey });
  }
}
