#!/usr/bin/env python3
"""Images of the media storage that no product and no customer uses any more, listed, and removed on request.

    ./scripts/clean-orphan-media.py            # lists the orphans, removes nothing
    ./scripts/clean-orphan-media.py --delete   # removes them

An image becomes an orphan when its row goes without the API (SQL by hand, test data removed before its images went
with it), or when the storage could not remove it. Orphans are never served (their key is in no response): they only
take room.

Objects are listed and removed through the file API of SeaweedFS, inside its container (docker compose exec media);
the keys in use come from the database (products.image_key, customers.avatar_key). An object younger than one hour is
never touched: it may be an upload whose row is not committed yet. Stack running: docker compose up -d --wait.
Python standard library only. Development only (the storage of another environment is not SeaweedFS).
"""
import datetime
import json
import re
import subprocess
import sys

FOLDERS = ("products", "avatars")
MIN_AGE = datetime.timedelta(hours=1)
PAGE = 1000


def compose(*args, stdin=None):
    """Output of a command run in a container of the Compose stack; stops the script on a failure."""
    result = subprocess.run(["docker", "compose", "exec", "-T", *args], input=stdin, capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit(f"FAIL: docker compose exec {args[0]}: {result.stderr.strip() or result.stdout.strip()}")
    return result.stdout


def keys_in_use():
    sql = ("SELECT image_key FROM products WHERE image_key IS NOT NULL "
           "UNION SELECT avatar_key FROM customers WHERE avatar_key IS NOT NULL")
    psql = 'psql -v ON_ERROR_STOP=1 -qAt -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
    out = compose("postgres", "sh", "-c", psql, stdin=sql)
    return {line.strip() for line in out.splitlines() if line.strip()}


def parse_time(value):
    """Filer times are RFC 3339 with nanoseconds; Python 3.9 reads neither the nanoseconds nor a final Z."""
    value = re.sub(r"\.\d+", "", value).replace("Z", "+00:00")
    return datetime.datetime.fromisoformat(value)


def stored_objects(folder):
    """(key, size, modified) of every file of a folder of the bucket, page by page."""
    objects, last = [], ""
    while True:
        url = f"http://127.0.0.1:8888/buckets/$S3_BUCKET/{folder}/?limit={PAGE}&lastFileName={last}"
        listing = compose("media", "sh", "-c", f'curl -fs -H "Accept: application/json" "{url}"')
        page = json.loads(listing or "{}")
        entries = page.get("Entries") or []
        for entry in entries:
            path = entry["FullPath"]
            key = path.split("/", 3)[3]  # /buckets/<bucket>/<key>
            if entry.get("Mode", 0) & 0o20000000000:  # a sub-folder: not an image of ours
                continue
            objects.append((key, entry.get("FileSize", 0), parse_time(entry["Mtime"])))
        if not page.get("ShouldDisplayLoadMore") or not entries:
            return objects
        last = page.get("LastFileName") or entries[-1]["FullPath"].rsplit("/", 1)[1]


def remove(key):
    """True once the storage removed the object; a failure is reported, the others still go."""
    url = f"http://127.0.0.1:8888/buckets/$S3_BUCKET/{key}"
    command = ["docker", "compose", "exec", "-T", "media", "sh", "-c", f'curl -fs -o /dev/null -X DELETE "{url}"']
    return subprocess.run(command, capture_output=True).returncode == 0


def main():
    delete = sys.argv[1:] == ["--delete"]
    if sys.argv[1:] not in ([], ["--delete"]):
        sys.exit(__doc__)

    used = keys_in_use()
    now = datetime.datetime.now(datetime.timezone.utc)
    orphans, recent, total = [], 0, 0
    for folder in FOLDERS:
        for key, size, modified in stored_objects(folder):
            total += 1
            if key in used:
                continue
            if now - modified < MIN_AGE:
                recent += 1
            else:
                orphans.append((key, size))

    weight = sum(size for _, size in orphans)
    print(f"{total} images stored, {len(used)} in use, {len(orphans)} orphans ({weight / 1024:.0f} KB)"
          + (f", {recent} recent ones left alone" if recent else ""))
    for key, size in orphans:
        print(f"  {key}  {size / 1024:.0f} KB")
    if not orphans:
        return
    if not delete:
        print("Nothing removed: run again with --delete to remove them.")
        return

    failed = [key for key, _ in orphans if not remove(key)]
    for key in failed:
        print(f"WARN: {key} not removed", file=sys.stderr)
    print(f"OK  {len(orphans) - len(failed)} orphan images removed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
