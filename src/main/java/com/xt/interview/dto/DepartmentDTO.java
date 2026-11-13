package com.xt.interview.dto;

import java.time.LocalDateTime;

/**
 * 部门数据传输对象（DTO）。
 * 与 {@link com.xt.interview.entity.Department} 对应，用于 Controller ↔ Service 间的数据交换。
 * 权限：仅超级管理员（isSuper=1 且 roleLevel≥3）可写操作；普通用户仅可读取。
 */
public class DepartmentDTO {
    private Long id;
    private String name;
    private String code;
    private String description;
    private Integer status;
    /** 是否删除：0-未删除，1-已删除 */
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public DepartmentDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
