-- ============================================================
-- Luciano Platform - V5: Phase 3 创作流程
-- 资产关联项目 + 批量生成 + 创作模式
-- ============================================================

-- ----------------------------------------------------------
-- 1. 资产表增加 project_id（可选，表示首次创建于哪个项目）
-- ----------------------------------------------------------

ALTER TABLE character_assets ADD COLUMN IF NOT EXISTS project_id BIGINT REFERENCES projects(id);
ALTER TABLE scene_assets ADD COLUMN IF NOT EXISTS project_id BIGINT REFERENCES projects(id);
ALTER TABLE prop_assets ADD COLUMN IF NOT EXISTS project_id BIGINT REFERENCES projects(id);

CREATE INDEX IF NOT EXISTS idx_character_assets_project ON character_assets(project_id);
CREATE INDEX IF NOT EXISTS idx_scene_assets_project ON scene_assets(project_id);
CREATE INDEX IF NOT EXISTS idx_prop_assets_project ON prop_assets(project_id);

-- ----------------------------------------------------------
-- 2. projects 表增加创作模式 + 视觉风格
-- ----------------------------------------------------------

ALTER TABLE projects ADD COLUMN IF NOT EXISTS creation_mode VARCHAR(20) DEFAULT 'manual';
ALTER TABLE projects ADD COLUMN IF NOT EXISTS visual_style TEXT;

-- ----------------------------------------------------------
-- 3. generation_tasks 表增加批量生成标记
-- ----------------------------------------------------------

ALTER TABLE generation_tasks ADD COLUMN IF NOT EXISTS batch_id VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_gen_tasks_batch ON generation_tasks(batch_id);