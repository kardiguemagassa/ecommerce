package com.kmgtechnologies.ecommerce.controller;

import com.kmgtechnologies.ecommerce.dto.AdminReviewResponse;
import com.kmgtechnologies.ecommerce.security.BffTestConfiguration;
import com.kmgtechnologies.ecommerce.security.SecurityConfig;
import com.kmgtechnologies.ecommerce.service.ReviewService;
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

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminReviewController.class)
@Import({SecurityConfig.class, BffTestConfiguration.class})
@ActiveProfiles("test")
class AdminReviewControllerTest {

    private static final AdminReviewResponse SPAM = new AdminReviewResponse(3L, 7L, "Lamp", "Carol Petit",
            "carol@example.com", 1, "spam", true, Instant.parse("2026-11-07T12:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    /** Requests are authenticated by @WithMockUser: no token to decode, no Keycloak. */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(roles = "ADMIN")
    void moderatedReviews_filterOnTheVisibility() throws Exception {
        when(reviewService.moderation(true, 0, 20)).thenReturn(new PageImpl<>(List.of(SPAM), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin/reviews").param("hidden", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].authorEmail").value("carol@example.com"))
                .andExpect(jsonPath("$.content[0].hidden").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setReviewVisibility_hidesTheReview() throws Exception {
        when(reviewService.setHidden(3L, true)).thenReturn(SPAM);

        mockMvc.perform(put("/api/admin/reviews/3/visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hidden": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setReviewVisibility_needsTheVisibility() throws Exception {
        mockMvc.perform(put("/api/admin/reviews/3/visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.hidden").value("Hidden is required"));

        verifyNoInteractions(reviewService);
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void moderation_isForbiddenToCustomers() throws Exception {
        mockMvc.perform(get("/api/admin/reviews"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/reviews/3/visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hidden": true}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reviewService);
    }
}
