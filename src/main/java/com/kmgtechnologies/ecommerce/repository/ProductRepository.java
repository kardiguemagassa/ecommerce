package com.kmgtechnologies.ecommerce.repository;

import com.kmgtechnologies.ecommerce.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query(value = """
            SELECT new com.kmgtechnologies.ecommerce.repository.ProductRow(
                p.id, p.name, p.description, p.price, p.stock, c.id, c.name, p.imageKey,
                (SELECT avg(r.rating) FROM Review r WHERE r.product = p AND r.hidden = false),
                (SELECT count(r) FROM Review r WHERE r.product = p AND r.hidden = false))
            FROM Product p
            JOIN p.category c
            WHERE (:categoryId is null or c.id = :categoryId)
              AND lower(p.name) like lower(concat('%', :name, '%'))
            """,
            countQuery = """
            SELECT count(p)
            FROM Product p
            WHERE (:categoryId is null or p.category.id = :categoryId)
              AND lower(p.name) like lower(concat('%', :name, '%'))
            """)
    Page<ProductRow> search(@Param("categoryId") Long categoryId, @Param("name") String name, Pageable pageable);

    boolean existsByCategoryId(Long categoryId);

    @Query("""
            select new com.kmgtechnologies.ecommerce.repository.ProductRow(
                p.id, p.name, p.description, p.price, p.stock, c.id, c.name, p.imageKey,
                (select avg(r.rating) from Review r where r.product = p and r.hidden = false),
                (select count(r) from Review r where r.product = p and r.hidden = false))
            from Product p
            join p.category c
            where p.id = :id
            """)
    Optional<ProductRow> findRowById(@Param("id") Long id);
}
