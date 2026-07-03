-- V15: Relax method/path NOT NULL for complete/failed operation logs
-- These are not HTTP requests, so they have no method/path
ALTER TABLE api_operation_log ALTER COLUMN method DROP NOT NULL;
ALTER TABLE api_operation_log ALTER COLUMN path DROP NOT NULL;

-- Index for querying by operation_type (including complete/failed)
CREATE INDEX IF NOT EXISTS idx_oplog_op_type ON api_operation_log(operation_type, created_at);