package com.xt.interview.service.impl;

import com.xt.interview.dto.AccountDTO;
import com.xt.interview.entity.Account;
import com.xt.interview.entity.Department;
import com.xt.interview.repository.AccountRepository;
import com.xt.interview.repository.DepartmentRepository;
import com.xt.interview.service.AccountService;
import com.xt.interview.util.PasswordUtil;
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
 * 账号服务实现。
 * 核心逻辑：
 * - 创建/更新时做用户名重复检查（仅检查未删行，软删行不影响）
 * - 软删：置 deleted=1 + status=0，保留 username 原值（展示无感）
 * - 还原：置 deleted=0 + status=0（默认禁用，需手动启用）；检查同名未删行是否冲突
 * - 分页查询：支持按 deleted（null=仅未删，0=未删，1=已删）、username、status、roleLevel 组合筛选
 * - 密码统一 Argon2id 编码；存量 MD5 登录时自动升级并回写
 */
@Service
@Transactional
public class AccountServiceImpl implements AccountService {

    private static final Integer NOT_DELETED = 0;
    private static final Integer DELETED = 1;

    private static final String DEFAULT_PASSWORD = "123456";

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Override
    public AccountDTO createAccount(AccountDTO dto) {
        if (dto.getUsername() == null || dto.getUsername().isBlank()) {
            throw new RuntimeException("用户名不能为空");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new RuntimeException("姓名不能为空");
        }
        String username = dto.getUsername().trim();
        if (username.length() > 50) {
            throw new RuntimeException("用户名过长（最多50个字符）");
        }
        if (dto.getName().trim().length() > 100) {
            throw new RuntimeException("姓名过长（最多100个字符）");
        }
        // 同名未删账号冲突（活名槽机制：软删行的 username_live 已带后缀，不占原 username）
        // 命中"未删"才是真正冲突；命中"仅软删"说明可安全重建同名账号。
        if (accountRepository.existsByUsernameAndDeleted(dto.getUsername(), NOT_DELETED)) {
            throw new RuntimeException("用户名已存在");
        }
        Account account = new Account();
        account.setUsername(username);
        account.setName(dto.getName().trim());
        account.setGender(dto.getGender() == null ? null : dto.getGender().trim());
        account.setPhone(dto.getPhone() == null ? null : dto.getPhone().trim());
        String password = (dto.getPassword() == null || dto.getPassword().isEmpty())
                ? DEFAULT_PASSWORD : dto.getPassword();
        // Argon2id：随机 salt + 算法参数随 hash 一并存储，校验时无需先 encode
        account.setPassword(PasswordUtil.encode(password));
        account.setAvatar(dto.getAvatar());
        account.setRoleLevel(dto.getRoleLevel());
        account.setStatus(dto.getStatus());
        account.setIsSuper(dto.getIsSuper());
        account.setDepartment(resolveDepartment(dto.getDepartmentId()));
        account.setDeleted(NOT_DELETED);
        account = accountRepository.save(account);
        return convertToDTO(account);
    }

    @Override
    public AccountDTO updateAccount(Long id, AccountDTO dto) {
        Account account = accountRepository.findByIdAndDeleted(id, NOT_DELETED)
                .orElseThrow(() -> new RuntimeException("账号不存在"));

        if (dto.getUsername() == null || dto.getUsername().isBlank()) {
            throw new RuntimeException("用户名不能为空");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new RuntimeException("姓名不能为空");
        }
        String username = dto.getUsername().trim();
        if (username.length() > 50) {
            throw new RuntimeException("用户名过长（最多50个字符）");
        }
        if (dto.getName().trim().length() > 100) {
            throw new RuntimeException("姓名过长（最多100个字符）");
        }

        if (!account.getUsername().equals(username) &&
            accountRepository.existsByUsernameAndDeleted(username, NOT_DELETED)) {
            throw new RuntimeException("用户名已存在");
        }

        account.setUsername(username);
        account.setName(dto.getName().trim());
        account.setGender(dto.getGender() == null ? null : dto.getGender().trim());
        account.setPhone(dto.getPhone() == null ? null : dto.getPhone().trim());
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            account.setPassword(PasswordUtil.encode(dto.getPassword()));
        }
        account.setRoleLevel(dto.getRoleLevel());
        account.setStatus(dto.getStatus());
        account.setIsSuper(dto.getIsSuper());
        account.setAvatar(dto.getAvatar());
        account.setDepartment(resolveDepartment(dto.getDepartmentId()));
        account = accountRepository.save(account);
        return convertToDTO(account);
    }

    @Override
    public void deleteAccount(Long id) {
        Account account = accountRepository.findByIdAndDeleted(id, NOT_DELETED)
                .orElseThrow(() -> new RuntimeException("账号不存在"));
        // 最后一名超级管理员不允许删除，防止系统无人可管理
        if (Integer.valueOf(1).equals(account.getIsSuper())
                && accountRepository.countByIsSuperAndDeleted(1, NOT_DELETED) <= 1) {
            throw new RuntimeException("仅剩一个超管，不允许再删除");
        }
        account.setDeleted(DELETED);
        // 软删：置 deleted=1，状态默认禁用，username 保留原值（展示无感）。
        account.setStatus(0);
        accountRepository.save(account);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountDTO getAccountById(Long id) {
        Account account = accountRepository.findByIdAndDeleted(id, NOT_DELETED)
                .orElseThrow(() -> new RuntimeException("账号不存在"));
        return convertToDTO(account);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountDTO> getAllAccounts() {
        return accountRepository.findByDeleted(NOT_DELETED).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public AccountDTO restoreAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("账号不存在"));
        if (!DELETED.equals(account.getDeleted())) {
            throw new RuntimeException("该账号未被删除，无需恢复");
        }
        // 还原：仅置 deleted=0，username 保留原值（展示无感）。
        // 唯一性检查改看 username_live：若"同名且未删"的账号已存在，
        // 则被还原行（live=原username）会撞 uk_username_live，提前拦下并给中文提示。
        if (accountRepository.existsByUsernameAndDeleted(account.getUsername(), NOT_DELETED)) {
            throw new RuntimeException("用户名 " + account.getUsername() + " 已被其他账号占用，无法恢复，请联系超管处理");
        }
        account.setDeleted(NOT_DELETED);
        account.setStatus(0); // 恢复后默认禁用，需管理员手动启用
        account = accountRepository.save(account);
        return convertToDTO(account);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountDTO login(String username, String password) {
        // Argon2id：先按用户名取账号，再用 matches 校验哈希（hash 自带 salt）
        Account account = accountRepository.findByUsernameAndDeleted(username, NOT_DELETED)
                .orElseThrow(() -> new RuntimeException("用户名或密码错误"));
        if (!PasswordUtil.matches(password, account.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        if (account.getStatus() != 1) {
            throw new RuntimeException("账号已禁用");
        }
        return convertToDTO(account);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAccountPage(Integer page, Integer size, String username, Integer status, Integer roleLevel, Integer deleted) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 10 : Math.max(1, size);

        // deleted 过滤：null=仅未删（兼容旧行为），0=未删，1=已删
        Integer deletedFilter = deleted == null ? NOT_DELETED : deleted;
        Specification<Account> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("deleted"), deletedFilter));
            if (username != null && !username.isBlank()) {
                predicates.add(cb.like(root.get("username"), "%" + username.trim() + "%"));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (roleLevel != null) {
                predicates.add(cb.equal(root.get("roleLevel"), roleLevel));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Account> result = accountRepository.findAll(spec, PageRequest.of(p, s, Sort.by("id").ascending()));

        List<AccountDTO> list = result.getContent().stream().map(this::convertToDTO).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("list", list);
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        response.put("currentPage", result.getNumber());
        response.put("pageSize", result.getSize());
        return response;
    }

    private Department resolveDepartment(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findByIdAndDeleted(departmentId, NOT_DELETED)
                .orElse(null);
    }

    private AccountDTO convertToDTO(Account account) {
        AccountDTO dto = new AccountDTO();
        dto.setId(account.getId());
        dto.setUsername(account.getUsername());
        dto.setName(account.getName());
        dto.setGender(account.getGender());
        dto.setPhone(account.getPhone());
        dto.setRoleLevel(account.getRoleLevel());
        dto.setStatus(account.getStatus());
        dto.setIsSuper(account.getIsSuper());
        dto.setDeleted(account.getDeleted());
        dto.setAvatar(account.getAvatar());
        Department department = account.getDepartment();
        if (department != null) {
            dto.setDepartmentId(department.getId());
            dto.setDepartmentName(department.getName());
        }
        dto.setCreateTime(account.getCreateTime());
        dto.setUpdateTime(account.getUpdateTime());
        return dto;
    }
}
