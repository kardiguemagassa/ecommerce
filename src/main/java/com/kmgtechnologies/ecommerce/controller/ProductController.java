package com.kmgtechnologies.ecommerce.controller;

import com.kmgtechnologies.ecommerce.dto.ProductImageOrderRequest;
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
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.BAD_REQUEST;
import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.CONFLICT;
import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.IMAGE_REJECTED;
import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.NOT_FOUND;
import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.STORAGE_UNAVAILABLE;

@Tag(name = "Products", description = "Product catalog: search, details (cached), management, images and best sellers")
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

    @Operation(summary = "Delete a product", description = "Evicts the cached product and deletes its images.")
    @ApiResponse(responseCode = "204", description = "Product deleted")
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = CONFLICT)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }

    @Operation(summary = "Add a view of a product",
            description = """
                    Multipart field `file`: JPEG, PNG or WebP, 2 MB at most (format read from the content). Stored as \
                    a JPEG within 1200 x 900 (whole picture, proportions kept, never enlarged), without the metadata of the \
                    upload (EXIF, GPS). Added after the other views; the first view is the main image. A product shows 4 \
                    views at most: 409 with code `IMAGE_LIMIT` beyond. Returns the product with its `images`.""")
    @ApiResponse(responseCode = "200", description = "View added")
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = CONFLICT)
    @ApiResponse(responseCode = "413", ref = IMAGE_REJECTED)
    @ApiResponse(responseCode = "415", ref = IMAGE_REJECTED)
    @ApiResponse(responseCode = "422", ref = IMAGE_REJECTED)
    @ApiResponse(responseCode = "503", ref = STORAGE_UNAVAILABLE)
    @PostMapping(path = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductResponse addImage(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return productService.addImage(id, file);
    }

    @Operation(summary = "Remove a view of a product",
            description = "The image is deleted from the storage; the next views move up (the second becomes the main "
                    + "image when the first is removed). Returns the product with its `images`.")
    @ApiResponse(responseCode = "200", description = "View removed")
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @DeleteMapping("/{id}/images/{imageId}")
    public ProductResponse removeImage(@PathVariable Long id, @PathVariable Long imageId) {
        return productService.removeImage(id, imageId);
    }

    @Operation(summary = "Change the order of the views of a product",
            description = "Lists the ids of all the views of the product, each once, in their new order; the first "
                    + "becomes the main image. Returns the product with its `images`.")
    @ApiResponse(responseCode = "200", description = "Views reordered")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @PutMapping("/{id}/images/order")
    public ProductResponse reorderImages(@PathVariable Long id, @Valid @RequestBody ProductImageOrderRequest request) {
        return productService.reorderImages(id, request);
    }

    @Operation(summary = "Replace the main image of a product", deprecated = true,
            description = """
                    Kept for clients of 2.0: replaces the first view (adds it when the product has none). Prefer \
                    `POST /api/products/{id}/images`. Same rules for the file; the previous image is deleted.""")
    @ApiResponse(responseCode = "200", description = "Image stored")
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = CONFLICT)
    @ApiResponse(responseCode = "413", ref = IMAGE_REJECTED)
    @ApiResponse(responseCode = "415", ref = IMAGE_REJECTED)
    @ApiResponse(responseCode = "422", ref = IMAGE_REJECTED)
    @ApiResponse(responseCode = "503", ref = STORAGE_UNAVAILABLE)
    @PutMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductResponse replaceImage(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return productService.replaceImage(id, file);
    }

    @Operation(summary = "Remove the main image of a product", deprecated = true,
            description = "Kept for clients of 2.0: removes the first view, the next one becomes the main image. "
                    + "Prefer `DELETE /api/products/{id}/images/{imageId}`.")
    @ApiResponse(responseCode = "204", description = "Image removed (or there was none)")
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @DeleteMapping("/{id}/image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImage(@PathVariable Long id) {
        productService.deleteImage(id);
    }
}
