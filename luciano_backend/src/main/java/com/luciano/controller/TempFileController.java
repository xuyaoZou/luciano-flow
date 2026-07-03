package com.luciano.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 临时文件服务 Controller
 * 仅用于开发测试，生产环境应删除。
 */
@RestController
@RequestMapping("/api/v1/test")
public class TempFileController {

    @Value("${media.storage-path:./uploads/media}")
    private String storagePath;

    @GetMapping("/video/{filename}")
    public ResponseEntity<Resource> serveVideo(@PathVariable String filename) {
        Path filePath = Paths.get(storagePath, filename);
        File file = filePath.toFile();

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        String contentType = filename.endsWith(".mp4") ? "video/mp4" : "application/octet-stream";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                .body(resource);
    }
}