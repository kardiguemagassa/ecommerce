// The OpenAPI contract of the API, kept in frontend/openapi.json for the generated client (src/api/schema.d.ts).
// The backend test OpenApiExportIT writes the live document to target/openapi.json on every `./mvnw verify`.
//   node scripts/openapi.mjs sync  <target/openapi.json>   copy it here, normalised (npm run api:sync)
//   node scripts/openapi.mjs check <target/openapi.json>   fail when the copy here is out of date (CI)
// Normalised: keys sorted (stable diffs), server URL and version left out (they change with the port and releases).
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const committed = join(dirname(fileURLToPath(import.meta.url)), '..', 'openapi.json');

function normalise(document) {
  const copy = structuredClone(document);
  delete copy.servers;
  if (copy.info) delete copy.info.version;
  return sortKeys(copy);
}

function sortKeys(value) {
  if (Array.isArray(value)) return value.map(sortKeys);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value).sort().map((key) => [key, sortKeys(value[key])]));
  }
  return value;
}

const serialise = (document) => JSON.stringify(normalise(document), null, 2) + '\n';
const read = (path) => JSON.parse(readFileSync(path, 'utf8'));

const [command, source] = process.argv.slice(2);
if (!['sync', 'check'].includes(command) || !source) {
  console.error('Usage: node scripts/openapi.mjs sync|check <path to the exported openapi.json>');
  process.exit(2);
}
const live = serialise(read(source));
if (command === 'sync') {
  writeFileSync(committed, live);
  console.log(`frontend/openapi.json updated from ${source}`);
} else if (live !== serialise(read(committed))) {
  console.error('frontend/openapi.json is out of date: the API changed.');
  console.error('Run ./mvnw verify, then `npm run api:sync` in frontend/, and commit openapi.json.');
  process.exit(1);
} else {
  console.log('frontend/openapi.json matches the API');
}
