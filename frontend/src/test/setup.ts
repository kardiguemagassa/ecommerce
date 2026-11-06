import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { clearToasts } from '../app/notifications';
import { server } from './server';

// findBy* and waitFor wait 1 s by default: too short for a first render on a slower machine.
configure({ asyncUtilTimeout: 5_000 });

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => {
  cleanup();
  server.resetHandlers();
  clearToasts();
  document.cookie.split('; ').forEach((cookie) => {
    document.cookie = `${cookie.split('=')[0]}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
  });
});
afterAll(() => server.close());
