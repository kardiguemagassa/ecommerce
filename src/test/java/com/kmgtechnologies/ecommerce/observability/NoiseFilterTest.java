package com.kmgtechnologies.ecommerce.observability;

import io.micrometer.observation.Observation;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NoiseFilterTest {

    private final Tracer tracer = mock(Tracer.class);
    private final NoiseFilter filter = new NoiseFilter(provider(tracer));

    @SuppressWarnings("unchecked")
    private static ObjectProvider<Tracer> provider(Tracer available) {
        ObjectProvider<Tracer> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(available);
        return provider;
    }

    /** As datasource-micrometer does with connection spans off: the parent of a query is a no-op observation. */
    private static Observation.Context query() {
        Observation.Context query = new Observation.Context();
        query.setParentObservation(Observation.NOOP);
        return query;
    }

    private static ServerRequestObservationContext request(String path) {
        return new ServerRequestObservationContext(new MockHttpServletRequest("GET", path), new MockHttpServletResponse());
    }

    @Test
    void healthProbesAreDropped() {
        assertThat(filter.test("http.server.requests", request("/actuator/health/readiness"))).isFalse();
    }

    @Test
    void apiRequestsAreKept() {
        assertThat(filter.test("http.server.requests", request("/api/orders"))).isTrue();
        assertThat(filter.test("http.server.requests", new ServerRequestObservationContext(null, null))).isTrue();
    }

    @Test
    void sqlQueryOutsideOfAnyTraceIsDropped() {
        assertThat(filter.test("jdbc.query", query())).isFalse();
        assertThat(filter.test("jdbc.connection", new Observation.Context())).isFalse();
    }

    @Test
    void sqlQueryOfARequestOrOfTheConfirmationStaysInItsTrace() {
        when(tracer.currentSpan()).thenReturn(mock(Span.class));

        assertThat(filter.test("jdbc.query", query())).isTrue();
    }

    @Test
    void sqlQueryUnderAnObservationStaysInItsTrace() {
        Observation.Context query = new Observation.Context();
        query.setParentObservation(mock(Observation.class));

        assertThat(filter.test("jdbc.query", query)).isTrue();
    }

    @Test
    void withoutTracingSqlIsKeptForItsMetrics() {
        assertThat(new NoiseFilter(provider(null)).test("jdbc.query", query())).isTrue();
    }

    @Test
    void anyOtherObservationIsKept() {
        assertThat(filter.test("ecommerce.orders.confirmation", new Observation.Context())).isTrue();
    }
}
