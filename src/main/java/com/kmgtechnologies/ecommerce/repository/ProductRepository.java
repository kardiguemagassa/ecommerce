package com.kmgtechnologies.ecommerce.repository;

import com.kmgtechnologies.ecommerce.dto.ProductResponse;
import com.kmgtechnologies.ecommerce.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query(value = """
            SELECT new com.kmgtechnologies.ecommerce.dto.ProductResponse(
                p.id, p.name, p.description, p.price, p.stock, c.id, c.name)
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
    Page<ProductResponse> search (@Param("categoryId") Long categoryId, @Param("name") String name, Pageable pageable);
}
