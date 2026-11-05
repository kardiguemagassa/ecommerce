-- Customers become Keycloak users: the subject of the token ("sub") identifies them.
-- Nullable: customers created before authentication are linked on their first sign-in (same verified e-mail).
alter table customers add column external_id varchar(255);
alter table customers add constraint uk_customers_external_id unique (external_id);
