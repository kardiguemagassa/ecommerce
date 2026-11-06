import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { clearToasts } from '../app/notifications';
import { server } from './server';

// jsdom's Blob (and File) has no stream(), which Node's fetch reads to send a multipart body (photo upload).
Blob.prototype.stream ??= function stream(this: Blob) {
  return new ReadableStream<Uint8Array<ArrayBuffer>>({
    start: async (controller) => {
      controller.enqueue(new Uint8Array(await this.arrayBuffer()));
      controller.close();
    },
  });
};

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
