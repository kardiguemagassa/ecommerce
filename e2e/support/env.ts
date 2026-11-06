import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/** The repository: scripts/ and .env live there. */
export const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');

/** A setting from the environment, else from the .env of the repository (CI writes one, a developer has one). */
export function setting(name: string): string {
  const fromEnv = process.env[name];
  if (fromEnv) return fromEnv;
  const file = resolve(ROOT, '.env');
  if (existsSync(file)) {
    const line = readFileSync(file, 'utf8').split('\n').findLast((entry) => entry.startsWith(`${name}=`));
    if (line) return line.slice(name.length + 1).trim();
  }
  throw new Error(`${name} is not set (environment or .env)`);
}

export const BASE_URL = process.env.BASE_URL ?? 'http://localhost:8080';

/** Demo accounts of the realm; their password is DEMO_USER_PASSWORD, the administrator's e-mail SHOP_ADMIN_EMAIL. */
export const CUSTOMER = { email: 'alice@example.com', name: 'Alice Martin', state: '.auth/customer.json' };
export const ADMIN = { email: () => setting('SHOP_ADMIN_EMAIL'), state: '.auth/admin.json' };

/** Suffix of the data a run creates: two runs never collide, and the data is recognisable ("E2E …"). */
export const RUN = Date.now().toString(36);
