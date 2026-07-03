-- ============================================================
-- Luciano Platform - V3 Script Enhancement & Generation Tasks
-- Phase 2: 剧本大纲模块增强 + 生成任务追踪
-- ============================================================

-- ----------------------------------------------------------
-- 1. scripts 表增加 Agent 追踪和版本字段
-- ----------------------------------------------------------

ALTER TABLE scripts ADD COLUMN IF NOT EXISTS agent_thread_id VARCHAR(100);
ALTER TABLE scripts ADD COLUMN IF NOT EXISTS agent_run_id VARCHAR(100);
ALTER TABLE scripts ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 1;

-- ----------------------------------------------------------
-- 2. generation_tasks 表增加双 Key 和 thread 字段
-- ----------------------------------------------------------

ALTER TABLE generation_tasks ADD COLUMN IF NOT EXISTS provider_source VARCHAR(20) NOT NULL DEFAULT 'platform';
ALTER TABLE generation_tasks ADD COLUMN IF NOT EXISTS thread_id VARCHAR(100);