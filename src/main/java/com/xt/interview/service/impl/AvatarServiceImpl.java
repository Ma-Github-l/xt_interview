package com.xt.interview.service.impl;

import com.xt.interview.repository.AccountRepository;
import com.xt.interview.service.AvatarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

@Service
public class AvatarServiceImpl implements AvatarService {

    private static final java.util.Set<String> SUPPORTED_EXTS =
            java.util.Set.of("png", "jpg", "jpeg", "gif", "webp");

    @Value("${app.upload-dir:./uploads/avatars}")
    private String uploadDir;

    @Autowired
    private AccountRepository accountRepository;

    private Path uploadPath() {
        Path p = Paths.get(uploadDir);
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new RuntimeException("无法创建头像目录: " + p.toAbsolutePath(), e);
        }
        return p;
    }

    @Override
    public String saveAvatar(Long userId, String originalFilename, String contentType, InputStream in) throws IOException {
        // 先查出该用户当前头像文件名，保存新文件后删除旧文件（避免残留）
        String oldFileName = accountRepository.findByIdAndDeleted(userId, 0)
                .map(com.xt.interview.entity.Account::getAvatar)
                .orElse(null);

        String ext = "png";
        if (contentType != null && contentType.toLowerCase().contains("gif")) {
            ext = "gif";
        } else if (contentType != null && contentType.toLowerCase().contains("jpeg")) {
            ext = "jpg";
        } else if (contentType != null && contentType.toLowerCase().contains("webp")) {
            ext = "webp";
        } else if (originalFilename != null && originalFilename.contains(".")) {
            String suffix = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
            if (SUPPORTED_EXTS.contains(suffix)) {
                ext = suffix;
            }
        }

        String fileName;
        // GIF 直接原样保存（保留动画帧）
        if (ext.equals("gif")) {
            fileName = "avatar_" + userId + ".gif";
            Path target = uploadPath().resolve(fileName);
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } else {
            fileName = "avatar_" + userId + ".png";
            Path target = uploadPath().resolve(fileName);
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (!ext.equals("png")) {
                BufferedImage img = ImageIO.read(target.toFile());
                if (img != null) {
                    ImageIO.write(img, "png", target.toFile());
                }
            }
        }

        // 删除旧头像文件（文件名不同且存在时）
        if (oldFileName != null && !oldFileName.isBlank()
                && !oldFileName.equals(fileName)
                && !oldFileName.equals("avatar_default.png")) {
            Path oldPath = uploadPath().resolve(oldFileName);
            if (Files.exists(oldPath)) {
                Files.delete(oldPath);
            }
        }
        return fileName;
    }

    @Override
    public byte[] getAvatarBytes(String fileName) throws IOException {
        if (fileName == null || fileName.contains("..") || fileName.contains("/")) {
            throw new IOException("非法文件名");
        }
        Path p = uploadPath().resolve(fileName);
        if (!Files.exists(p)) {
            p = uploadPath().resolve("avatar_default.png");
        }
        if (!Files.exists(p)) {
            generateDefaultAvatar();
            p = uploadPath().resolve("avatar_default.png");
        }
        return Files.readAllBytes(p);
    }

    @Override
    public String getAvatarContentType(String fileName) {
        if (fileName == null) {
            return "image/png";
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/png";
    }

    @Override
    public String generateDefaultAvatar() {
        String fileName = "avatar_default.png";
        Path target = uploadPath().resolve(fileName);
        if (!Files.exists(target)) {
            try {
                BufferedImage img = createRandomAvatar(256);
                ImageIO.write(img, "png", target.toFile());
            } catch (IOException e) {
                throw new RuntimeException("生成默认头像失败", e);
            }
        }
        try {
            List<com.xt.interview.entity.Account> accounts = accountRepository.findByDeleted(0);
            for (com.xt.interview.entity.Account a : accounts) {
                if (a.getAvatar() == null || a.getAvatar().isBlank()) {
                    a.setAvatar(fileName);
                    accountRepository.save(a);
                }
            }
        } catch (Exception ignored) {
            // 数据库未就绪时忽略
        }
        return fileName;
    }

    private BufferedImage createRandomAvatar(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Random rnd = new Random();
        Color bg = new Color(rnd.nextInt(0xffffff));
        Color fg = new Color(rnd.nextInt(0xffffff));

        g.setColor(bg);
        g.fill(new RoundRectangle2D.Double(0, 0, size, size, size / 2, size / 2));

        String label = "?";
        try {
            var accounts = accountRepository.findByDeleted(0);
            if (!accounts.isEmpty()) {
                String name = accounts.get(0).getName();
                if (name != null && !name.isBlank()) {
                    label = String.valueOf(name.trim().charAt(0)).toUpperCase();
                }
            }
        } catch (Exception ignored) {
        }

        g.setColor(fg);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size / 3));
        FontMetrics fm = g.getFontMetrics();
        int x = (size - fm.stringWidth(label)) / 2;
        int y = (size - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(label, x, y);
        g.dispose();
        return img;
    }
}
