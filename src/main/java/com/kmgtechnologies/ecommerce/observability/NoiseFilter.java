package com.kmgtechnologies.ecommerce.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationView;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Keeps traces about the business: drops what runs every few seconds and would bury the useful traces
 * (sampling is 100 % in development).
 * <ul>
 *   <li>Actuator requests: health probes of Docker every 5 seconds.</li>
 *   <li>SQL queries outside of any trace: Flyway at startup, the connection pool, health checks. A query made while
 *       handling a request, or during the asynchronous order confirmation, runs inside that trace and stays in it.</li>
 * </ul>
 */
final class NoiseFilter implements ObservationPredicate {

    static final String ACTUATOR_PATH = "/actuator";
    /** Observations of datasource-micrometer ({@code jdbc.query}, {@code jdbc.connection}...). */
    static final String JDBC_PREFIX = "jdbc.";

    private final ObjectProvider<Tracer> tracer;

    NoiseFilter(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    @Override
    public boolean test(String name, Observation.Context context) {
        if (context instanceof ServerRequestObservationContext server
                && server.getCarrier() != null
                && server.getCarrier().getRequestURI().startsWith(ACTUATOR_PATH)) {
            return false;
        }
        return !(name.startsWith(JDBC_PREFIX) && outsideOfAnyTrace(context));
    }

    /**
     * datasource-micrometer gives a query the observation of its connection as parent, a no-op one when connection
     * spans are off: the span of the query then joins the current span of the thread. Without a current span, it would
     * start a trace of its own. Without a tracer, nothing is filtered: the JDBC metrics stay.
     */
    private boolean outsideOfAnyTrace(Observation.Context context) {
        ObservationView parent = context.getParentObservation();
        if (parent instanceof Observation observation && !observation.isNoop()) {
            return false;
        }
        Tracer current = tracer.getIfAvailable();
        return current != null && current.currentSpan() == null;
    }
}
