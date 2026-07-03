-- V9: Add api_secret column to model_providers for JWT signing (Kling etc.)
ALTER TABLE model_providers ADD COLUMN IF NOT EXISTS api_secret VARCHAR(512);

-- Add adapter_registry table for capability routing
CREATE TABLE IF NOT EXISTS adapter_registry (
    id BIGSERIAL PRIMARY KEY,
    adapter_id VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(128),
    description TEXT,
    capabilities TEXT[] NOT NULL DEFAULT '{}',
    cost_level VARCHAR(16),
    is_active BOOLEAN DEFAULT TRUE,
    config JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Add generation_tasks.adapter_id column for routing
ALTER TABLE generation_tasks ADD COLUMN IF NOT EXISTS adapter_id VARCHAR(64);
ALTER TABLE generation_tasks ADD COLUMN IF NOT EXISTS capability VARCHAR(64);