package com.xt.interview.repository;

import com.xt.interview.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 账号 JPA Repository。
 * 支持按 deleted 字段（0=未删，1=已删）过滤；软删时删除行保留 username 原值（展示无感）。
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {
    List<Account> findByDeleted(Integer deleted);
    Optional<Account> findByIdAndDeleted(Long id, Integer deleted);
    Optional<Account> findByUsernameAndDeleted(String username, Integer deleted);
    Optional<Account> findByUsernameAndPasswordAndDeleted(String username, String password, Integer deleted);
    boolean existsByUsernameAndDeleted(String username, Integer deleted);
    /** 按 deleted 值分页（含可选 username 模糊），供"是否删除"下拉框使用 */
    org.springframework.data.domain.Page<Account> findPageByDeleted(Integer deleted, org.springframework.data.domain.Pageable pageable);
    /** 统计未删超级管理员数量（isSuper=1 且 deleted=0） */
    long countByIsSuperAndDeleted(Integer isSuper, Integer deleted);
}
