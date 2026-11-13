package com.xt.interview.service;

import com.xt.interview.dto.AccountDTO;

import java.util.List;
import java.util.Map;

/**
 * 账号（用户）服务接口。
 * 提供 CRUD、登录认证、分页查询（支持按 deleted 字段筛选未删/已删）、还原软删账号。
 * 权限模型：
 *   超管（isSuper=1 且 roleLevel≥3）：全部操作
 *   管理员（roleLevel≥2）：用户管理，不能创建/操作其他超管
 *   普通用户（roleLevel=1）：仅可修改自己的信息
 */
public interface AccountService {

    AccountDTO createAccount(AccountDTO dto);

    AccountDTO updateAccount(Long id, AccountDTO dto);

    void deleteAccount(Long id);

    AccountDTO getAccountById(Long id);

    List<AccountDTO> getAllAccounts();

    AccountDTO login(String username, String password);

    Map<String, Object> getAccountPage(Integer page, Integer size, String username, Integer status, Integer roleLevel, Integer deleted);

    /** 恢复被逻辑删除的账号（仅超级管理员） */
    AccountDTO restoreAccount(Long id);
}
