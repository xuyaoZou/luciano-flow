-- V17: workflow_executions 外键改为 CASCADE 删除
-- 删除工作流时自动删除关联的执行记录

ALTER TABLE workflow_executions
  DROP CONSTRAINT workflow_executions_workflow_id_fkey,
  ADD CONSTRAINT workflow_executions_workflow_id_fkey
  FOREIGN KEY (workflow_id) REFERENCES workflows(id) ON DELETE CASCADE;