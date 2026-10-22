package com.kmgtechnologies.ecommerce.notification;

import com.kmgtechnologies.ecommerce.event.OrderPlacedEvent;
import com.kmgtechnologies.ecommerce.service.OrderService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderConfirmationListener {

    private final EmailSender emailSender;
    private final OrderService orderService;

    public OrderConfirmationListener(EmailSender emailSender, OrderService orderService) {
        this.emailSender = emailSender;
        this.orderService = orderService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        emailSender.sendOrderConfirmation(event.customerEmail(), event.orderId(), event.totalAmount());
        orderService.markAsConfirmed(event.orderId());
    }
}