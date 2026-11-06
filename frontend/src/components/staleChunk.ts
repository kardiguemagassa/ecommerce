/**
 * A page module that no longer exists on the server: the site was deployed again while this tab kept the previous
 * index.html, whose chunks have new hashed names now. Reloading fetches the new version.
 */
export function isStaleChunkError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error);
  return /dynamically imported module|Importing a module script failed|error loading dynamically imported module|Unable to preload CSS/i.test(
    message,
  );
}
