package com.kmgtechnologies.ecommerce.repository;

import com.kmgtechnologies.ecommerce.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByCustomerIdAndProductId(Long customerId, Long productId);

    /** The reviews shown on the product page, with their authors (one query). */
    @EntityGraph(attributePaths = "customer")
    Page<Review> findByProductIdAndHiddenFalse(Long productId, Pageable pageable);

    @Query("""
            select new com.kmgtechnologies.ecommerce.repository.StarCountRow(r.rating, count(r))
            from Review r
            where r.product.id = :productId and r.hidden = false
            group by r.rating
            """)
    List<StarCountRow> countByStars(@Param("productId") Long productId);

    @Query("""
            select avg(r.rating) as average, count(r) as total
            from Review r
            where r.product.id = :productId and r.hidden = false
            """)
    RatingRow ratingOf(@Param("productId") Long productId);

    /** AVG (null without review) and COUNT of the visible reviews of a product. */
    interface RatingRow {
        Double getAverage();

        Long getTotal();
    }
}
