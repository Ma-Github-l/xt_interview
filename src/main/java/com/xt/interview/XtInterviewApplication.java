package com.xt.interview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 欣铁面试项目主入口。
 * 功能：用户管理、部门管理、头像上传、双 Token 鉴权（Access 15min + Refresh 7天，HttpOnly Cookie 下发）。
 */
@SpringBootApplication
public class XtInterviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(XtInterviewApplication.class, args);
    }
}
