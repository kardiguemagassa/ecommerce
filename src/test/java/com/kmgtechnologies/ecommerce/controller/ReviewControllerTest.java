package com.kmgtechnologies.ecommerce.controller;

import com.kmgtechnologies.ecommerce.dto.MyReviewResponse;
import com.kmgtechnologies.ecommerce.dto.ProductRatingResponse;
import com.kmgtechnologies.ecommerce.dto.ReviewRequest;
import com.kmgtechnologies.ecommerce.dto.ReviewResponse;
import com.kmgtechnologies.ecommerce.dto.ReviewSummaryResponse;
import com.kmgtechnologies.ecommerce.exception.ReviewNotAllowedException;
import com.kmgtechnologies.ecommerce.security.BffTestConfiguration;
import com.kmgtechnologies.ecommerce.security.CurrentCustomer;
import com.kmgtechnologies.ecommerce.security.SecurityConfig;
import com.kmgtechnologies.ecommerce.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
@Import({SecurityConfig.class, BffTestConfiguration.class})
@ActiveProfiles("test")
class ReviewControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final ReviewResponse REVIEW = new ReviewResponse(3L, 5, "Parfaite", "Alice M.", NOW, NOW);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    /** Requests are authenticated by @WithMockUser: no token to decode, no Keycloak. */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private CurrentCustomer currentCustomer;

    @BeforeEach
    void signedInAsCustomer1() {
        when(currentCustomer.id()).thenReturn(1L);
    }

    @Test
    void productReviews_areReadByAnyone() throws Exception {
        when(reviewService.list(7L, 0, 10)).thenReturn(new PageImpl<>(List.of(REVIEW), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/products/7/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].author").value("Alice M."))
                .andExpect(jsonPath("$.content[0].rating").value(5))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void productReviews_refuseAPageAbove50() throws Exception {
        mockMvc.perform(get("/api/products/7/reviews").param("size", "51"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reviewService);
    }

    @Test
    void reviewSummary_givesTheSpread() throws Exception {
        when(reviewService.summary(7L)).thenReturn(new ReviewSummaryResponse(new ProductRatingResponse(4.5, 2),
                List.of(new ReviewSummaryResponse.StarCount(5, 1), new ReviewSummaryResponse.StarCount(4, 1))));

        mockMvc.perform(get("/api/products/7/reviews/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating.average").value(4.5))
                .andExpect(jsonPath("$.distribution[0].stars").value(5));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void myReview_saysWhetherICanReview() throws Exception {
        when(reviewService.mine(1L, 7L)).thenReturn(new MyReviewResponse(true, null));

        mockMvc.perform(get("/api/me/products/7/review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void saveMyReview_returnsIt() throws Exception {
        when(reviewService.save(1L, 7L, new ReviewRequest(5, "Parfaite"))).thenReturn(REVIEW);

        mockMvc.perform(put("/api/me/products/7/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating": 5, "comment": "Parfaite"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void saveMyReview_refusesARatingOutside1To5() throws Exception {
        mockMvc.perform(put("/api/me/products/7/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating": 6}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rating").value("Rating must be between 1 and 5"));

        verifyNoInteractions(reviewService);
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void saveMyReview_withoutPurchase_is403WithItsCode() throws Exception {
        when(reviewService.save(any(), any(), any())).thenThrow(new ReviewNotAllowedException());

        mockMvc.perform(put("/api/me/products/7/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating": 4}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));
    }

    @Test
    void saveMyReview_requiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/me/products/7/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating": 4}
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reviewService);
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void deleteMyReview_returns204() throws Exception {
        mockMvc.perform(delete("/api/me/products/7/review"))
                .andExpect(status().isNoContent());

        verify(reviewService).delete(1L, 7L);
    }
}
