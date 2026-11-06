/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// The API in development (./mvnw spring-boot:run or the compose stack).
const api = process.env.API_URL ?? 'http://localhost:8080';

// Development server: one origin for the browser. API calls, the Keycloak login (/oauth2, /login/oauth2) and the
// logout go to the API through this proxy, so the session cookie (SameSite=Lax) and the CSRF cookie just work, with
// no CORS. The Host header is kept: the API builds its OAuth2 redirect URI on http://localhost:5173.
// In the released image, nginx plays this part.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': api,
      '/oauth2': api,
      '/login/oauth2': api,
      '/logout': api,
    },
  },
  build: {
    // Source maps for debugging and error tracking, not referenced by the bundles (nginx does not serve them).
    sourcemap: 'hidden',
    // Every asset as a file, nothing inlined as data: URI: the Content Security Policy keeps font-src 'self', and the
    // files are cached with the bundles.
    assetsInlineLimit: 0,
    rolldownOptions: {
      output: {
        // Libraries apart from the application code: they change less often, browsers keep them cached across releases.
        codeSplitting: {
          groups: [
            { name: 'mui', test: /node_modules[\\/](@mui|@emotion)[\\/]/ },
            { name: 'vendor', test: /node_modules[\\/]/ },
          ],
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    restoreMocks: true,
    // The first test of a file loads React, MUI and jsdom: seconds on a busy laptop or CI runner, not a hang.
    testTimeout: 20_000,
  },
});
