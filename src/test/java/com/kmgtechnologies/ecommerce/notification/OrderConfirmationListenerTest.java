package com.kmgtechnologies.ecommerce.notification;

import com.kmgtechnologies.ecommerce.event.OrderPlacedEvent;
import com.kmgtechnologies.ecommerce.service.OrderService;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderConfirmationListenerTest {

    private static final OrderPlacedEvent EVENT =
            new OrderPlacedEvent(5L, "alice@example.com", new BigDecimal("999.99"), List.of(10L));

    @Mock
    private EmailSender emailSender;

    @Mock
    private OrderService orderService;

    private SimpleMeterRegistry meterRegistry;
    private OrderConfirmationListener listener;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        // As in the application: observations recorded as timers
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig().observationHandler(new DefaultMeterObservationHandler(meterRegistry));
        listener = new OrderConfirmationListener(emailSender, orderService, observationRegistry);
    }

    @Test
    void onOrderPlaced_sendsEmailThenConfirmsOrder_andRecordsSuccess() {
        listener.onOrderPlaced(EVENT);

        InOrder inOrder = inOrder(emailSender, orderService);
        inOrder.verify(emailSender).sendOrderConfirmation("alice@example.com", 5L, new BigDecimal("999.99"));
        inOrder.verify(orderService).markAsConfirmed(5L);
        assertThat(meterRegistry.get(OrderConfirmationListener.CONFIRMATION_OBSERVATION)
                .tag("outcome", "success").timer().count()).isEqualTo(1);
    }

    @Test
    void onOrderPlaced_whenEmailFails_doesNotConfirmOrder_andRecordsFailure() {
        doThrow(new IllegalStateException("SMTP down"))
                .when(emailSender).sendOrderConfirmation(any(), any(), any());

        assertThatThrownBy(() -> listener.onOrderPlaced(EVENT))
                .isInstanceOf(IllegalStateException.class);

        verify(orderService, never()).markAsConfirmed(any());
        assertThat(meterRegistry.get(OrderConfirmationListener.CONFIRMATION_OBSERVATION)
                .tag("outcome", "failure").timer().count()).isEqualTo(1);
    }
}
