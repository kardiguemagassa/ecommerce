package com.kmgtechnologies.ecommerce.notification;

import com.kmgtechnologies.ecommerce.event.OrderPlacedEvent;
import com.kmgtechnologies.ecommerce.service.OrderService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderConfirmationListener {

    static final String CONFIRMATION_TIMER = "ecommerce.orders.confirmation";

    private final EmailSender emailSender;
    private final OrderService orderService;
    private final MeterRegistry meterRegistry;

    public OrderConfirmationListener(EmailSender emailSender, OrderService orderService, MeterRegistry meterRegistry) {
        this.emailSender = emailSender;
        this.orderService = orderService;
        this.meterRegistry = meterRegistry;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            emailSender.sendOrderConfirmation(event.customerEmail(), event.orderId(), event.totalAmount());
            orderService.markAsConfirmed(event.orderId());
        } catch (RuntimeException ex) {
            outcome = "failure";
            throw ex;
        } finally {
            sample.stop(Timer.builder(CONFIRMATION_TIMER)
                    .description("Duration of asynchronous order confirmation")
                    .tag("outcome", outcome)
                    .register(meterRegistry));
        }
    }
}
