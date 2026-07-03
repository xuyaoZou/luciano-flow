-- V16: Flow 引擎表
-- 参考设计文档 §4.2 数据库表定义

CREATE TABLE workflows (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    thumbnail_url   VARCHAR(500),
    category        VARCHAR(64),             -- 模板分类: "video_generation", "character_consistency", ...
    is_template     BOOLEAN DEFAULT FALSE,    -- 是否为系统模板
    user_id         BIGINT,                   -- NULL for system templates
    project_id      BIGINT,
    nodes           JSONB NOT NULL,           -- 节点列表（序列化存储）
    edges           JSONB NOT NULL,           -- 连线列表（序列化存储）
    variables       JSONB,                    -- 全局变量
    status          VARCHAR(32) DEFAULT 'draft',
    version         INTEGER DEFAULT 1,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

COMMENT ON TABLE workflows IS '工作流定义（含系统模板和用户自定义）';
COMMENT ON COLUMN workflows.id IS '主键';
COMMENT ON COLUMN workflows.name IS '工作流名称';
COMMENT ON COLUMN workflows.description IS '工作流描述';
COMMENT ON COLUMN workflows.thumbnail_url IS '缩略图 URL';
COMMENT ON COLUMN workflows.category IS '模板分类';
COMMENT ON COLUMN workflows.is_template IS '是否为系统模板';
COMMENT ON COLUMN workflows.user_id IS '创建者 ID，模板则为 NULL';
COMMENT ON COLUMN workflows.project_id IS '所属项目 ID';
COMMENT ON COLUMN workflows.nodes IS '节点列表（JSON）';
COMMENT ON COLUMN workflows.edges IS '连线列表（JSON）';
COMMENT ON COLUMN workflows.variables IS '全局变量（JSON）';
COMMENT ON COLUMN workflows.status IS '状态: draft/running/completed/failed';
COMMENT ON COLUMN workflows.version IS '版本号，模板更新时递增';
COMMENT ON COLUMN workflows.created_at IS '创建时间';
COMMENT ON COLUMN workflows.updated_at IS '更新时间';

CREATE TABLE workflow_executions (
    id              BIGSERIAL PRIMARY KEY,
    workflow_id     BIGINT NOT NULL REFERENCES workflows(id),
    user_id         BIGINT NOT NULL,
    project_id      BIGINT,
    status          VARCHAR(32) DEFAULT 'pending',
    dag_snapshot     JSONB NOT NULL,           -- 执行时的 DAG 快照（防模板修改影响执行）
    node_results    JSONB DEFAULT '{}',       -- 节点执行结果
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    error_msg       TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

COMMENT ON TABLE workflow_executions IS '工作流执行实例';
COMMENT ON COLUMN workflow_executions.id IS '主键';
COMMENT ON COLUMN workflow_executions.workflow_id IS '关联工作流 ID';
COMMENT ON COLUMN workflow_executions.user_id IS '执行用户 ID';
COMMENT ON COLUMN workflow_executions.project_id IS '所属项目 ID';
COMMENT ON COLUMN workflow_executions.status IS '状态: pending/running/completed/failed/cancelled';
COMMENT ON COLUMN workflow_executions.dag_snapshot IS '执行时的 DAG 快照（冻结节点和连线）';
COMMENT ON COLUMN workflow_executions.node_results IS '每个节点的执行结果 {nodeId: {status, outputUrl, assetId}}';
COMMENT ON COLUMN workflow_executions.started_at IS '开始执行时间';
COMMENT ON COLUMN workflow_executions.completed_at IS '执行完成时间';
COMMENT ON COLUMN workflow_executions.error_msg IS '错误信息';
COMMENT ON COLUMN workflow_executions.created_at IS '创建时间';

CREATE INDEX idx_workflows_project ON workflows(project_id);
CREATE INDEX idx_workflows_template ON workflows(is_template, category);
CREATE INDEX idx_workflow_exec_workflow ON workflow_executions(workflow_id);
CREATE INDEX idx_workflow_exec_status ON workflow_executions(status);