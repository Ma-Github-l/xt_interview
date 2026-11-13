package com.xt.interview.service;

import com.xt.interview.dto.DepartmentDTO;

import java.util.List;
import java.util.Map;

/**
 * 部门服务接口。
 * 提供 CRUD、分页查询（支持按 deleted 字段筛选）、还原软删部门。
 * 写操作（创建/编辑/删除/还原）仅超级管理员可执行。
 */
public interface DepartmentService {

    DepartmentDTO createDepartment(DepartmentDTO dto);

    DepartmentDTO updateDepartment(Long id, DepartmentDTO dto);

    void deleteDepartment(Long id);

    DepartmentDTO getDepartmentById(Long id);

    List<DepartmentDTO> getAllDepartments();

    Map<String, Object> getDepartmentPage(Integer page, Integer size, String name, String code, Integer status, Integer deleted);

    /** 恢复被逻辑删除的部门（仅超级管理员） */
    DepartmentDTO restoreDepartment(Long id);
}
