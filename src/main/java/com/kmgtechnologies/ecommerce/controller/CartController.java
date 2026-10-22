package com.kmgtechnologies.ecommerce.controller;

import com.kmgtechnologies.ecommerce.dto.AddCartItemRequest;
import com.kmgtechnologies.ecommerce.dto.CartResponse;
import com.kmgtechnologies.ecommerce.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.BAD_REQUEST;
import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.NOT_FOUND;

@Tag(name = "Cart", description = "Shopping cart of a customer (one cart per customer)")
@RestController
@RequestMapping("/api/customers/{customerId}/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @Operation(summary = "Get the cart", description = "Lines with subtotals and the cart total.")
    @ApiResponse(responseCode = "200", description = "Cart returned (empty if nothing was added yet)")
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @GetMapping
    public CartResponse getCart(@PathVariable Long customerId) {
        return cartService.getCart(customerId);
    }

    @Operation(summary = "Add a product to the cart",
            description = "Creates the cart on first use. Adding a product already in the cart increases its quantity.")
    @ApiResponse(responseCode = "200", description = "Updated cart")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @PostMapping("/items")
    public CartResponse addItem(@PathVariable Long customerId, @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(customerId, request);
    }

    @Operation(summary = "Remove a product from the cart")
    @ApiResponse(responseCode = "200", description = "Updated cart")
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@PathVariable Long customerId, @PathVariable Long productId) {
        return cartService.removeItem(customerId, productId);
    }

    @Operation(summary = "Empty the cart")
    @ApiResponse(responseCode = "204", description = "Cart emptied")
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable Long customerId) {
        cartService.clear(customerId);
    }
}
