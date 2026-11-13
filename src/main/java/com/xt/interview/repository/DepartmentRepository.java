package com.xt.interview.repository;

import com.xt.interview.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 部门 JPA Repository。
 * 支持按 deleted 字段（0=未删，1=已删）过滤；软删时保留 code 原值（展示无感）。
 */
@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long>, JpaSpecificationExecutor<Department> {
    List<Department> findByDeleted(Integer deleted);
    Optional<Department> findByIdAndDeleted(Long id, Integer deleted);
    boolean existsByCodeAndDeleted(String code, Integer deleted);
}
