-- Case-insensitive uniqueness of category names, enforced by the database.
-- The service still checks first for a clear error message; this index closes the race
-- window between that check and the insert (two concurrent "Books" / "books" requests).
create unique index uk_categories_name_lower on categories (lower(name));
