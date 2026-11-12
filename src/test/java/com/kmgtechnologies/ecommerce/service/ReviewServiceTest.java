package com.kmgtechnologies.ecommerce.service;

import com.kmgtechnologies.ecommerce.dto.AdminReviewResponse;
import com.kmgtechnologies.ecommerce.dto.MyReviewResponse;
import com.kmgtechnologies.ecommerce.dto.ReviewRequest;
import com.kmgtechnologies.ecommerce.dto.ReviewResponse;
import com.kmgtechnologies.ecommerce.dto.ReviewSummaryResponse;
import com.kmgtechnologies.ecommerce.entity.Category;
import com.kmgtechnologies.ecommerce.entity.Customer;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private OrderRepository orderRepository;

    private ReviewService reviewService;
    private Product lamp;
    private Customer alice;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepository, productRepository, customerRepository, orderRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lamp = new Product("Lamp", null, new BigDecimal("34.50"), 10, new Category("Home"));
        alice = new Customer("alice@example.com", "Alice Martin");
    }

    @Test
    void save_withoutAConfirmedOrderOfTheProduct_isRefused() {
        when(productRepository.findById(7L)).thenReturn(Optional.of(lamp));
        when(orderRepository.hasOrdered(1L, 7L, OrderStatus.CONFIRMED)).thenReturn(false);

        assertThatThrownBy(() -> reviewService.save(1L, 7L, new ReviewRequest(5, "Super")))
                .isInstanceOf(ReviewNotAllowedException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void save_writesTheFirstReview_ofABuyer() {
        when(productRepository.findById(7L)).thenReturn(Optional.of(lamp));
        when(orderRepository.hasOrdered(1L, 7L, OrderStatus.CONFIRMED)).thenReturn(true);
        when(reviewRepository.findByCustomerIdAndProductId(1L, 7L)).thenReturn(Optional.empty());
        when(customerRepository.getReferenceById(1L)).thenReturn(alice);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponse saved = reviewService.save(1L, 7L, new ReviewRequest(4, "  Belle lumière  "));

        assertThat(saved.rating()).isEqualTo(4);
        assertThat(saved.comment()).isEqualTo("Belle lumière");
        assertThat(saved.author()).isEqualTo("Alice M.");
        assertThat(saved.createdAt()).isEqualTo(NOW);
    }

    @Test
    void save_replacesTheReviewOfTheCustomer_keepingItsDate() {
        Review existing = new Review(lamp, alice, 2, "Bof", NOW.minusSeconds(3600));
        when(productRepository.findById(7L)).thenReturn(Optional.of(lamp));
        when(orderRepository.hasOrdered(1L, 7L, OrderStatus.CONFIRMED)).thenReturn(true);
        when(reviewRepository.findByCustomerIdAndProductId(1L, 7L)).thenReturn(Optional.of(existing));

        ReviewResponse saved = reviewService.save(1L, 7L, new ReviewRequest(5, " "));

        assertThat(saved.rating()).isEqualTo(5);
        assertThat(saved.comment()).isNull();
        assertThat(saved.createdAt()).isEqualTo(NOW.minusSeconds(3600));
        assertThat(saved.updatedAt()).isEqualTo(NOW);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void summary_spreadsTheReviewsFrom5To1Stars_zerosIncluded() {
        when(productRepository.findById(7L)).thenReturn(Optional.of(lamp));
        when(reviewRepository.ratingOf(7L)).thenReturn(rating(4.333, 3L));
        when(reviewRepository.countByStars(7L)).thenReturn(List.of(new StarCountRow(5, 2L), new StarCountRow(3, 1L)));

        ReviewSummaryResponse summary = reviewService.summary(7L);

        assertThat(summary.rating().average()).isEqualTo(4.3);
        assertThat(summary.rating().count()).isEqualTo(3);
        assertThat(summary.distribution()).extracting(ReviewSummaryResponse.StarCount::stars).containsExactly(5, 4, 3, 2, 1);
        assertThat(summary.distribution()).extracting(ReviewSummaryResponse.StarCount::count).containsExactly(2L, 0L, 1L, 0L, 0L);
    }

    @Test
    void mine_saysWhetherTheCustomerMayReview_andGivesTheirReview() {
        when(productRepository.findById(7L)).thenReturn(Optional.of(lamp));
        when(orderRepository.hasOrdered(1L, 7L, OrderStatus.CONFIRMED)).thenReturn(true);
        when(reviewRepository.findByCustomerIdAndProductId(1L, 7L)).thenReturn(Optional.empty());

        MyReviewResponse mine = reviewService.mine(1L, 7L);

        assertThat(mine.allowed()).isTrue();
        assertThat(mine.review()).isNull();
        assertThat(mine.hidden()).isFalse();
    }

    @Test
    void mine_tellsTheAuthorThatTheModerationHidTheirReview() {
        Review hidden = new Review(lamp, alice, 1, "spam", NOW);
        hidden.setHidden(true);
        when(productRepository.findById(7L)).thenReturn(Optional.of(lamp));
        when(orderRepository.hasOrdered(1L, 7L, OrderStatus.CONFIRMED)).thenReturn(true);
        when(reviewRepository.findByCustomerIdAndProductId(1L, 7L)).thenReturn(Optional.of(hidden));

        MyReviewResponse mine = reviewService.mine(1L, 7L);

        assertThat(mine.review().rating()).isEqualTo(1);
        assertThat(mine.hidden()).isTrue();
    }

    @Test
    void setHidden_hidesTheReview_andGivesItBackForTheModeration() {
        Review review = new Review(lamp, alice, 1, "spam", NOW);
        when(reviewRepository.findById(3L)).thenReturn(Optional.of(review));

        AdminReviewResponse moderated = reviewService.setHidden(3L, true);

        assertThat(review.isHidden()).isTrue();
        assertThat(moderated.hidden()).isTrue();
        assertThat(moderated.authorEmail()).isEqualTo("alice@example.com");
        assertThat(moderated.productName()).isEqualTo("Lamp");
    }

    @Test
    void setHidden_ofAnUnknownReview_is404() {
        when(reviewRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.setHidden(3L, true)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_withoutReview_is404() {
        when(reviewRepository.findByCustomerIdAndProductId(1L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.delete(1L, 7L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesTheReviewOfTheCustomer() {
        Review existing = new Review(lamp, alice, 2, null, NOW);
        when(reviewRepository.findByCustomerIdAndProductId(1L, 7L)).thenReturn(Optional.of(existing));

        reviewService.delete(1L, 7L);

        ArgumentCaptor<Review> deleted = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).delete(deleted.capture());
        assertThat(deleted.getValue()).isSameAs(existing);
    }

    @Test
    void list_ofAnUnknownProduct_is404() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.list(99L, 0, 10)).isInstanceOf(ResourceNotFoundException.class);
    }

    private static ReviewRepository.RatingRow rating(Double average, Long total) {
        return new ReviewRepository.RatingRow() {
            @Override
            public Double getAverage() {
                return average;
            }

            @Override
            public Long getTotal() {
                return total;
            }
        };
    }
}
