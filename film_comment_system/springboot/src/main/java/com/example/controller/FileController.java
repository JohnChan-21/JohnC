package com.example.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;

import cn.dev33.satoken.stp.StpUtil;
import com.example.common.RateLimiter;
import com.example.common.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 文件相关操作接口
 */
@RestController
@RequestMapping("/files")
public class FileController {

    // 本地磁盘文件的存储路径：由 file.upload-path 配置注入（可用环境变量 FILE_UPLOAD_PATH 覆盖）。
    // 不再依赖 user.dir（JVM 启动目录），避免因启动目录不同导致读写错位；路径须以 / 结尾
    @Value("${file.upload-path}")
    private String filePath;

    /** 单张图片大小上限：5MB（multipart 全局上限为 100MB，这里按业务再收紧） */
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024L;
    /** 上传频率限制：同一账号每分钟最多 20 次 */
    private static final long UPLOAD_LIMIT_PER_MINUTE = 20L;

    org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileController.class);

    @Value("${fileBaseUrl}")
    private String fileBaseUrl;

    @Resource
    private RateLimiter rateLimiter;

    /**
     * 文件上传
     */
    @PostMapping("/upload")
    public Result upload(MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }

        // 0. 频率限制：同一账号每分钟最多 UPLOAD_LIMIT_PER_MINUTE 次，防刷
        if (!allowUpload()) {
            return Result.error("上传过于频繁，请稍后再试");
        }

        // 1. 大小限制：按业务收紧（multipart 全局上限 100MB 之外再兜一层，防超大文件占满磁盘）
        if (file.getSize() > MAX_IMAGE_SIZE) {
            return Result.error("图片大小不能超过 5MB");
        }

        // 2. 获取并校验后缀（白名单）
        String originalFilename = file.getOriginalFilename();
        String suffix = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }
        // 假设只允许图片
        if (!Arrays.asList(".jpg", ".jpeg", ".png", ".webp", ".gif").contains(suffix)) {
            return Result.error("只支持图片格式");
        }

        // 2. 魔数校验：核对文件头字节确认真实类型，防止改后缀伪装（如把脚本/HTML 改名为 .jpg）
        String magicError = checkImageMagic(file, suffix);
        if (magicError != null) {
            return Result.error(magicError);
        }

        // 3. 生成安全的文件名（抛弃原始文件名）
        String safeFileName = System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8) + suffix;
        String realFilePath = filePath + safeFileName;

        // 4. 确保目录存在
        File dir = new File(filePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 5. 流式写入（防 OOM）
        try {
            file.transferTo(new File(realFilePath));
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.error("上传失败，请稍后重试");
        }

        // 6. 返回 URL （此时 URL 是安全的，且没有中文/特殊字符）
        String url = fileBaseUrl + "/files/download/" + safeFileName;
        return Result.success(url);
    }

    /**
     * 上传频率限制：以登录账号（loginId）为维度，每分钟最多 UPLOAD_LIMIT_PER_MINUTE 次。
     * 未登录情形由鉴权拦截器处理，这里不重复判定。
     *
     * @return true 表示允许本次上传
     */
    private boolean allowUpload() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            return true;
        }
        return rateLimiter.tryAcquire("upload:limit:" + loginId, UPLOAD_LIMIT_PER_MINUTE, 60);
    }

    /**
     * 图片魔数（文件签名）校验：仅校验后缀可被伪造，这里核对文件头字节确认真实类型。
     * 支持 JPG / PNG / GIF / WEBP 四种，各自签名如下：
     * JPG  FFD8FF；PNG  89504E47；GIF  "GIF87a"/"GIF89a"；WEBP 前 4 字节 "RIFF" + 第 9~12 字节 "WEBP"。
     *
     * @return null 表示校验通过；否则返回错误提示，由调用方转为业务错误
     */
    private String checkImageMagic(MultipartFile file, String suffix) {
        // WEBP 需校验第 1~4 与第 9~12 字节，故统一读取前 12 字节
        byte[] header = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(header, 0, header.length);
        } catch (IOException e) {
            log.error("读取文件头失败", e);
            return "上传失败，请稍后重试";
        }
        // 真实图片均远大于 12 字节，不足说明文件异常
        if (read < header.length) {
            return "文件内容不是有效的图片";
        }
        switch (suffix) {
            case ".jpg":
            case ".jpeg":
                // JPEG：FF D8 FF
                if (header[0] != (byte) 0xFF || header[1] != (byte) 0xD8 || header[2] != (byte) 0xFF) {
                    return "文件内容不是有效的 JPG 图片";
                }
                break;
            case ".png":
                // PNG：89 50 4E 47
                if (header[0] != (byte) 0x89 || header[1] != 'P' || header[2] != 'N' || header[3] != 'G') {
                    return "文件内容不是有效的 PNG 图片";
                }
                break;
            case ".gif":
                // GIF：ASCII "GIF87a" / "GIF89a"
                String gif = new String(header, 0, 6, StandardCharsets.US_ASCII);
                if (!"GIF87a".equals(gif) && !"GIF89a".equals(gif)) {
                    return "文件内容不是有效的 GIF 图片";
                }
                break;
            case ".webp":
                // WEBP：前 4 字节 "RIFF"，第 9~12 字节 "WEBP"
                String riff = new String(header, 0, 4, StandardCharsets.US_ASCII);
                String webp = new String(header, 8, 4, StandardCharsets.US_ASCII);
                if (!"RIFF".equals(riff) || !"WEBP".equals(webp)) {
                    return "文件内容不是有效的 WEBP 图片";
                }
                break;
            default:
                // 理论上不会到达（后缀已白名单过滤），兜底拒绝
                return "只支持图片格式";
        }
        return null;
    }

    /**
     * 文件下载
     * 安全防护：① 文件名白名单正则；② canonical path 必须在 base 目录内
     */
    @GetMapping("/download/{fileName}")
    public void download(@PathVariable String fileName, HttpServletResponse response) {
        // 1. 文件名白名单：仅允许字母数字._-，长度 1-128，杜绝 ../ 路径穿越
        if (fileName == null || !fileName.matches("^[A-Za-z0-9._-]{1,128}$")) {
            log.warn("非法文件名: {}", fileName);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        // 2. canonical path 校验：解析后必须仍在 base 目录内（防符号链接/.. 绕过）
        File baseDir;
        File targetFile;
        try {
            baseDir = new File(filePath).getCanonicalFile();
            targetFile = new File(baseDir, fileName).getCanonicalFile();
        } catch (IOException e) {
            log.error("路径解析失败: {}", fileName, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        if (!targetFile.toPath().startsWith(baseDir.toPath())) {
            log.warn("路径穿越尝试被拒绝: {}", fileName);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (!targetFile.exists() || !targetFile.isFile()) {
            log.error("文件不存在, 文件名: {}", fileName);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        // 设置响应头：显式声明内容类型。配合全局 X-Content-Type-Options:nosniff，
        // 浏览器只有在拿到正确 Content-Type 时才会内联渲染图片（否则会拒绝显示）
        response.setContentType(contentTypeOf(fileName));
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        try {
            // 通过文件的存储路径拿到文件字节数组
            byte[] bytes = FileUtil.readBytes(targetFile);
            ServletOutputStream os = response.getOutputStream();
            // 将文件字节数组写出到文件流
            os.write(bytes);
            os.flush();
            os.close();
        } catch (IOException e) {
            log.error("文件下载失败, 文件名: {}", fileName, e);
            throw new RuntimeException("文件下载失败", e);
        }
    }

    /**
     * 依据文件后缀返回对应的图片 MIME 类型。
     * 下载接口原本不设置 Content-Type，在启用 nosniff 后浏览器会拒绝对图片做嗅探渲染，
     * 因此必须显式声明；未知后缀回退为二进制流。
     */
    private String contentTypeOf(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

}
