package com.kmgtechnologies.ecommerce.metrics;

import com.kmgtechnologies.ecommerce.event.OrderPlacedEvent;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMetricsTest {

    private SimpleMeterRegistry registry;
    private OrderMetrics orderMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        orderMetrics = new OrderMetrics(registry);
    }

    @Test
    void metersAreRegisteredAtZero_beforeAnyOrder() {
        assertThat(registry.get("ecommerce.orders.placed").counter().count()).isZero();
        assertThat(registry.get("ecommerce.orders.amount").summary().count()).isZero();
    }

    @Test
    void onOrderPlaced_incrementsCounter_andRecordsAmounts() {
        orderMetrics.onOrderPlaced(new OrderPlacedEvent(1L, "a@example.com", new BigDecimal("100.00"), List.of(1L)));
        orderMetrics.onOrderPlaced(new OrderPlacedEvent(2L, "b@example.com", new BigDecimal("50.50"), List.of(2L)));

        assertThat(registry.get("ecommerce.orders.placed").counter().count()).isEqualTo(2);
        DistributionSummary amount = registry.get("ecommerce.orders.amount").summary();
        assertThat(amount.count()).isEqualTo(2);
        assertThat(amount.totalAmount()).isEqualTo(150.50);
        assertThat(amount.max()).isEqualTo(100.00);
    }
}
