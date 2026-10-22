package com.kmgtechnologies.ecommerce.metrics;

import com.kmgtechnologies.ecommerce.event.OrderPlacedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderMetrics {

    private final Counter ordersPlaced;
    private final DistributionSummary orderAmount;

    public OrderMetrics(MeterRegistry registry) {
        this.ordersPlaced = Counter.builder("ecommerce.orders.placed")
                .description("Number of orders successfully placed")
                .register(registry);
        this.orderAmount = DistributionSummary.builder("ecommerce.orders.amount")
                .description("Total amount of placed orders")
                .register(registry);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        ordersPlaced.increment();
        orderAmount.record(event.totalAmount().doubleValue());
    }
}
