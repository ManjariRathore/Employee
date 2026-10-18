package lk.linex.emloyeems.observability;

import io.micrometer.tracing.Tracer;
import lk.linex.emloyeems.dto.ResponseDTO;
import lk.linex.emloyeems.util.VarList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionHandlerTest {

    @Test
    void logsTraceContextAndHidesInternalExceptionDetails(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/v1/employee/getAllEmployees");
        request.setAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/api/v1/employee/getAllEmployees");

        ApiExceptionHandler handler = new ApiExceptionHandler(Tracer.NOOP);
        ResponseEntity<ResponseDTO> response = handler.handleUnexpectedException(
                new IllegalStateException("internal database connection details"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(VarList.RSP_ERROR);
        assertThat(response.getBody().getMessage())
                .isEqualTo(ApiExceptionHandler.INTERNAL_ERROR_MESSAGE)
                .doesNotContain("database");

        assertThat(output.getOut())
                .contains("controller_exception method=GET")
                .contains("route=/api/v1/employee/getAllEmployees")
                .contains("status=500")
                .contains("trace_id=none")
                .contains("span_id=none");
    }
}
