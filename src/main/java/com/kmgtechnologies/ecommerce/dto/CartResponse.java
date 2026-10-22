package com.kmgtechnologies.ecommerce.dto;

import com.kmgtechnologies.ecommerce.entity.Cart;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        Long customerId,
        List<CartItemResponse> items,
        int totalQuantity,
        BigDecimal total
) {

    public static CartResponse from(Long customerId, Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(CartItemResponse::from)
                .toList();
        int totalQuantity = items.stream().mapToInt(CartItemResponse::quantity).sum();
        return new CartResponse(customerId, items, totalQuantity, cart.getTotal());
    }

    public static CartResponse empty(Long customerId) {
        return new CartResponse(customerId, List.of(), 0, BigDecimal.ZERO);
    }
}