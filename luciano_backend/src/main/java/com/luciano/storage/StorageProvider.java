package com.luciano.storage;

import java.io.InputStream;

/**
 * 可插拔存储提供者接口
 * <p>
 * 所有存储实现（local/s3/oss/tos）统一走这个接口。
 * 上传返回公网可访问的 URL，供外部 API 调用。
 */
public interface StorageProvider {

    /**
     * 上传文件，返回公网可访问的 URL
     *
     * @param key         存储路径（如 "image/upload/xxx.png"）
     * @param data        文件流
     * @param contentType MIME 类型
     * @return 公网可访问的 URL
     */
    String upload(String key, InputStream data, String contentType);

    /**
     * 删除文件
     */
    void delete(String key);

    /**
     * 获取公网 URL（不下载，只拼 URL）
     */
    String getPublicUrl(String key);

    /**
     * 下载文件，返回 InputStream
     * <p>
     * 用于代理返回给前端（解决 fetch 302 重定向丢失 Authorization 的问题）
     *
     * @param key 存储路径
     * @return 文件输入流，由调用方负责关闭
     */
    default InputStream download(String key) {
        throw new UnsupportedOperationException("download not supported by " + getType());
    }

    /**
     * 存储类型标识
     */
    String getType();
}