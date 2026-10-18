package lk.linex.emloyeems.controller;

import lk.linex.emloyeems.dto.EmployeeDTO;
import lk.linex.emloyeems.repo.EmployeeRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class EmployeeApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmployeeRepo employeeRepo;

    @BeforeEach
    void clearEmployees() {
        employeeRepo.deleteAll();
    }

    @Test
    void servesTheAdminConsoleAtTheApplicationRoot() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Employee Admin Console")));
    }

    @Test
    void logsApiRequestDetailsWithoutLoggingThePayload(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/employee/getAllEmployees"))
                .andExpect(status().isAccepted());

        org.assertj.core.api.Assertions.assertThat(output.getOut())
                .contains("http_request method=GET")
                .contains("route=/api/v1/employee/getAllEmployees")
                .contains("status=202")
                .contains("duration_ms=")
                .contains("trace_id=")
                .contains("span_id=")
                .doesNotContain("9000000004");
    }

    @Test
    void completesTheCrudJourneyThroughHttp() throws Exception {
        EmployeeDTO employee = new EmployeeDTO(0, "Meera", "Bengaluru", "9000000004");

        String createResponse = mockMvc.perform(post("/api/v1/employee/saveEmployee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(employee)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.content.empId").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode createdEmployee = objectMapper.readTree(createResponse).path("content");
        int employeeId = createdEmployee.path("empId").asInt();

        mockMvc.perform(get("/api/v1/employee/searchEmployee/{empId}", employeeId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.content.empName").value("Meera"))
                .andExpect(jsonPath("$.content.mNumber").value("9000000004"));

        employee.setEmpId(employeeId);
        employee.setEmpAddress("Chennai");
        mockMvc.perform(put("/api/v1/employee/updateEmployee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(employee)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("00"));

        mockMvc.perform(get("/api/v1/employee/getAllEmployees"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.content[*].empId", hasItem(employeeId)))
                .andExpect(jsonPath("$.content[?(@.empId == " + employeeId + ")].empAddress", hasItem("Chennai")));

        mockMvc.perform(delete("/api/v1/employee/deleteEmployee/{empId}", employeeId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("00"));

        mockMvc.perform(get("/api/v1/employee/searchEmployee/{empId}", employeeId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("01"));
    }
}
