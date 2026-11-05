-- Product images and customer avatars live in object storage (S3 API); the database keeps their object key only,
-- never a URL: the public address of the storage changes between environments (direct, behind nginx, a CDN).
-- Nullable: an image is optional.
alter table products add column image_key varchar(200);
alter table customers add column avatar_key varchar(200);
