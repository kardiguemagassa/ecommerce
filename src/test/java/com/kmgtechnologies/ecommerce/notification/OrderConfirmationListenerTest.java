package com.kmgtechnologies.ecommerce.notification;

import com.kmgtechnologies.ecommerce.event.OrderPlacedEvent;
import com.kmgtechnologies.ecommerce.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class OrderConfirmationListenerTest {

    @Mock
    private EmailSender emailSender;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderConfirmationListener listener;

    @Test
    void onOrderPlaced_sendsEmailThenConfirmsOrder() {
        listener.onOrderPlaced(new OrderPlacedEvent(5L, "alice@example.com", new BigDecimal("999.99")));

        InOrder inOrder = inOrder(emailSender, orderService);
        inOrder.verify(emailSender).sendOrderConfirmation("alice@example.com", 5L, new BigDecimal("999.99"));
        inOrder.verify(orderService).markAsConfirmed(5L);
    }
}