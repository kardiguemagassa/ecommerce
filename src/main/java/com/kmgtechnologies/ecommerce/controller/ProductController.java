package com.kmgtechnologies.ecommerce.controller;

import com.kmgtechnologies.ecommerce.dto.ProductRequest;
import com.kmgtechnologies.ecommerce.dto.ProductResponse;
import com.kmgtechnologies.ecommerce.dto.TopSellingProductResponse;
import com.kmgtechnologies.ecommerce.service.ProductService;
import com.kmgtechnologies.ecommerce.service.TopSellingProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.BAD_REQUEST;
import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.CONFLICT;
import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.NOT_FOUND;

@Tag(name = "Products", description = "Product catalog: search, details (cached), management and best sellers")
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Set<String> SORTABLE_PROPERTIES = Set.of("id", "name", "price", "stock");

    private final ProductService productService;
    private final TopSellingProductService topSellingProductService;

    public ProductController(ProductService productService, TopSellingProductService topSellingProductService) {
        this.productService = productService;
        this.topSellingProductService = topSellingProductService;
    }

    @Operation(summary = "Best-selling products",
            description = "Ranked by total quantity ordered. Cached for 5 minutes.")
    @ApiResponse(responseCode = "200", description = "Ranking returned")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @GetMapping("/top-selling")
    public List<TopSellingProductResponse> topSelling(
            @Parameter(description = "Number of products to return (1-50)")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return topSellingProductService.findTopSelling(limit);
    }

    @Operation(summary = "Search products",
            description = "Paginated search, optionally filtered by category and name. "
                    + "Sortable properties: id, name, price, stock. Maximum page size: 100.")
    @ApiResponse(responseCode = "200", description = "Page of products returned")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @GetMapping
    public PagedModel<ProductResponse> search(
            @Parameter(description = "Only products of this category")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Case-insensitive substring of the product name")
            @RequestParam(required = false) String name,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        SortValidation.requireAllowed(pageable.getSort(), SORTABLE_PROPERTIES);
        return new PagedModel<>(productService.search(categoryId, name, pageable));
    }

    @Operation(summary = "Get a product by id", description = "Served from cache when available (10-minute TTL).")
    @ApiResponse(responseCode = "200", description = "Product found")
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable Long id) {
        return productService.findById(id);
    }

    @Operation(summary = "Create a product")
    @ApiResponse(responseCode = "201", description = "Product created; URI in the Location header")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Update a product",
            description = "Evicts the cached product. Returns 409 on concurrent modification (optimistic locking).")
    @ApiResponse(responseCode = "200", description = "Product updated")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = CONFLICT)
    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @Operation(summary = "Delete a product", description = "Evicts the cached product.")
    @ApiResponse(responseCode = "204", description = "Product deleted")
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = CONFLICT)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
