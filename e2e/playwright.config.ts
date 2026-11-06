import { defineConfig, devices } from '@playwright/test';
import { BASE_URL } from './support/env';

/**
 * The shop in a real browser, on the whole stack started by `docker compose --profile app up`: nginx, BFF session,
 * Keycloak login, API, PostgreSQL, media storage. The setup project signs each demo account in once, through the
 * Keycloak form; the tests start from those sessions.
 */
export default defineConfig({
  testDir: './tests',
  // One worker: the tests share the demo accounts (alice's cart, her orders).
  workers: 1,
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: process.env.CI ? [['github'], ['list'], ['html', { open: 'never' }]] : [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: BASE_URL,
    locale: 'fr-FR',
    timezoneId: 'Europe/Paris',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    // No trace nor screenshot of the logins: they would show the password typed in the Keycloak form.
    { name: 'setup', testMatch: /auth\.setup\.ts/, use: { trace: 'off', screenshot: 'off' } },
    { name: 'desktop', use: { ...devices['Desktop Chrome'] }, dependencies: ['setup'], testIgnore: [/mobile\.spec\.ts/, /auth\.setup\.ts/] },
    { name: 'mobile', use: { ...devices['Pixel 7'] }, dependencies: ['setup'], testMatch: /mobile\.spec\.ts/ },
    // README screenshots, on the demo data: only with npm run screenshots.
    ...(process.env.SCREENSHOTS
      ? [{ name: 'screenshots', testDir: './screenshots', testMatch: /\.shots\.ts/, dependencies: ['setup'], use: { ...devices['Desktop Chrome'] } }]
      : []),
  ],
});
