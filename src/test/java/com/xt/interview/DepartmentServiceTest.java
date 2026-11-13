package com.xt.interview;

import com.xt.interview.dto.AccountDTO;
import com.xt.interview.dto.DepartmentDTO;
import com.xt.interview.entity.Department;
import com.xt.interview.repository.DepartmentRepository;
import com.xt.interview.service.impl.DepartmentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DepartmentServiceImpl departmentService;

    @Test
    void testCreateDepartment() {
        DepartmentDTO dto = new DepartmentDTO();
        dto.setName("测试部");
        dto.setCode("TEST");
        dto.setStatus(1);

        when(departmentRepository.existsByCodeAndDeleted("TEST", 0)).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> {
            Department d = inv.getArgument(0);
            d.setId(1L);
            d.setCreateTime(java.time.LocalDateTime.now());
            d.setUpdateTime(java.time.LocalDateTime.now());
            return d;
        });

        DepartmentDTO result = departmentService.createDepartment(dto);
        assertEquals("测试部", result.getName());
        assertEquals("TEST", result.getCode());
    }

    @Test
    void testGetAllDepartments() {
        Department d = new Department();
        d.setId(1L);
        d.setName("研发部");
        d.setCode("DEV");
        d.setStatus(1);
        d.setDeleted(0);

        when(departmentRepository.findByDeleted(0)).thenReturn(List.of(d));

        List<DepartmentDTO> result = departmentService.getAllDepartments();
        assertEquals(1, result.size());
        assertEquals("研发部", result.get(0).getName());
    }
}
