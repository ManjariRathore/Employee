package lk.linex.emloyeems.service;

import lk.linex.emloyeems.dto.EmployeeDTO;
import lk.linex.emloyeems.repo.EmployeeRepo;
import lk.linex.emloyeems.util.VarList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EmployeeServiceIntegrationTest {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeRepo employeeRepo;

    @BeforeEach
    void clearEmployees() {
        employeeRepo.deleteAll();
    }

    @Test
    void createsAndRejectsDuplicateEmployee() {
        EmployeeDTO employee = employee("Asha", "Pune", "9000000001");

        assertThat(employeeService.saveEmployee(employee)).isEqualTo(VarList.RSP_SUCCESS);
        assertThat(employee.getEmpId()).isPositive();
        assertThat(employeeService.saveEmployee(employee)).isEqualTo(VarList.RSP_DUPLICATED);
    }

    @Test
    void supportsTheCompleteCrudLifecycle() {
        EmployeeDTO employee = employee("Ravi", "Mumbai", "9000000002");
        employeeService.saveEmployee(employee);

        EmployeeDTO found = employeeService.searchEmployee(employee.getEmpId());
        assertThat(found).isNotNull();
        assertThat(found.getEmpName()).isEqualTo("Ravi");

        employee.setEmpAddress("Delhi");
        assertThat(employeeService.updateEmployee(employee)).isEqualTo(VarList.RSP_SUCCESS);
        assertThat(employeeService.searchEmployee(employee.getEmpId()).getEmpAddress()).isEqualTo("Delhi");

        List<EmployeeDTO> employees = employeeService.getAllEmployee();
        assertThat(employees).extracting(EmployeeDTO::getEmpId).contains(employee.getEmpId());

        assertThat(employeeService.deleteEmployee(employee.getEmpId())).isEqualTo(VarList.RSP_SUCCESS);
        assertThat(employeeService.searchEmployee(employee.getEmpId())).isNull();
        assertThat(employeeService.deleteEmployee(employee.getEmpId())).isEqualTo(VarList.RSP_NO_DATA_FOUND);
    }

    @Test
    void rejectsAnUpdateForAnUnknownEmployee() {
        EmployeeDTO employee = new EmployeeDTO(999999, "Unknown", "Nowhere", "9000000003");

        assertThat(employeeService.updateEmployee(employee)).isEqualTo(VarList.RSP_NO_DATA_FOUND);
    }

    private EmployeeDTO employee(String name, String address, String mobileNumber) {
        return new EmployeeDTO(0, name, address, mobileNumber);
    }
}
