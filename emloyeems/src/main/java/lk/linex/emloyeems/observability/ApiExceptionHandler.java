package lk.linex.emloyeems.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import lk.linex.emloyeems.dto.ResponseDTO;
import lk.linex.emloyeems.util.VarList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;

@RestControllerAdvice
public class ApiExceptionHandler {

    static final String INTERNAL_ERROR_MESSAGE = "An unexpected error occurred";
    static final String INVALID_REQUEST_MESSAGE = "Invalid request";

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String NO_TRACE = "none";

    private final Tracer tracer;

    public ApiExceptionHandler(Tracer tracer) {
        this.tracer = tracer;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ResponseDTO> handleInvalidRequest(
            Exception exception,
            HttpServletRequest request) {

        RequestDetails details = requestDetails(request);
        log.warn(
                "controller_exception method={} route={} status=400 trace_id={} span_id={} exception={}",
                details.method(),
                details.route(),
                details.traceId(),
                details.spanId(),
                exception.getClass().getSimpleName());

        ResponseDTO body = new ResponseDTO(VarList.RSP_FAIL, INVALID_REQUEST_MESSAGE, null);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseDTO> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {

        RequestDetails details = requestDetails(request);

        log.error(
                "controller_exception method={} route={} status=500 trace_id={} span_id={}",
                details.method(),
                details.route(),
                details.traceId(),
                details.spanId(),
                exception);

        ResponseDTO body = new ResponseDTO(VarList.RSP_ERROR, INTERNAL_ERROR_MESSAGE, null);
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private RequestDetails requestDetails(HttpServletRequest request) {
        Object routePattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = routePattern == null ? request.getRequestURI() : routePattern.toString();
        Span span = tracer.currentSpan();
        TraceContext traceContext = span == null ? null : span.context();
        String traceId = traceContext == null ? NO_TRACE : valueOrNone(traceContext.traceId());
        String spanId = traceContext == null ? NO_TRACE : valueOrNone(traceContext.spanId());
        return new RequestDetails(request.getMethod(), route, traceId, spanId);
    }

    private String valueOrNone(String value) {
        return value == null || value.isBlank() ? NO_TRACE : value;
    }

    private record RequestDetails(String method, String route, String traceId, String spanId) {
    }
}
