-- V13: API operation log table
CREATE TABLE IF NOT EXISTS api_operation_log (
    id              BIGSERIAL PRIMARY KEY,

    -- ===== 谁 =====
    user_id         BIGINT,
    username        VARCHAR(100),

    -- ===== 做什么 =====
    adapter_id      VARCHAR(64) NOT NULL,
    capability      VARCHAR(64) NOT NULL,
    operation_type  VARCHAR(32) NOT NULL,     -- submit / poll / download

    -- ===== 关联 =====
    task_id         VARCHAR(255),             -- generation_tasks.task_id
    provider_task_id VARCHAR(255),            -- 厂商返回的 task_id
    project_id      BIGINT,
    episode_id      BIGINT,
    storyboard_id   BIGINT,

    -- ===== API 调用详情 =====
    method          VARCHAR(10) NOT NULL,     -- GET / POST
    path            VARCHAR(512) NOT NULL,
    request_body    TEXT,                     -- 请求参数（脱敏）
    response_status INTEGER,                  -- HTTP 状态码
    response_body   TEXT,                     -- 响应体（截断到 4KB）
    error_code      VARCHAR(50),              -- 业务错误码
    error_message   TEXT,                     -- 错误描述

    -- ===== 结果 =====
    duration_ms     INTEGER,                  -- 调用耗时
    credits_used    DECIMAL(10,2),            -- 扣费
    result_url      TEXT,                     -- 产出文件 URL

    -- ===== 时间 =====
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 核心查询索引
CREATE INDEX idx_oplog_user_time ON api_operation_log(user_id, created_at);
CREATE INDEX idx_oplog_adapter_cap ON api_operation_log(adapter_id, capability, created_at);
CREATE INDEX idx_oplog_task ON api_operation_log(task_id);
CREATE INDEX idx_oplog_status ON api_operation_log(response_status, created_at);
CREATE INDEX idx_oplog_provider_task ON api_operation_log(provider_task_id);