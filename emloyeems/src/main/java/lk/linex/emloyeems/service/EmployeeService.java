package lk.linex.emloyeems.service;

import jakarta.transaction.Transactional;
import lk.linex.emloyeems.dto.EmployeeDTO;
import lk.linex.emloyeems.entity.Employee;
import lk.linex.emloyeems.repo.EmployeeRepo;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class EmployeeService {
    private final EmployeeRepo employeeRepo;
    private final ModelMapper modelMapper;

    public EmployeeService(EmployeeRepo employeeRepo, ModelMapper modelMapper) {
        this.employeeRepo = employeeRepo;
        this.modelMapper = modelMapper;
    }

    public EmployeeOperationResult saveEmployee(EmployeeDTO employeeDTO){
        if (employeeRepo.existsById(employeeDTO.getEmpId())){
            return EmployeeOperationResult.DUPLICATE;
        }else {
            Employee savedEmployee = employeeRepo.save(modelMapper.map(employeeDTO, Employee.class));
            employeeDTO.setEmpId(savedEmployee.getEmpId());
            return EmployeeOperationResult.SUCCESS;
        }
    }

    public EmployeeOperationResult updateEmployee(EmployeeDTO employeeDTO){
        if (employeeRepo.existsById(employeeDTO.getEmpId())){
            employeeRepo.save(modelMapper.map(employeeDTO, Employee.class));
            return EmployeeOperationResult.SUCCESS;
        }else {
            return EmployeeOperationResult.NOT_FOUND;
        }
    }

    public List<EmployeeDTO> getAllEmployee(){
        List<Employee> employeeList =  employeeRepo.findAll();
        return modelMapper.map(employeeList, new TypeToken<ArrayList<EmployeeDTO>>(){}.getType());
    }

    public EmployeeDTO searchEmployee(int empId){
        if(employeeRepo.existsById(empId)){
            Employee employee = employeeRepo.findById(empId).orElse(null);
            return modelMapper.map(employee, EmployeeDTO.class);
        }else{
            return null;
        }
    }
    public EmployeeOperationResult deleteEmployee(int empId){
        if(employeeRepo.existsById(empId)){
            employeeRepo.deleteById(empId);
            return EmployeeOperationResult.SUCCESS;
        }else{
            return EmployeeOperationResult.NOT_FOUND;
        }
    }
}
