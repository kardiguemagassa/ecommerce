package com.kmgtechnologies.ecommerce.observability;

import io.micrometer.observation.ObservationPredicate;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

/**
 * OpenTelemetry setup on top of Spring Boot's: export and sampling are standard properties
 * ({@code management.opentelemetry.*}, {@code management.otlp.metrics.export.*},
 * {@code management.tracing.sampling.probability}), see application.yml.
 */
@Configuration(proxyBeanMethods = false)
public class ObservabilityConfig {

    @Bean
    ObservationPredicate noiseFilter(ObjectProvider<Tracer> tracer) {
        return new NoiseFilter(tracer);
    }

    @Bean
    OpenTelemetryLogsInstaller openTelemetryLogsInstaller(ObjectProvider<OpenTelemetry> openTelemetry) {
        return new OpenTelemetryLogsInstaller(openTelemetry);
    }

    /**
     * Applied by Spring Boot to the executor behind {@code @Async}: the order confirmation runs in the trace of the
     * request that placed the order, and its logs carry the same trace id.
     */
    @Bean
    ContextPropagatingTaskDecorator contextPropagatingTaskDecorator() {
        return new ContextPropagatingTaskDecorator();
    }
}
