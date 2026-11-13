package com.xt.interview.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 账号实体（sys_account 表）。
 * 软删除：deleted=1 时保留 username 原值，前端按 deleted 筛选展示，无感知。
 * 密码字段统一存 Argon2id 哈希（$argon2id$ 前缀）；存量 MD5 登录时自动升级。
 * 权限模型：roleLevel（1=普通用户，2=管理员，3=超级管理员）+ isSuper（0/1）双重判断。
 */
@Entity
@Table(name = "sys_account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", unique = true, nullable = false, length = 50)
    private String username;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "avatar", length = 200)
    private String avatar;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "role_level")
    private Integer roleLevel;

    @Column(name = "status")
    private Integer status;

    @Column(name = "is_super")
    private Integer isSuper;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "deleted")
    private Integer deleted;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (roleLevel == null) roleLevel = 1;
        if (status == null) status = 1;
        if (isSuper == null) isSuper = 0;
        if (deleted == null) deleted = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

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

    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
