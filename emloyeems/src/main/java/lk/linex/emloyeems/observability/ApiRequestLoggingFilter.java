package lk.linex.emloyeems.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestLoggingFilter.class);
    private static final String API_PATH_PREFIX = "/api/";
    private static final String NO_TRACE = "none";

    private final Tracer tracer;

    public ApiRequestLoggingFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(API_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        long startNanos = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            Object routePattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String route = routePattern == null ? request.getRequestURI() : routePattern.toString();
            Span span = tracer.currentSpan();
            String traceId = span == null ? NO_TRACE : span.context().traceId();
            String spanId = span == null ? NO_TRACE : span.context().spanId();

            logRequest(
                    response.getStatus(),
                    request.getMethod(),
                    route,
                    durationMillis,
                    traceId,
                    spanId);
        }
    }

    private void logRequest(
            int status,
            String method,
            String route,
            long durationMillis,
            String traceId,
            String spanId) {

        String message = "http_request method={} route={} status={} duration_ms={} trace_id={} span_id={}";

        if (status >= 500) {
            log.error(message, method, route, status, durationMillis, traceId, spanId);
        } else if (status >= 400) {
            log.warn(message, method, route, status, durationMillis, traceId, spanId);
        } else {
            log.info(message, method, route, status, durationMillis, traceId, spanId);
        }
    }
}
