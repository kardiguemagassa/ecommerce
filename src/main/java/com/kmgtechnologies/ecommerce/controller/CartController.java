package com.kmgtechnologies.ecommerce.controller;

import com.kmgtechnologies.ecommerce.dto.AddCartItemRequest;
import com.kmgtechnologies.ecommerce.dto.CartResponse;
import com.kmgtechnologies.ecommerce.dto.UpdateCartItemRequest;
import com.kmgtechnologies.ecommerce.security.CurrentCustomer;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.BAD_REQUEST;
import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.CONFLICT;
import static com.kmgtechnologies.ecommerce.config.OpenApiResponses.NOT_FOUND;

@Tag(name = "Cart", description = "Cart of the signed-in customer (one cart per customer)")
@RestController
@RequestMapping("/api/me/cart")
public class CartController {

    private final CartService cartService;
    private final CurrentCustomer currentCustomer;

    public CartController(CartService cartService, CurrentCustomer currentCustomer) {
        this.cartService = cartService;
        this.currentCustomer = currentCustomer;
    }

    @Operation(summary = "Get the cart", description = "Lines with subtotals and the cart total.")
    @ApiResponse(responseCode = "200", description = "Cart returned (empty if nothing was added yet)")
    @GetMapping
    public CartResponse getCart() {
        return cartService.getCart(currentCustomer.id());
    }

    @Operation(summary = "Add a product to the cart",
            description = "Creates the cart on first use. Adding a product already in the cart increases its quantity.")
    @ApiResponse(responseCode = "200", description = "Updated cart")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = CONFLICT)
    @PostMapping("/items")
    public CartResponse addItem(@Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(currentCustomer.id(), request);
    }

    @Operation(summary = "Change the quantity of a cart line",
            description = "Sets the quantity (not added to it). The stock is checked against the new quantity.")
    @ApiResponse(responseCode = "200", description = "Updated cart")
    @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = CONFLICT)
    @PutMapping("/items/{productId}")
    public CartResponse changeQuantity(@PathVariable Long productId,
                                       @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.changeQuantity(currentCustomer.id(), productId, request);
    }

    @Operation(summary = "Remove a product from the cart")
    @ApiResponse(responseCode = "200", description = "Updated cart")
    @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@PathVariable Long productId) {
        return cartService.removeItem(currentCustomer.id(), productId);
    }

    @Operation(summary = "Empty the cart")
    @ApiResponse(responseCode = "204", description = "Cart emptied")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear() {
        cartService.clear(currentCustomer.id());
    }
}
