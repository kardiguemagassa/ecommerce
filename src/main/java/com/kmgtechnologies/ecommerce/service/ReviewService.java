package com.kmgtechnologies.ecommerce.service;

import com.kmgtechnologies.ecommerce.config.CacheNames;
import com.kmgtechnologies.ecommerce.dto.MyReviewResponse;
import com.kmgtechnologies.ecommerce.dto.ProductRatingResponse;
import com.kmgtechnologies.ecommerce.dto.ReviewRequest;
import com.kmgtechnologies.ecommerce.dto.ReviewResponse;
import com.kmgtechnologies.ecommerce.dto.ReviewSummaryResponse;
import com.kmgtechnologies.ecommerce.dto.ReviewSummaryResponse.StarCount;
import com.kmgtechnologies.ecommerce.entity.OrderStatus;
import com.kmgtechnologies.ecommerce.entity.Product;
import com.kmgtechnologies.ecommerce.entity.Review;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.exception.ReviewNotAllowedException;
import com.kmgtechnologies.ecommerce.repository.CustomerRepository;
import com.kmgtechnologies.ecommerce.repository.OrderRepository;
import com.kmgtechnologies.ecommerce.repository.ProductRepository;
import com.kmgtechnologies.ecommerce.repository.ReviewRepository;
import com.kmgtechnologies.ecommerce.repository.StarCountRow;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Reviews of the products. Only a customer with a confirmed order of a product reviews it (a verified purchase), once:
 * writing again replaces their review. The product details carry the rating (cached): every change evicts them.
 */
@Service
@Transactional(readOnly = true)
public class ReviewService {

    /** Newest first; the id breaks ties of a same instant, so that pages never overlap. */
    static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final Clock clock;

    public ReviewService(ReviewRepository reviewRepository, ProductRepository productRepository,
                         CustomerRepository customerRepository, OrderRepository orderRepository, Clock clock) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    /** The visible reviews of a product, newest first. */
    public Page<ReviewResponse> list(Long productId, int page, int size) {
        requireProduct(productId);
        return reviewRepository.findByProductIdAndHiddenFalse(productId, PageRequest.of(page, size, NEWEST_FIRST))
                .map(ReviewResponse::from);
    }

    /** The rating of a product and its spread over 5 down to 1 stars. */
    public ReviewSummaryResponse summary(Long productId) {
        requireProduct(productId);
        ReviewRepository.RatingRow row = reviewRepository.ratingOf(productId);
        ProductRatingResponse rating = row == null
                ? ProductRatingResponse.NONE
                : ProductRatingResponse.of(row.getAverage(), row.getTotal());
        Map<Integer, Long> counts = reviewRepository.countByStars(productId).stream()
                .collect(Collectors.toMap(StarCountRow::stars, StarCountRow::count));
        List<StarCount> distribution = IntStream.iterate(5, stars -> stars >= 1, stars -> stars - 1)
                .mapToObj(stars -> new StarCount(stars, counts.getOrDefault(stars, 0L)))
                .toList();
        return new ReviewSummaryResponse(rating, distribution);
    }

    /** Whether the customer may review the product, and their review if any (hidden ones included: it is theirs). */
    public MyReviewResponse mine(Long customerId, Long productId) {
        requireProduct(productId);
        ReviewResponse review = reviewRepository.findByCustomerIdAndProductId(customerId, productId)
                .map(ReviewResponse::from)
                .orElse(null);
        return new MyReviewResponse(hasBought(customerId, productId), review);
    }

    /** Writes the customer's review of the product, or replaces it; 403 without a confirmed order of the product. */
    @Transactional
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#productId")
    public ReviewResponse save(Long customerId, Long productId, ReviewRequest request) {
        Product product = requireProduct(productId);
        if (!hasBought(customerId, productId)) {
            throw new ReviewNotAllowedException();
        }
        Instant now = Instant.now(clock);
        Review review = reviewRepository.findByCustomerIdAndProductId(customerId, productId)
                .map(existing -> {
                    existing.edit(request.rating(), request.comment(), now);
                    return existing;
                })
                .orElseGet(() -> reviewRepository.save(new Review(product,
                        customerRepository.getReferenceById(customerId), request.rating(), request.comment(), now)));
        return ReviewResponse.from(review);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#productId")
    public void delete(Long customerId, Long productId) {
        Review review = reviewRepository.findByCustomerIdAndProductId(customerId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Review of product", productId));
        reviewRepository.delete(review);
    }

    private boolean hasBought(Long customerId, Long productId) {
        return orderRepository.hasOrdered(customerId, productId, OrderStatus.CONFIRMED);
    }

    private Product requireProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    }
}
