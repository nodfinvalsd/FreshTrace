package com.freshtrace.common.storage;

import com.freshtrace.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    @Value("${storage.local-dir}")
    private String localDir;

    @Value("${storage.base-url}")
    private String baseUrl;

    @Override
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }
        String ext = resolveExtension(file.getOriginalFilename());
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        String relativePath = "/" + dateDir + "/" + filename;

        try {
            Path target = Paths.get(localDir, dateDir, filename).toAbsolutePath().normalize();
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
        } catch (IOException e) {
            log.error("文件保存失败", e);
            throw new BizException("文件保存失败");
        }
        return baseUrl + relativePath;
    }

    @Override
    public void delete(String url) {
        if (!StringUtils.hasText(url) || !url.startsWith(baseUrl)) {
            return;
        }
        try {
            String relative = url.substring(baseUrl.length());
            Path target = Paths.get(localDir).resolve(relative.replaceFirst("^/", "")).toAbsolutePath().normalize();
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("文件删除失败: {}", url, e);
        }
    }

    private String resolveExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
            return ".jpg";
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
        return ext.length() > 10 ? ".jpg" : ext;
    }
}
