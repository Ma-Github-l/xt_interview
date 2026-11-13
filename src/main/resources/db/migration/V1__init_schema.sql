-- ==================== V1: 完整初始化（账号 + 部门 + 头像） ====================
-- 合并自原 V1~V6，适用于全新环境；已有库无需重新迁移，Flyway 识别版本号后跳过

-- 1. 部门表
CREATE TABLE IF NOT EXISTS `sys_department` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '部门ID',
    `name` VARCHAR(100) NOT NULL COMMENT '部门名称',
    `code` VARCHAR(50) NOT NULL COMMENT '部门编码',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '部门描述',
    `status` INT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `deleted` INT DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_department_code` (`code`),
    INDEX `idx_department_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';

-- 2. 账号表（含部门外键、头像列）
CREATE TABLE IF NOT EXISTS `sys_account` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '账号ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `name` VARCHAR(100) NOT NULL COMMENT '姓名',
    `avatar` VARCHAR(200) DEFAULT NULL COMMENT '头像文件名（相对于 upload-dir 目录）',
    `gender` VARCHAR(10) DEFAULT NULL COMMENT '性别：男/女',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系方式',
    `password` VARCHAR(255) NOT NULL COMMENT '密码（Argon2id 哈希）',
    `role_level` INT DEFAULT 1 COMMENT '权限级别：1-用户，2-管理员，3-超级管理员',
    `status` INT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `is_super` INT DEFAULT 0 COMMENT '是否超级管理员：0-否，1-是',
    `department_id` BIGINT DEFAULT NULL COMMENT '所属部门ID',
    `deleted` INT DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    INDEX `idx_role_level` (`role_level`),
    INDEX `idx_account_deleted` (`deleted`),
    INDEX `idx_account_department` (`department_id`),
    CONSTRAINT `fk_account_department` FOREIGN KEY (`department_id`) REFERENCES `sys_department` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号表';

-- 3. 头像元数据表
CREATE TABLE IF NOT EXISTS `sys_avatar_meta` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '元数据ID',
    `file_name` VARCHAR(200) NOT NULL COMMENT '头像文件名',
    `content_type` VARCHAR(100) DEFAULT NULL COMMENT 'MIME 类型',
    `size_bytes` BIGINT DEFAULT NULL COMMENT '文件大小（字节）',
    `uploaded_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_avatar_file` (`file_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='头像元数据';

-- 4. 初始数据
-- 默认管理员账号：admin / 123456（MD5 哈希，首次登录自动升级为 Argon2id）
INSERT IGNORE INTO `sys_account` (`username`, `name`, `gender`, `phone`, `password`, `role_level`, `status`, `is_super`, `deleted`) VALUES
('admin', '超级管理员', '男', NULL, 'e10adc3949ba59abbe56e057f20f883e', 3, 1, 1, 0);

-- 部门初始数据
INSERT IGNORE INTO `sys_department` (`name`, `code`, `description`, `status`, `deleted`) VALUES
('研发部', 'DEV', '负责产品研发与技术开发', 1, 0),
('运维部', 'OPS', '负责系统运维与基础设施保障', 1, 0);

-- 5. 给所有无头像的未删账号补默认头像文件名
UPDATE `sys_account` SET `avatar` = 'avatar_default.png' WHERE `avatar` IS NULL AND `deleted` = 0;

-- 6. 给现有 gif 头像补元数据
INSERT IGNORE INTO `sys_avatar_meta` (`file_name`, `content_type`, `size_bytes`)
SELECT `avatar`, 'image/gif', NULL
FROM `sys_account`
WHERE `avatar` IS NOT NULL AND `avatar` LIKE '%.gif' AND `deleted` = 0;