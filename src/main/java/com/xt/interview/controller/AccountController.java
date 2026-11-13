package com.xt.interview.controller;

import com.xt.interview.dto.AccountDTO;
import com.xt.interview.service.AccountService;
import com.xt.interview.util.AuthContext;
import com.xt.interview.repository.AccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 账号管理 REST 接口（/api/accounts）。
 * 权限分层：
 *   超管：全部 CRUD + 还原
 *   管理员（roleLevel≥2）：用户 CRUD，不能操作其他超管
 *   普通用户（roleLevel=1）：仅可改自己信息
 * 所有接口均经 JwtAuthenticationFilter 鉴权（access token 15min），refresh token 走 HttpOnly Cookie。
 */
@RestController
@RequestMapping("/api/accounts")
@CrossOrigin(origins = "*")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;
    @GetMapping
    public ResponseEntity<List<AccountDTO>> getAll() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.getAccountById(id));
    }

    @GetMapping("/page")
    public ResponseEntity<Map<String, Object>> getPage(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer roleLevel,
            @RequestParam(required = false) Integer deleted) {
        return ResponseEntity.ok(accountService.getAccountPage(page, size, username, status, roleLevel, deleted));
    }

    @PostMapping
    public ResponseEntity<AccountDTO> create(@RequestBody AccountDTO dto, HttpServletRequest req) {
        // 创建用户：管理员及以上（超级管理员可创建超级管理员账号）
        AuthContext.requireCanManageUsers(req);
        if (dto.getIsSuper() != null && dto.getIsSuper() == 1) {
            AuthContext.requireCanManageEverything(req);
        }
        return ResponseEntity.ok(accountService.createAccount(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AccountDTO> update(@PathVariable Long id, @RequestBody AccountDTO dto, HttpServletRequest req) {
        // 编辑：超管全放行；管理员可编辑非超管账号；普通用户仅能编辑自己
        if (!AuthContext.canOperateAccount(req, id, accountRepository)) {
            throw new AuthContext.UnauthorizedException("无权限操作该账号");
        }
        // 提升目标为超管 → 仅超管可操作
        if (dto.getIsSuper() != null && dto.getIsSuper() == 1) {
            AuthContext.requireCanManageEverything(req);
        }
        return ResponseEntity.ok(accountService.updateAccount(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest req) {
        if (!AuthContext.canOperateAccount(req, id, accountRepository)) {
            throw new AuthContext.UnauthorizedException("无权限删除该账号");
        }
        if (!AuthContext.canManageUsers(req) && id.equals(AuthContext.currentUserId(req))) {
            throw new AuthContext.UnauthorizedException("普通用户不能删除自己");
        }
        accountService.deleteAccount(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<AccountDTO> restore(@PathVariable Long id, HttpServletRequest req) {
        // 恢复用户：仅超级管理员
        AuthContext.requireCanManageEverything(req);
        return ResponseEntity.ok(accountService.restoreAccount(id));
    }
}
