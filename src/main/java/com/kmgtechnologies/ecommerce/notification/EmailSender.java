package com.kmgtechnologies.ecommerce.notification;

import java.math.BigDecimal;

public interface EmailSender {

    void sendOrderConfirmation(String to, Long orderId, BigDecimal totalAmount);
}