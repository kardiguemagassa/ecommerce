package com.kmgtechnologies.ecommerce.dto;

import com.kmgtechnologies.ecommerce.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryResponse(
        Long id,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt
) {
}