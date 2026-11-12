-- Reviews of the products by the customers who bought them: one per customer and product, 1 to 5 stars, an optional
-- comment. A moderator hides an abusive one (kept, never shown). The average and the count are computed when read.
create sequence reviews_seq start with 1 increment by 50;

create table reviews (
    id          bigint                      not null,
    product_id  bigint                      not null,
    customer_id bigint                      not null,
    rating      integer                     not null,
    comment     varchar(1000),
    hidden      boolean                     not null default false,
    created_at  timestamp(6) with time zone not null,
    updated_at  timestamp(6) with time zone not null,
    version     bigint,
    constraint pk_reviews primary key (id),
    constraint uk_reviews_customer_product unique (customer_id, product_id),
    constraint ck_reviews_rating check (rating between 1 and 5),
    constraint fk_reviews_product foreign key (product_id) references products (id) on delete cascade,
    constraint fk_reviews_customer foreign key (customer_id) references customers (id)
);
-- The visible reviews of a product, newest first (the product page, its average and count).
create index idx_reviews_product_visible on reviews (product_id, created_at desc) where not hidden;
