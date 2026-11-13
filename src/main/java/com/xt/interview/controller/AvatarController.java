package com.xt.interview.controller;

import com.xt.interview.entity.Account;
import com.xt.interview.repository.AccountRepository;
import com.xt.interview.service.AvatarService;
import com.xt.interview.util.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 头像上传/读取 REST 接口（/api/avatars）。
 * 文件保存在项目内 app.upload-dir（默认 ./uploads/avatars），
 * 支持 png / jpg / jpeg / gif / webp，非 GIF 统一重编码为 PNG。
 * 鉴权：
 *   GET /api/avatars/{fileName}  公开（img 标签无法带 Authorization 头）
 *   POST /api/avatars            仅当前登录用户
 *   POST /api/avatars/{targetId} 管理员为其他用户上传（复用用户管理权限模型）
 */
@RestController
@RequestMapping("/api/avatars")
@CrossOrigin(origins = "*")
public class AvatarController {

    @Autowired
    private AvatarService avatarService;

    @Autowired
    private AccountRepository accountRepository;

    /**
     * 上传头像：
     * - POST /api/avatars  → 仅当前登录用户可上传自己的头像（userId 强制取 token 里的）
     * - POST /api/avatars/{targetUserId} → 管理员为其他用户上传头像
     * 支持 png / jpg / jpeg / gif / webp
     * 返回 { avatar: "avatar_{userId}.gif" }
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> upload(
            @RequestAttribute("userId") Long userId,
            @RequestParam("file") MultipartFile file) throws IOException {
        return doUpload(userId, userId, file);
    }

    /**
     * 管理员为其他用户上传头像。
     * POST /api/avatars/{targetUserId}
     * 仅当 targetUserId 可被当前用户操作时才允许（复用用户管理权限模型）。
     */
    @PostMapping("/{targetUserId}")
    public ResponseEntity<Map<String, String>> uploadForUser(
            @PathVariable Long targetUserId,
            @RequestAttribute("userId") Long currentUserId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest req) throws IOException {
        if (!AuthContext.canOperateAccount(req, targetUserId, accountRepository)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("avatar", "", "error", "无权限上传该用户的头像"));
        }
        // 当前用户为上传人，目标用户头像被更新
        return doUpload(currentUserId, targetUserId, file);
    }

    private ResponseEntity<Map<String, String>> doUpload(Long operatorUserId, Long targetUserId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("avatar", "", "error", "文件为空"));
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        // 只允许图片
        if (!contentType.toLowerCase().startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("avatar", "", "error", "只支持图片格式"));
        }

        String fileName = avatarService.saveAvatar(targetUserId, file.getOriginalFilename(), contentType, file.getInputStream());
        Account account = accountRepository.findByIdAndDeleted(targetUserId, 0)
                .orElseThrow(() -> new RuntimeException("账号不存在"));
        account.setAvatar(fileName);
        accountRepository.save(account);
        return ResponseEntity.ok(Map.of("avatar", fileName));
    }
    /**
     * 读取头像图片。
     * GET /api/avatars/{fileName}
     * 返回二进制流，Content-Type 按扩展名判断
     */
    @GetMapping("/{fileName}")
    public ResponseEntity<byte[]> get(@PathVariable String fileName) throws IOException {
        byte[] bytes = avatarService.getAvatarBytes(fileName);
        MediaType type = MediaType.parseMediaType(avatarService.getAvatarContentType(fileName));
        return ResponseEntity.ok()
                .contentType(type)
                .header("Cache-Control", "public, max-age=3600")
                .body(bytes);
    }
}
