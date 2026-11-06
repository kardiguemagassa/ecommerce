import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';
import { ROOT } from './env';

/**
 * After the run, the categories and products the tests created ("E2E …") leave the database, with their orders: the
 * developer's catalogue keeps only its own data. KEEP_TEST_DATA=1 keeps them, to look at them after a run.
 */
export default function globalTeardown() {
  execFileSync(resolve(ROOT, 'scripts/clean-test-data.sh'), { cwd: ROOT, stdio: 'inherit' });
}
