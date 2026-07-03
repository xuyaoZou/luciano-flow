-- V7: Agent 消息持久化
-- 存储用户和 Agent 的对话消息，支持上下文恢复

CREATE TABLE IF NOT EXISTS agent_messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT       NOT NULL REFERENCES agent_conversations(id) ON DELETE CASCADE,
    role            VARCHAR(20)  NOT NULL CHECK (role IN ('user', 'assistant')),
    content         TEXT,                    -- 用户发送的文本
    text            TEXT,                    -- Agent 回复的文本
    run_id          VARCHAR(200),            -- 小云雀 run_id
    status          VARCHAR(20)  DEFAULT 'completed' CHECK (status IN ('processing', 'completed', 'failed')),
    error_msg       TEXT,
    media_count     INTEGER      DEFAULT 0,  -- 本条消息产出的媒体数量
    created_at      TIMESTAMPTZ  DEFAULT now()
);

CREATE INDEX idx_agent_msg_conv ON agent_messages(conversation_id);
CREATE INDEX idx_agent_msg_conv_created ON agent_messages(conversation_id, created_at);

-- 会话表加最后消息时间
ALTER TABLE agent_conversations ADD COLUMN IF NOT EXISTS last_message_at TIMESTAMPTZ;