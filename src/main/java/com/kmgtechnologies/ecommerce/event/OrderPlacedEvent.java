package com.kmgtechnologies.ecommerce.event;

import java.math.BigDecimal;

public record OrderPlacedEvent(Long orderId, String customerEmail, BigDecimal totalAmount) {
}