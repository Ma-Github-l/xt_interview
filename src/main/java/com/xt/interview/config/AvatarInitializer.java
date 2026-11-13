package com.xt.interview.config;

import com.xt.interview.service.AvatarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动初始化器（ApplicationRunner）。
 * 职责：
 *   1. 若默认头像文件（avatar_default.png）不存在，随机生成一张（彩色背景 + 首字母）。
 *   2. 遍历所有未删账号，若 avatar 字段为空则自动填充为默认头像文件名。
 * 目的：保证前端头像列永不显示空白占位符，且与 Flyway 迁移的账号创建顺序解耦。
 */
@Component
public class AvatarInitializer implements ApplicationRunner {

    @Autowired
    private AvatarService avatarService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            avatarService.generateDefaultAvatar();
        } catch (Exception e) {
            System.err.println("[AvatarInitializer] 生成默认头像失败: " + e.getMessage());
        }
    }
}
