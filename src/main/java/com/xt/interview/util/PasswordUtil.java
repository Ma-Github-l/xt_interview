package com.xt.interview.util;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码工具：统一使用 Argon2id（替代早期 MD5）。
 * MD5 已不满足现代安全要求（速度过快、可被 GPU/ASIC 批量碰撞），
 * Argon2id 是 2015 年 Password Hashing Competition 冠军，抗暴力破解 / 抗 GPU 破解。
 */
public final class PasswordUtil {

    /**
     * Argon2id 编码：
     * 内存 64MB、迭代 3、并行度 4 —— 兼顾 CPU 性能与抗硬件破解。
     * 输出的 hash 串自带 salt + 参数，验证时由 $argon2id$ 前缀自动解析。
     */
    private static final PasswordEncoder ENCODER =
            new Argon2PasswordEncoder(
                    32,                // salt 长度 32 字节
                    32,                // hash 长度 32 字节
                    4,                 // 并行度 4（抗 GPU 暴力破解）
                    64 * 1024,         // 内存 64MB（Argon2Parameters.memory 单位是 KB）
                    3                 // 迭代次数
            );

    private PasswordUtil() {
    }

    /** 生成 Argon2id 哈希（含随机 salt + 算法参数） */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /** 校验原始密码与存储的哈希是否匹配 */
    public static boolean matches(String rawPassword, String storedHash) {
        return ENCODER.matches(rawPassword, storedHash);
    }

    /**
     * 兼容存量 MD5 数据的校验：
     * 1. 先按 Argon2id 校验（$argon2 前缀的 hash）；
     * 2. 不通过则回退 MD5Util 校验（旧 hash，32 位十六进制）；
     * 3. 若回退成功，调用方应拿到 upgradedHash 并回写数据库完成升级。
     */
    public static MatchResult verify(String rawPassword, String storedHash) {
        if (!storedHash.startsWith("$argon2")) {
            // 非 Argon2 前缀 → 走 MD5 回退
            if (MD5Util.encode(rawPassword).equals(storedHash)) {
                return new MatchResult(true, true, encode(rawPassword));
            }
            return new MatchResult(false, false, null);
        }
        // Argon2id 校验
        if (ENCODER.matches(rawPassword, storedHash)) {
            return new MatchResult(true, false, null);
        }
        return new MatchResult(false, false, null);
    }

    /** 校验结果：是否匹配 + 是否命中 MD5 回退 + 升级后应存的新 hash */
    public static final class MatchResult {
        public final boolean matched;
        public final boolean md5FallbackUsed;
        public final String upgradedHash;

        public MatchResult(boolean matched, boolean md5FallbackUsed, String upgradedHash) {
            this.matched = matched;
            this.md5FallbackUsed = md5FallbackUsed;
            this.upgradedHash = upgradedHash;
        }
    }
}
