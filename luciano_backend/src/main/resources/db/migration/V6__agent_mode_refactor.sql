-- ============================================================
-- V6: Agent 中转站模式重构
-- 新增: agent_conversations, media_assets
-- 修改: projects 加上下文字段
-- 修改: 专业模式资产表加可选关联字段
-- ============================================================

-- 1. 项目加 Agent 上下文字段
ALTER TABLE projects ADD COLUMN IF NOT EXISTS model_provider VARCHAR(50);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS context_session_id VARCHAR(200);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS provider_meta JSONB DEFAULT '{}';

-- 2. Agent 对话记录表
CREATE TABLE IF NOT EXISTS agent_conversations (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    provider VARCHAR(50) NOT NULL,
    context_session_id VARCHAR(200),
    status VARCHAR(20) DEFAULT 'active',
    provider_meta JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_agent_conv_project ON agent_conversations(project_id);
CREATE INDEX IF NOT EXISTS idx_agent_conv_user ON agent_conversations(user_id);
CREATE INDEX IF NOT EXISTS idx_agent_conv_status ON agent_conversations(status);
CREATE INDEX IF NOT EXISTS idx_agent_conv_context ON agent_conversations(context_session_id);

-- 3. 统一资源库
CREATE TABLE IF NOT EXISTS media_assets (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    conversation_id BIGINT REFERENCES agent_conversations(id),
    source VARCHAR(20) NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    url TEXT NOT NULL,
    thumbnail_url TEXT,
    metadata JSONB DEFAULT '{}',
    tags TEXT[],
    created_at TIMESTAMPTZ DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_media_project ON media_assets(project_id);
CREATE INDEX IF NOT EXISTS idx_media_user ON media_assets(user_id);
CREATE INDEX IF NOT EXISTS idx_media_conv ON media_assets(conversation_id);
CREATE INDEX IF NOT EXISTS idx_media_type ON media_assets(media_type);
CREATE INDEX IF NOT EXISTS idx_media_source ON media_assets(source);

-- 4. 专业模式资产加可选关联字段
ALTER TABLE character_assets ADD COLUMN IF NOT EXISTS media_asset_id BIGINT REFERENCES media_assets(id);
ALTER TABLE scene_assets ADD COLUMN IF NOT EXISTS media_asset_id BIGINT REFERENCES media_assets(id);
ALTER TABLE prop_assets ADD COLUMN IF NOT EXISTS media_asset_id BIGINT REFERENCES media_assets(id);
ALTER TABLE storyboards ADD COLUMN IF NOT EXISTS first_frame_media_id BIGINT REFERENCES media_assets(id);
ALTER TABLE storyboards ADD COLUMN IF NOT EXISTS last_frame_media_id BIGINT REFERENCES media_assets(id);
ALTER TABLE storyboards ADD COLUMN IF NOT EXISTS video_media_id BIGINT REFERENCES media_assets(id);

-- 5. 数据迁移: 将现有 episode.xyq_thread_id 迁移到 projects
-- 只迁移第一个有 thread_id 的 episode
UPDATE projects p
SET context_session_id = sub.thread_id,
    model_provider = 'xyq'
FROM (
    SELECT DISTINCT ON (e.project_id) e.project_id, e.xyq_thread_id AS thread_id
    FROM episodes e
    WHERE e.xyq_thread_id IS NOT NULL
    ORDER BY e.project_id, e.id
) sub
WHERE p.id = sub.project_id
  AND p.context_session_id IS NULL;