package com.kmgtechnologies.ecommerce.notification;

import com.kmgtechnologies.ecommerce.event.OrderPlacedEvent;
import com.kmgtechnologies.ecommerce.service.OrderService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderConfirmationListener {

    /**
     * One observation, two signals: a timer (duration per outcome, for the dashboard) and a span, child of the
     * request that placed the order (context propagated to the {@code @Async} thread).
     */
    static final String CONFIRMATION_OBSERVATION = "ecommerce.orders.confirmation";

    private final EmailSender emailSender;
    private final OrderService orderService;
    private final ObservationRegistry observationRegistry;

    public OrderConfirmationListener(EmailSender emailSender, OrderService orderService,
                                     ObservationRegistry observationRegistry) {
        this.emailSender = emailSender;
        this.orderService = orderService;
        this.observationRegistry = observationRegistry;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        Observation observation = Observation.createNotStarted(CONFIRMATION_OBSERVATION, observationRegistry)
                .contextualName("order confirmation")
                .highCardinalityKeyValue("order.id", String.valueOf(event.orderId()))
                .start();
        String outcome = "success";
        try (Observation.Scope ignored = observation.openScope()) {
            emailSender.sendOrderConfirmation(event.customerEmail(), event.orderId(), event.totalAmount());
            orderService.markAsConfirmed(event.orderId());
        } catch (RuntimeException ex) {
            outcome = "failure";
            observation.error(ex);
            throw ex;
        } finally {
            observation.lowCardinalityKeyValue("outcome", outcome);
            observation.stop();
        }
    }
}
