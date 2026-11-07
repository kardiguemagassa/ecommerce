-- A product shows up to four views; the first one (display_order 0) is its main image.
-- products.image_key stays as a copy of the main image's key: the catalogue, the cart and the best sellers read it in
-- the same query as the product, without joining the views. The entity keeps the two in step.
create sequence product_images_seq start with 1 increment by 50;

create table product_images (
    id            bigint       not null,
    product_id    bigint       not null,
    image_key     varchar(200) not null,
    display_order integer      not null,
    constraint pk_product_images primary key (id),
    constraint fk_product_images_product foreign key (product_id) references products (id) on delete cascade,
    constraint uk_product_images_image_key unique (image_key),
    constraint ck_product_images_display_order check (display_order between 0 and 3),
    -- Checked at commit: moving the views around swaps their orders within one transaction.
    constraint uk_product_images_order unique (product_id, display_order) deferrable initially deferred
);

-- The image of each product becomes its main view.
insert into product_images (id, product_id, image_key, display_order)
select nextval('product_images_seq'), id, image_key, 0
from products
where image_key is not null;
