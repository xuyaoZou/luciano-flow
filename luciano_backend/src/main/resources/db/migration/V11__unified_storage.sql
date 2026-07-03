-- V11: 统一存储架构 — TOS 配置迁移为 s3 类型 + MinIO 种子数据

-- 把现有的 tos 类型改为 s3（TOS 本质是 S3 兼容）
UPDATE storage_providers SET provider_type = 's3' WHERE provider_type = 'tos';

-- 加 MinIO 种子数据（默认关闭，需要时手动启用）
INSERT INTO storage_providers (provider_type, name, is_default, config, enabled)
VALUES ('s3', 'MinIO 对象存储', FALSE,
        '{"endpoint": "localhost:9000", "region": "default", "bucket": "luciano-media", "accessKeyId": "minioadmin", "secretAccessKey": "minioadmin", "publicUrl": "http://localhost:9000/luciano-media", "pathStyle": true}',
        FALSE);

-- 更新 local 配置，加上更明确的说明
UPDATE storage_providers SET name = '本地存储（开发用）' WHERE provider_type = 'local';

COMMENT ON COLUMN storage_providers.provider_type IS '存储类型：local / s3（兼容 TOS/MinIO/AWS S3）';