-- ============================================================
-- Luciano Platform - V2 Auth & API Keys
-- Phase 2: Authentication + Dual Key Mode
-- ============================================================

-- ----------------------------------------------------------
-- 1. 用户表补充认证字段
-- ----------------------------------------------------------

ALTER TABLE users ADD COLUMN IF NOT EXISTS refresh_token VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS refresh_token_expires_at TIMESTAMPTZ;

-- ----------------------------------------------------------
-- 2. 用户自带 API Key（双 Key 模式核心）
-- ----------------------------------------------------------

CREATE TABLE user_api_keys (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    provider_name   VARCHAR(50) NOT NULL,  -- volcengine / siliconflow / minimax / openai / xyq
    encrypted_key   TEXT NOT NULL,          -- AES-256 加密存储
    base_url        TEXT,                   -- 可选，用户自建端点
    is_active       BOOLEAN NOT NULL DEFAULT true,
    last_verified   TIMESTAMPTZ,           -- 上次验证时间
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, provider_name)
);

CREATE INDEX idx_user_api_keys_user ON user_api_keys(user_id);
CREATE INDEX idx_user_api_keys_provider ON user_api_keys(provider_name);

-- ----------------------------------------------------------
-- 3. model_configs 新增 provider_source 字段
-- ----------------------------------------------------------

ALTER TABLE model_configs ADD COLUMN IF NOT EXISTS provider_source VARCHAR(20) NOT NULL DEFAULT 'platform';

-- ----------------------------------------------------------
-- 4. 系统项目和模型服务商初始数据
-- ----------------------------------------------------------

-- 插入系统用户（id=0）作为系统项目的 creator
INSERT INTO users (id, username, email, password_hash, role, credits)
VALUES (0, 'system', 'system@luciano.internal', '', 'system', 0)
ON CONFLICT (id) DO NOTHING;

-- 插入系统项目（id=0）用于系统默认模型配置
INSERT INTO projects (id, creator_id, title, description, type, status)
VALUES (0, 0, 'System Defaults', 'System default project for model configs', 'system', 'active')
ON CONFLICT (id) DO NOTHING;

-- 插入模型服务商（如果不存在）
INSERT INTO model_providers (name, display_name, service_type, base_url, api_key, is_active, config)
VALUES (
    'xyq', '小云雀', 'agent',
    'https://xyq.jianying.com/api/biz/v1',
    'ak-Eo4eAWFG5u8zIXRSQApzye3iMAm114AK3ZcnjydX2qQ',
    true,
    '{"submit_url": "/skill/submit_run", "get_thread_url": "/skill/get_thread", "upload_url": "/skill/upload_file", "poll_interval": 5000, "max_poll_time": 300000}'
) ON CONFLICT (name) DO NOTHING;

INSERT INTO model_providers (name, display_name, service_type, base_url, api_key, is_active, config)
VALUES (
    'siliconflow', '硅基流动', 'image',
    'https://api.siliconflow.cn/v1',
    '',
    true,
    '{"default_model": "stable-diffusion-xl", "default_width": 1024, "default_height": 1024}'
) ON CONFLICT (name) DO NOTHING;

INSERT INTO model_providers (name, display_name, service_type, base_url, api_key, is_active, config)
VALUES (
    'minimax', 'MiniMax', 'tts',
    'https://api.minimax.chat',
    '',
    true,
    '{}'
) ON CONFLICT (name) DO NOTHING;

INSERT INTO model_providers (name, display_name, service_type, base_url, api_key, is_active, config)
VALUES (
    'volcengine', '火山引擎', 'video',
    'https://visual.volcengineapi.com',
    '',
    true,
    '{}'
) ON CONFLICT (name) DO NOTHING;

-- 系统默认模型配置（project_id = 0）
INSERT INTO model_configs (project_id, step_type, provider_id, model_name, is_default, provider_source)
SELECT 0, 'character_image', id, 'stable-diffusion-xl', true, 'platform' FROM model_providers WHERE name = 'siliconflow' AND NOT EXISTS (SELECT 1 FROM model_configs WHERE project_id = 0 AND step_type = 'character_image' AND is_default = true)
UNION ALL
SELECT 0, 'scene_image', id, 'stable-diffusion-xl', true, 'platform' FROM model_providers WHERE name = 'siliconflow' AND NOT EXISTS (SELECT 1 FROM model_configs WHERE project_id = 0 AND step_type = 'scene_image' AND is_default = true)
UNION ALL
SELECT 0, 'prop_image', id, 'stable-diffusion-xl', true, 'platform' FROM model_providers WHERE name = 'siliconflow' AND NOT EXISTS (SELECT 1 FROM model_configs WHERE project_id = 0 AND step_type = 'prop_image' AND is_default = true)
UNION ALL
SELECT 0, 'video', id, '', true, 'platform' FROM model_providers WHERE name = 'xyq' AND NOT EXISTS (SELECT 1 FROM model_configs WHERE project_id = 0 AND step_type = 'video' AND is_default = true)
UNION ALL
SELECT 0, 'tts', id, '', true, 'platform' FROM model_providers WHERE name = 'minimax' AND NOT EXISTS (SELECT 1 FROM model_configs WHERE project_id = 0 AND step_type = 'tts' AND is_default = true)
UNION ALL
SELECT 0, 'script_generation', id, '', true, 'platform' FROM model_providers WHERE name = 'xyq' AND NOT EXISTS (SELECT 1 FROM model_configs WHERE project_id = 0 AND step_type = 'script_generation' AND is_default = true);