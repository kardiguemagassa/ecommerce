package com.kmgtechnologies.ecommerce.controller;

import com.kmgtechnologies.ecommerce.dto.AddCartItemRequest;
import com.kmgtechnologies.ecommerce.dto.CartResponse;
import com.kmgtechnologies.ecommerce.service.CartService;
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

@RestController
@RequestMapping("/api/customers/{customerId}/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse getCart(@PathVariable Long customerId) {
        return cartService.getCart(customerId);
    }

    @PostMapping("/items")
    public CartResponse addItem(@PathVariable Long customerId, @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(customerId, request);
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@PathVariable Long customerId, @PathVariable Long productId) {
        return cartService.removeItem(customerId, productId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable Long customerId) {
        cartService.clear(customerId);
    }
}