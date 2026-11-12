package com.kmgtechnologies.ecommerce.controller;

import com.kmgtechnologies.ecommerce.dto.MyReviewResponse;
import com.kmgtechnologies.ecommerce.dto.ReviewRequest;
import com.kmgtechnologies.ecommerce.dto.ReviewResponse;
import com.kmgtechnologies.ecommerce.dto.ReviewSummaryResponse;
import com.kmgtechnologies.ecommerce.security.CurrentCustomer;
import com.kmgtechnologies.ecommerce.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.BAD_REQUEST;
import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.NOT_FOUND;

@Tag(name = "Reviews", description = "Reviews of the products: read by everyone, written by the customers who bought them")
@RestController
public class ReviewController {

    private final ReviewService reviewService;
    private final CurrentCustomer currentCustomer;

    public ReviewController(ReviewService reviewService, CurrentCustomer currentCustomer) {
        this.reviewService = reviewService;
        this.currentCustomer = currentCustomer;
    }

    @Operation(summary = "Reviews of a product", description = "Visible reviews, newest first; 10 a page by default.")
    @ApiResponse(responseCode = "200", description = "Page of reviews returned")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @GetMapping("/api/products/{id}/reviews")
    public PagedModel<ReviewResponse> productReviews(
            @PathVariable Long id,
            @Parameter(description = "Page number, from 0") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1-50)") @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return new PagedModel<>(reviewService.list(id, page, size));
    }

    @Operation(summary = "Rating of a product", description = "Average, count and spread over 5 down to 1 stars.")
    @ApiResponse(responseCode = "200", description = "Summary returned")
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @GetMapping("/api/products/{id}/reviews/summary")
    public ReviewSummaryResponse reviewSummary(@PathVariable Long id) {
        return reviewService.summary(id);
    }

    @Operation(summary = "My review of a product",
            description = "Whether I may review it (a confirmed order of the product) and my review, if any.")
    @ApiResponse(responseCode = "200", description = "Returned")
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @GetMapping("/api/me/products/{id}/review")
    public MyReviewResponse myReview(@PathVariable Long id) {
        return reviewService.mine(currentCustomer.id(), id);
    }

    @Operation(summary = "Write or change my review of a product",
            description = "1 to 5 stars, a comment of 1000 characters at most. Replaces my previous review. 403 with "
                    + "code `REVIEW_NOT_ALLOWED` without a confirmed order of the product.")
    @ApiResponse(responseCode = "200", description = "Review saved")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "403", description = "No confirmed order of the product (code REVIEW_NOT_ALLOWED)")
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @PutMapping("/api/me/products/{id}/review")
    public ReviewResponse saveMyReview(@PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        return reviewService.save(currentCustomer.id(), id, request);
    }

    @Operation(summary = "Delete my review of a product")
    @ApiResponse(responseCode = "204", description = "Review deleted")
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @DeleteMapping("/api/me/products/{id}/review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMyReview(@PathVariable Long id) {
        reviewService.delete(currentCustomer.id(), id);
    }
}
