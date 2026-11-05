import createClient, { type Middleware } from 'openapi-fetch';
import type { paths } from './schema';

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);

/** Value of a cookie readable by JavaScript, or undefined. */
export function readCookie(name: string): string | undefined {
  const prefix = `${name}=`;
  const entry = document.cookie.split('; ').find((cookie) => cookie.startsWith(prefix));
  return entry ? decodeURIComponent(entry.slice(prefix.length)) : undefined;
}

/**
 * CSRF protection of the session (Spring Security): the API writes the token in the XSRF-TOKEN cookie, the front-end
 * sends it back in the X-XSRF-TOKEN header with every write. Another site can make the browser send the cookie, not
 * read it: its forged requests lack the header and are refused (403).
 */
export const csrfMiddleware: Middleware = {
  onRequest({ request }) {
    if (!SAFE_METHODS.has(request.method)) {
      const token = readCookie('XSRF-TOKEN');
      if (token) request.headers.set('X-XSRF-TOKEN', token);
    }
    return request;
  },
};

/**
 * Typed client of the API, generated from openapi.json: paths, parameters and bodies are checked at compile time.
 * Same origin as the page (Vite proxy in development, nginx in the image): the session cookie goes along, no token
 * is ever handled here.
 */
export const api = createClient<paths>({
  baseUrl: globalThis.location?.origin ?? '',
  credentials: 'same-origin',
  // Resolved at each call, not when this module loads: tools that wrap fetch (tests, monitoring) are honoured.
  fetch: (request) => globalThis.fetch(request),
});
api.use(csrfMiddleware);
