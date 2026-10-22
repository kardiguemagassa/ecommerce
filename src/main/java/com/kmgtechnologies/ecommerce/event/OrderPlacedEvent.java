package com.kmgtechnologies.ecommerce.event;

import java.math.BigDecimal;
import java.util.List;

public record OrderPlacedEvent(Long orderId, String customerEmail, BigDecimal totalAmount, List<Long> productIds) {
}