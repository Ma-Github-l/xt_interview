package com.xt.interview.util;

import com.xt.interview.entity.Account;
import com.xt.interview.repository.AccountRepository;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 从已鉴权请求中提取当前登录人信息，并提供角色判断。
 * 权限约定（按角色级别）：
 * - level 3（超级管理员 isSuper=1）：用户 + 部门 全部 CRUD
 * - level 2（管理员）：用户 CRUD，部门只读
 * - level 1（普通用户）：仅可改自己的信息，部门菜单不可见
 */
public final class AuthContext {

    private AuthContext() {
    }

    public static Long currentUserId(HttpServletRequest req) {
        Object v = req.getAttribute("userId");
        return v == null ? null : Long.valueOf(v.toString());
    }

    public static int currentRoleLevel(HttpServletRequest req) {
        Object v = req.getAttribute("roleLevel");
        return v == null ? 0 : Integer.parseInt(v.toString());
    }

    public static boolean isSuperAdmin(HttpServletRequest req) {
        Object v = req.getAttribute("isSuper");
        return v != null && "1".equals(v.toString());
    }

    /** 超级管理员：用户 + 部门 全部写操作 */
    public static boolean canManageEverything(HttpServletRequest req) {
        return isSuperAdmin(req) && currentRoleLevel(req) >= 3;
    }

    /** 管理员及以上：用户 CRUD */
    public static boolean canManageUsers(HttpServletRequest req) {
        int level = currentRoleLevel(req);
        return isSuperAdmin(req) ? level >= 3 : level >= 2;
    }

    /** 管理员及以上：部门 CRUD */
    public static boolean canManageDepartments(HttpServletRequest req) {
        int level = currentRoleLevel(req);
        return isSuperAdmin(req) ? level >= 3 : level >= 2;
    }

    /** 判断目标账号是否可被当前用户操作 */
    public static boolean canOperateAccount(HttpServletRequest req, Long targetUserId, AccountRepository repo) {
        if (canManageEverything(req)) {
            return true;
        }
        if (canManageUsers(req)) {
            // 管理员：除超级管理员外的账号可操作
            Account target = repo.findByIdAndDeleted(targetUserId, 0).orElse(null);
            return target != null && !(Integer.valueOf(1).equals(target.getIsSuper()) && target.getRoleLevel() != null && target.getRoleLevel() >= 3);
        }
        // 普通用户：仅可操作自己
        Long me = currentUserId(req);
        return me != null && me.equals(targetUserId);
    }

    public static final class UnauthorizedException extends RuntimeException {
        public UnauthorizedException() {
            this("无权限执行该操作");
        }
        public UnauthorizedException(String message) {
            super(message);
        }
    }

    public static void requireCanManageEverything(HttpServletRequest req) {
        if (!canManageEverything(req)) {
            throw new UnauthorizedException("仅超级管理员可执行该操作");
        }
    }

    public static void requireCanManageUsers(HttpServletRequest req) {
        if (!canManageUsers(req)) {
            throw new UnauthorizedException("无用户管理权限");
        }
    }

    public static void requireCanManageDepartments(HttpServletRequest req) {
        if (!canManageDepartments(req)) {
            throw new UnauthorizedException("无部门管理权限");
        }
    }
}
