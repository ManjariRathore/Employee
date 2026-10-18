package lk.linex.emloyeems.controller;

import lk.linex.emloyeems.dto.EmployeeDTO;
import lk.linex.emloyeems.dto.ResponseDTO;
import lk.linex.emloyeems.service.EmployeeService;
import lk.linex.emloyeems.util.VarList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/employee")
public class EmployeeController {

    private static final Logger log = LoggerFactory.getLogger(EmployeeController.class);

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping(value = "/saveEmployee")
    public ResponseEntity<ResponseDTO> saveEmployee(@RequestBody EmployeeDTO employeeDTO){
            try {
                String res = employeeService.saveEmployee(employeeDTO);
                if (res.equals("00")){
                    return response(VarList.RSP_SUCCESS, "Success", employeeDTO, HttpStatus.ACCEPTED);
                }else if(res.equals("06")){
                    return response(VarList.RSP_DUPLICATED, "Employee Registered", employeeDTO, HttpStatus.BAD_REQUEST);
                }else{
                    return response(VarList.RSP_FAIL, "Error", null, HttpStatus.BAD_REQUEST);
                }
            }catch (Exception e){
                log.error("Failed to create employee", e);
                return response(VarList.RSP_ERROR, e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
            }
    }

    @PutMapping(value = "/updateEmployee")
    public ResponseEntity<ResponseDTO> updateEmployee(@RequestBody EmployeeDTO employeeDTO){
        try {
            String res = employeeService.updateEmployee(employeeDTO);
            if (res.equals("00")){
                return response(VarList.RSP_SUCCESS, "Success", employeeDTO, HttpStatus.ACCEPTED);
            }else if(res.equals("01")){
                return response(VarList.RSP_NO_DATA_FOUND, "NOT A Registered Employee", employeeDTO, HttpStatus.BAD_REQUEST);
            }else{
                return response(VarList.RSP_FAIL, "Error", null, HttpStatus.BAD_REQUEST);
            }
        }catch (Exception e){
            log.error("Failed to update employee id={}", employeeDTO.getEmpId(), e);
            return response(VarList.RSP_ERROR, e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getAllEmployees")
    public ResponseEntity<ResponseDTO> getAllEmployees(){
        try {
            List<EmployeeDTO> employeeDTOList = employeeService.getAllEmployee();
            return response(VarList.RSP_SUCCESS, "Success", employeeDTOList, HttpStatus.ACCEPTED);
        }catch (Exception e){
            log.error("Failed to list employees", e);
            return response(VarList.RSP_ERROR, e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/searchEmployee/{empId}")
    public ResponseEntity<ResponseDTO> searchEmployee(@PathVariable int empId){
        try {
            EmployeeDTO employeeDTO = employeeService.searchEmployee(empId);
            if (employeeDTO != null){
                return response(VarList.RSP_SUCCESS, "Success", employeeDTO, HttpStatus.ACCEPTED);
            }else{
                return response(VarList.RSP_NO_DATA_FOUND, "No Employee Available For this empId", null, HttpStatus.BAD_REQUEST);
            }
        }catch (Exception e){
            log.error("Failed to find employee id={}", empId, e);
            return response(VarList.RSP_ERROR, e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/deleteEmployee/{empId}")
    public ResponseEntity<ResponseDTO> deleteEmployee(@PathVariable int empId){
        try {
            String res = employeeService.deleteEmployee(empId);
            if (res.equals("00")){
                return response(VarList.RSP_SUCCESS, "Success", null, HttpStatus.ACCEPTED);
            }else{
                return response(VarList.RSP_NO_DATA_FOUND, "No Employee Available For this empId", null, HttpStatus.BAD_REQUEST);
            }
        }catch (Exception e){
            log.error("Failed to delete employee id={}", empId, e);
            return response(VarList.RSP_ERROR, e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ResponseEntity<ResponseDTO> response(String code, String message, Object content, HttpStatus status) {
        return new ResponseEntity<>(new ResponseDTO(code, message, content), status);
    }
}
