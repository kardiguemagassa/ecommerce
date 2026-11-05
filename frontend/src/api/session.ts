import { useQuery } from '@tanstack/react-query';
import { api, readCookie } from './client';
import { ApiError, unwrap } from './problem';

export type Role = 'CUSTOMER' | 'ADMIN';

export interface Session {
  authenticated: boolean;
  name?: string;
  email?: string;
  roles: string[];
  avatarUrl?: string;
}

export const sessionKey = ['session'] as const;

/** Who is signed in (BFF session): never a token, only what the interface shows. Also sets the XSRF-TOKEN cookie. */
export async function fetchSession(): Promise<Session> {
  const session = await unwrap(api.GET('/api/me/session'));
  return {
    authenticated: session.authenticated ?? false,
    name: session.name,
    email: session.email,
    roles: session.roles ?? [],
    avatarUrl: session.avatarUrl,
  };
}

export function useSession() {
  return useQuery({ queryKey: sessionKey, queryFn: fetchSession, staleTime: 60_000 });
}

export function hasRole(session: Session | undefined, role: Role): boolean {
  return session?.authenticated === true && session.roles.includes(role);
}

/** Browser login through the API (authorization code with PKCE at Keycloak), then back to the front-end home. */
export function signIn(): void {
  window.location.assign('/oauth2/authorization/keycloak');
}

/**
 * Closes the API session, then the Keycloak one (single sign-on), whose end-session URL the API returns: without
 * it, the next "Se connecter" would sign the user in again without asking.
 */
export async function signOut(): Promise<void> {
  const token = readCookie('XSRF-TOKEN');
  const response = await fetch('/logout', {
    method: 'POST',
    credentials: 'same-origin',
    headers: token ? { 'X-XSRF-TOKEN': token } : {},
  });
  if (!response.ok) {
    throw new ApiError(response.status, {});
  }
  const { logoutUrl } = (await response.json()) as { logoutUrl: string };
  window.location.assign(logoutUrl);
}
