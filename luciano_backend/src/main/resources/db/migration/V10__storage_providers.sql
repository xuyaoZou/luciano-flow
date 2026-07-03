-- 存储提供者配置表（可插拔存储：local/s3/oss/tos）
CREATE TABLE storage_providers (
    id              BIGSERIAL PRIMARY KEY,
    provider_type   VARCHAR(20) NOT NULL,  -- local / s3 / oss / tos
    name            VARCHAR(50) NOT NULL,  -- 显示名，如"阿里云OSS-生产"
    is_default       BOOLEAN NOT NULL DEFAULT FALSE,
    config          JSONB NOT NULL DEFAULT '{}',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 初始化默认 local provider
INSERT INTO storage_providers (provider_type, name, is_default, config, enabled)
VALUES ('local', '本地存储', TRUE, '{"path": "/Users/luciano/workspace/my/project/luciano/luciano_backend/uploads/media", "publicUrl": "http://localhost:8090"}', TRUE);

-- media_assets 增加存储关联字段
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS storage_provider_id BIGINT;
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS storage_key VARCHAR(500);

COMMENT ON TABLE storage_providers IS '存储提供者配置（可插拔：local/s3/oss/tos）';
COMMENT ON COLUMN media_assets.storage_provider_id IS '使用的存储提供者ID';
COMMENT ON COLUMN media_assets.storage_key IS '对象存储key（local=本地相对路径，s3=对象key）';