package lk.linex.emloyeems.controller;

import lk.linex.emloyeems.dto.EmployeeDTO;
import lk.linex.emloyeems.dto.ResponseDTO;
import lk.linex.emloyeems.service.EmployeeOperationResult;
import lk.linex.emloyeems.service.EmployeeService;
import lk.linex.emloyeems.util.VarList;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/employee")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping(value = "/saveEmployee")
    public ResponseEntity<ResponseDTO> saveEmployee(@RequestBody EmployeeDTO employeeDTO){
        EmployeeOperationResult result = employeeService.saveEmployee(employeeDTO);
        return switch (result) {
            case SUCCESS -> response(VarList.RSP_SUCCESS, "Success", employeeDTO, HttpStatus.ACCEPTED);
            case DUPLICATE -> response(
                    VarList.RSP_DUPLICATED,
                    "Employee Registered",
                    employeeDTO,
                    HttpStatus.BAD_REQUEST);
            case NOT_FOUND -> response(VarList.RSP_FAIL, "Error", null, HttpStatus.BAD_REQUEST);
        };
    }

    @PutMapping(value = "/updateEmployee")
    public ResponseEntity<ResponseDTO> updateEmployee(@RequestBody EmployeeDTO employeeDTO){
        EmployeeOperationResult result = employeeService.updateEmployee(employeeDTO);
        return switch (result) {
            case SUCCESS -> response(VarList.RSP_SUCCESS, "Success", employeeDTO, HttpStatus.ACCEPTED);
            case NOT_FOUND -> response(
                    VarList.RSP_NO_DATA_FOUND,
                    "NOT A Registered Employee",
                    employeeDTO,
                    HttpStatus.BAD_REQUEST);
            case DUPLICATE -> response(VarList.RSP_FAIL, "Error", null, HttpStatus.BAD_REQUEST);
        };
    }

    @GetMapping("/getAllEmployees")
    public ResponseEntity<ResponseDTO> getAllEmployees(){
        List<EmployeeDTO> employeeDTOList = employeeService.getAllEmployee();
        return response(VarList.RSP_SUCCESS, "Success", employeeDTOList, HttpStatus.ACCEPTED);
    }

    @GetMapping("/searchEmployee/{empId}")
    public ResponseEntity<ResponseDTO> searchEmployee(@PathVariable int empId){
        EmployeeDTO employeeDTO = employeeService.searchEmployee(empId);
        if (employeeDTO != null){
            return response(VarList.RSP_SUCCESS, "Success", employeeDTO, HttpStatus.ACCEPTED);
        }
        return response(
                VarList.RSP_NO_DATA_FOUND,
                "No Employee Available For this empId",
                null,
                HttpStatus.BAD_REQUEST);
    }

    @DeleteMapping("/deleteEmployee/{empId}")
    public ResponseEntity<ResponseDTO> deleteEmployee(@PathVariable int empId){
        EmployeeOperationResult result = employeeService.deleteEmployee(empId);
        return switch (result) {
            case SUCCESS -> response(VarList.RSP_SUCCESS, "Success", null, HttpStatus.ACCEPTED);
            case NOT_FOUND -> response(
                    VarList.RSP_NO_DATA_FOUND,
                    "No Employee Available For this empId",
                    null,
                    HttpStatus.BAD_REQUEST);
            case DUPLICATE -> response(VarList.RSP_FAIL, "Error", null, HttpStatus.BAD_REQUEST);
        };
    }

    private ResponseEntity<ResponseDTO> response(String code, String message, Object content, HttpStatus status) {
        return new ResponseEntity<>(new ResponseDTO(code, message, content), status);
    }
}
