package com.xt.interview.service.impl;

import com.xt.interview.dto.DepartmentDTO;
import com.xt.interview.entity.Department;
import com.xt.interview.repository.DepartmentRepository;
import com.xt.interview.service.DepartmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 部门服务实现。
 * 核心逻辑：
 * - 创建/更新时做部门编码重复检查（仅检查未删行，软删行不影响）
 * - 软删：置 deleted=1 + status=0，保留 code 原值（展示无感）
 * - 还原：置 deleted=0 + status=0（默认禁用）；检查同 code 未删部门是否冲突
 * - 分页查询：支持按 deleted（null=仅未删，0=未删，1=已删）、name、code、status 组合筛选
 * - 写操作（创建/编辑/删除/还原）由 Controller 层通过 AuthContext.requireCanManageEverything 鉴权，
 *   仅超级管理员（isSuper=1 且 roleLevel≥3）可执行
 */
@Service
@Transactional
public class DepartmentServiceImpl implements DepartmentService {

    private static final Integer NOT_DELETED = 0;
    private static final Integer DELETED = 1;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Override
    public DepartmentDTO createDepartment(DepartmentDTO dto) {
        if (dto.getCode() != null &&
            departmentRepository.existsByCodeAndDeleted(dto.getCode(), NOT_DELETED)) {
            throw new RuntimeException("部门编码已存在");
        }
        // 同 code 未删部门冲突（活名槽机制：软删行的 code_live 已带后缀，不占原 code）
        // 命中"未删"才是真正冲突；命中"仅软删"说明可安全重建同 code 部门。
        Department department = new Department();
        department.setName(dto.getName());
        department.setCode(dto.getCode());
        department.setDescription(dto.getDescription());
        department.setStatus(dto.getStatus());
        department.setDeleted(NOT_DELETED);
        department = departmentRepository.save(department);
        return convertToDTO(department);
    }

    @Override
    public DepartmentDTO updateDepartment(Long id, DepartmentDTO dto) {
        Department department = departmentRepository.findByIdAndDeleted(id, NOT_DELETED)
                .orElseThrow(() -> new RuntimeException("部门不存在"));

        if (!department.getCode().equals(dto.getCode()) &&
            departmentRepository.existsByCodeAndDeleted(dto.getCode(), NOT_DELETED)) {
            throw new RuntimeException("部门编码已存在");
        }

        department.setName(dto.getName());
        department.setCode(dto.getCode());
        department.setDescription(dto.getDescription());
        department.setStatus(dto.getStatus());
        department = departmentRepository.save(department);
        return convertToDTO(department);
    }

    @Override
    public void deleteDepartment(Long id) {
        Department department = departmentRepository.findByIdAndDeleted(id, NOT_DELETED)
                .orElseThrow(() -> new RuntimeException("部门不存在"));
        department.setDeleted(DELETED);
        // 软删：置 deleted=1，状态默认禁用，code 保留原值（展示无感）。
        department.setStatus(0);
        departmentRepository.save(department);
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentDTO getDepartmentById(Long id) {
        Department department = departmentRepository.findByIdAndDeleted(id, NOT_DELETED)
                .orElseThrow(() -> new RuntimeException("部门不存在"));
        return convertToDTO(department);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentDTO> getAllDepartments() {
        return departmentRepository.findByDeleted(NOT_DELETED).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getDepartmentPage(Integer page, Integer size, String name, String code, Integer status, Integer deleted) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 10 : Math.max(1, size);

        // deleted 过滤：null=仅未删（兼容旧行为），0=未删，1=已删
        Integer deletedFilter = deleted == null ? NOT_DELETED : deleted;
        Specification<Department> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("deleted"), deletedFilter));
            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(root.get("name"), "%" + name.trim() + "%"));
            }
            if (code != null && !code.isBlank()) {
                predicates.add(cb.like(root.get("code"), "%" + code.trim() + "%"));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Department> result = departmentRepository.findAll(spec, PageRequest.of(p, s, Sort.by("id").ascending()));

        List<DepartmentDTO> list = result.getContent().stream().map(this::convertToDTO).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("list", list);
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        response.put("currentPage", result.getNumber());
        response.put("pageSize", result.getSize());
        return response;
    }

    @Override
    public DepartmentDTO restoreDepartment(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("部门不存在"));
        if (!DELETED.equals(department.getDeleted())) {
            throw new RuntimeException("该部门未被删除，无需恢复");
        }
        // 还原：仅置 deleted=0，code 保留原值（展示无感）。
        // 唯一性检查改看 code_live：若"同 code 且未删"的部门已存在，
        // 则被还原行（live=原code）会撞 uk_department_code_live，提前拦下并给中文提示。
        if (departmentRepository.existsByCodeAndDeleted(department.getCode(), NOT_DELETED)) {
            throw new RuntimeException("部门编码 " + department.getCode() + " 已被其他部门占用，无法恢复，请联系超管处理");
        }
        department.setDeleted(NOT_DELETED);
        department.setStatus(0); // 恢复后默认禁用，需管理员手动启用
        department = departmentRepository.save(department);
        return convertToDTO(department);
    }

    private DepartmentDTO convertToDTO(Department department) {
        DepartmentDTO dto = new DepartmentDTO();
        dto.setId(department.getId());
        dto.setName(department.getName());
        dto.setCode(department.getCode());
        dto.setDescription(department.getDescription());
        dto.setStatus(department.getStatus());
        dto.setDeleted(department.getDeleted());
        dto.setCreateTime(department.getCreateTime());
        dto.setUpdateTime(department.getUpdateTime());
        return dto;
    }
}
