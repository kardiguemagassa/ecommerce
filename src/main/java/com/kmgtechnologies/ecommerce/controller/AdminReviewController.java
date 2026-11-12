package com.kmgtechnologies.ecommerce.controller;

import com.kmgtechnologies.ecommerce.dto.AdminReviewResponse;
import com.kmgtechnologies.ecommerce.dto.ReviewVisibilityRequest;
import com.kmgtechnologies.ecommerce.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.BAD_REQUEST;
import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.NOT_FOUND;

@Tag(name = "Reviews")
@RestController
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {

    private final ReviewService reviewService;

    public AdminReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "Reviews to moderate", description = "Newest first, with their product and author; 20 a page.")
    @ApiResponse(responseCode = "200", description = "Page of reviews returned")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @GetMapping
    public PagedModel<AdminReviewResponse> moderatedReviews(
            @Parameter(description = "true: the hidden ones only; false: the visible ones only; absent: all")
            @RequestParam(required = false) Boolean hidden,
            @Parameter(description = "Page number, from 0") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1-50)") @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return new PagedModel<>(reviewService.moderation(hidden, page, size));
    }

    @Operation(summary = "Hide a review or show it again",
            description = "A hidden review leaves the shop and the rating of its product; its author still sees it.")
    @ApiResponse(responseCode = "200", description = "Visibility changed")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @PutMapping("/{id}/visibility")
    public AdminReviewResponse setReviewVisibility(@PathVariable Long id, @Valid @RequestBody ReviewVisibilityRequest request) {
        return reviewService.setHidden(id, request.hidden());
    }
}
