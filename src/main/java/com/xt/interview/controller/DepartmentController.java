package com.xt.interview.controller;

import com.xt.interview.dto.DepartmentDTO;
import com.xt.interview.service.DepartmentService;
import com.xt.interview.util.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 部门管理 REST 接口（/api/departments）。
 * 写操作（创建/编辑/删除/还原）仅超级管理员（isSuper=1 且 roleLevel≥3）可执行。
 * 普通用户和管理员仅可读取（GET 端点不需要 token，由 JwtAuthenticationFilter 放行 GET 头像以外）。
 */
@RestController
@RequestMapping("/api/departments")
@CrossOrigin(origins = "*")
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    @GetMapping
    public ResponseEntity<List<DepartmentDTO>> getAll() {
        return ResponseEntity.ok(departmentService.getAllDepartments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepartmentDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(departmentService.getDepartmentById(id));
    }

    @GetMapping("/page")
    public ResponseEntity<Map<String, Object>> getPage(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer deleted) {
        return ResponseEntity.ok(departmentService.getDepartmentPage(page, size, name, code, status, deleted));
    }

    @PostMapping
    public ResponseEntity<DepartmentDTO> create(@RequestBody DepartmentDTO dto, HttpServletRequest req) {
        // 部门写操作：仅超级管理员
        AuthContext.requireCanManageEverything(req);
        return ResponseEntity.ok(departmentService.createDepartment(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DepartmentDTO> update(@PathVariable Long id, @RequestBody DepartmentDTO dto, HttpServletRequest req) {
        AuthContext.requireCanManageEverything(req);
        return ResponseEntity.ok(departmentService.updateDepartment(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest req) {
        AuthContext.requireCanManageEverything(req);
        departmentService.deleteDepartment(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<DepartmentDTO> restore(@PathVariable Long id, HttpServletRequest req) {
        // 恢复部门：仅超级管理员
        AuthContext.requireCanManageEverything(req);
        return ResponseEntity.ok(departmentService.restoreDepartment(id));
    }
}
