package com.xt.interview.dto;

import java.time.LocalDateTime;

/**
 * 账号数据传输对象（DTO）。
 * 与 {@link com.xt.interview.entity.Account} 对应，用于 Controller ↔ Service 间的数据交换，
 * 避免将 JPA 实体直接暴露给外部接口。
 *
 * 权限级别说明：
 * roleLevel = 1  普通用户
 * roleLevel = 2  管理员（可操作用户管理）
 * roleLevel = 3 + isSuper=1  超级管理员（全权，含部门管理）
 */
public class AccountDTO {
    private Long id;
    private String username;
    private String name;
    private String avatar;
    private String gender;
    private String phone;
    private String password;
    private Integer roleLevel;
    private Integer status;
    private Integer isSuper;
    /** 是否删除：0-未删除，1-已删除 */
    private Integer deleted;
    private Long departmentId;
    private String departmentName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public AccountDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Integer getRoleLevel() { return roleLevel; }
    public void setRoleLevel(Integer roleLevel) { this.roleLevel = roleLevel; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getIsSuper() { return isSuper; }
    public void setIsSuper(Integer isSuper) { this.isSuper = isSuper; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
