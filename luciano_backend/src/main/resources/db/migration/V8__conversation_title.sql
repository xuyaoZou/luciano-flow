-- 给 agent_conversations 添加 title 字段（会话标题，取自第一条用户消息摘要）
ALTER TABLE agent_conversations ADD COLUMN IF NOT EXISTS title VARCHAR(255);

COMMENT ON COLUMN agent_conversations.title IS '会话标题，取自第一条用户消息的摘要';