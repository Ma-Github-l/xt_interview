package com.xt.interview.service;

import java.io.IOException;
import java.io.InputStream;

/**
 * 头像服务接口：负责头像文件的存储、读取、默认头像生成。
 * 文件保存在项目内 app.upload-dir（默认 ./uploads/avatars），
 * 支持 png / jpg / jpeg / gif / webp，非 GIF 统一重编码为 PNG。
 */
public interface AvatarService {
    /**
     * 保存上传的头像图片到 upload-dir，返回文件名。
     * 支持 png / jpg / jpeg / gif / webp。
     * 文件名命名为 avatar_{userId}.{ext}
     */
    String saveAvatar(Long userId, String originalFilename, String contentType, InputStream in) throws IOException;

    /**
     * 读取头像图片，用于通过 /api/avatars/{fileName} 下载。
     */
    byte[] getAvatarBytes(String fileName) throws IOException;

    /**
     * 返回头像文件的 MIME 类型（根据扩展名判断）。
     */
    String getAvatarContentType(String fileName);

    /**
     * 生成随机头像（初次启动时调用），写入 upload-dir/avatar_default.png
     * 同时返回文件名，供调用者设置到 Account.avatar 字段。
     */
    String generateDefaultAvatar();
}
