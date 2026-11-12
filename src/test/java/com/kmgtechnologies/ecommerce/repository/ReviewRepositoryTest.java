package com.kmgtechnologies.ecommerce.repository;

import com.kmgtechnologies.ecommerce.entity.Category;
import com.kmgtechnologies.ecommerce.entity.Customer;
import com.kmgtechnologies.ecommerce.entity.Order;
import com.kmgtechnologies.ecommerce.entity.OrderStatus;
import com.kmgtechnologies.ecommerce.entity.Product;
import com.kmgtechnologies.ecommerce.entity.Review;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class ReviewRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Product lamp;
    private Customer alice;
    private Customer bob;
    private Customer carol;

    @BeforeEach
    void setUp() {
        Category home = entityManager.persist(new Category("Home"));
        lamp = entityManager.persist(new Product("Lamp", null, new BigDecimal("34.50"), 10, home));
        alice = entityManager.persist(new Customer("alice@example.com", "Alice Martin"));
        bob = entityManager.persist(new Customer("bob@example.com", "Bob Durand"));
        carol = entityManager.persist(new Customer("carol@example.com", "Carol"));
        entityManager.persist(new Review(lamp, alice, 5, "Parfaite", NOW));
        entityManager.persist(new Review(lamp, bob, 4, null, NOW.plusSeconds(60)));
        Review hidden = new Review(lamp, carol, 1, "spam", NOW.plusSeconds(120));
        hidden.setHidden(true);
        entityManager.persist(hidden);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void ratingOf_averagesTheVisibleReviewsOnly() {
        ReviewRepository.RatingRow rating = reviewRepository.ratingOf(lamp.getId());

        assertThat(rating.getAverage()).isEqualTo(4.5);
        assertThat(rating.getTotal()).isEqualTo(2);
    }

    @Test
    void ratingOf_aProductWithoutReview_isEmpty() {
        Product other = entityManager.persist(new Product("Other", null, BigDecimal.TEN, 1,
                entityManager.find(Category.class, lamp.getCategory().getId())));

        ReviewRepository.RatingRow rating = reviewRepository.ratingOf(other.getId());

        assertThat(rating.getAverage()).isNull();
        assertThat(rating.getTotal()).isZero();
    }

    @Test
    void countByStars_groupsTheVisibleReviews() {
        assertThat(reviewRepository.countByStars(lamp.getId()))
                .containsExactlyInAnyOrder(new StarCountRow(5, 1L), new StarCountRow(4, 1L));
    }

    @Test
    void visibleReviews_comeNewestFirst_withTheirAuthors() {
        var page = reviewRepository.findByProductIdAndHiddenFalse(lamp.getId(),
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"))));

        assertThat(page.getContent()).extracting(review -> review.getCustomer().getFullName())
                .containsExactly("Bob Durand", "Alice Martin");
    }

    @Test
    void findForModeration_filtersOnTheVisibility_orGivesThemAll() {
        PageRequest page = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt")));

        assertThat(reviewRepository.findForModeration(null, page).getContent()).hasSize(3);
        assertThat(reviewRepository.findForModeration(true, page).getContent())
                .extracting(Review::getComment).containsExactly("spam");
        assertThat(reviewRepository.findForModeration(false, page).getContent())
                .extracting(review -> review.getProduct().getName()).containsExactly("Lamp", "Lamp");
    }

    @Test
    void aCustomerReviewsAProductOnce() {
        Review again = new Review(entityManager.find(Product.class, lamp.getId()),
                entityManager.find(Customer.class, alice.getId()), 3, null, NOW);

        // Through the repository: Spring translates the unique constraint into DataIntegrityViolationException
        assertThatThrownBy(() -> reviewRepository.saveAndFlush(again)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void productSearchAndDetails_carryTheRating() {
        ProductRow row = productRepository.findRowById(lamp.getId()).orElseThrow();
        assertThat(row.ratingAverage()).isEqualTo(4.5);
        assertThat(row.reviewCount()).isEqualTo(2);

        ProductRow found = productRepository.search(null, "lamp", PageRequest.of(0, 10)).getContent().getFirst();
        assertThat(found.reviewCount()).isEqualTo(2);
    }

    @Test
    void hasOrdered_needsAnOrderOfTheProductWithTheStatus() {
        Order order = new Order(entityManager.find(Customer.class, alice.getId()), NOW);
        order.addItem(entityManager.find(Product.class, lamp.getId()), 1);
        orderRepository.saveAndFlush(order);

        assertThat(orderRepository.hasOrdered(alice.getId(), lamp.getId(), OrderStatus.PENDING)).isTrue();
        assertThat(orderRepository.hasOrdered(alice.getId(), lamp.getId(), OrderStatus.CONFIRMED)).isFalse();
        assertThat(orderRepository.hasOrdered(bob.getId(), lamp.getId(), OrderStatus.PENDING)).isFalse();

        order.confirm();
        orderRepository.saveAndFlush(order);
        assertThat(orderRepository.hasOrdered(alice.getId(), lamp.getId(), OrderStatus.CONFIRMED)).isTrue();
    }
}
