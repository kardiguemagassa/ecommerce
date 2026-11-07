package com.kmgtechnologies.ecommerce.repository;

import com.kmgtechnologies.ecommerce.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    /** The views of a product, main image first. */
    List<ProductImage> findByProductIdOrderByDisplayOrderAsc(Long productId);
}
