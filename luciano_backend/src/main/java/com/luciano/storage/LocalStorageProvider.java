package com.luciano.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地文件系统存储
 * <p>
 * 文件存本地磁盘，URL 指向 /api/v1/media/{id}/file 或配置的 publicUrl。
 * 主要用于开发/测试环境，或不想用对象存储的场景。
 */
@Slf4j
@Component("storageProvider_local")
public class LocalStorageProvider implements StorageProvider {

    @Override
    public String upload(String key, InputStream data, String contentType) {
        // LocalProvider 的 upload 不做实质操作
        // 文件写入由调用方（MediaDownloadService/UploadController）直接处理
        // 因为本地存储的 key 就是相对路径，写入由各调用方自己决定路径
        log.debug("[LocalStorage] Upload key={}: local mode, no remote upload needed", key);
        return null;
    }

    @Override
    public void delete(String key) {
        // 本地文件删除由调用方处理
        log.debug("[LocalStorage] Delete key={}: local mode, no remote delete needed", key);
    }

    @Override
    public String getPublicUrl(String key) {
        // 本地存储的公网 URL 由 StorageService 根据数据库配置拼接
        return null;
    }

    @Override
    public String getType() {
        return "local";
    }
}