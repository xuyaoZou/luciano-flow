-- ============================================================
-- Luciano Platform - V1 Initial Schema
-- Phase 1: Foundation (Data Model + Asset System)
-- ============================================================

-- ----------------------------------------------------------
-- 用户域
-- ----------------------------------------------------------

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    avatar_url      TEXT,
    role            VARCHAR(20) NOT NULL DEFAULT 'free',
    credits         INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);

-- 会员套餐
CREATE TABLE membership_plans (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(50) NOT NULL,
    level           VARCHAR(20) NOT NULL,
    price_cents     INTEGER NOT NULL,
    credits_per_month INTEGER NOT NULL,
    features        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 用户会员记录
CREATE TABLE user_memberships (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    plan_id         BIGINT NOT NULL REFERENCES membership_plans(id),
    started_at      TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 积分流水
CREATE TABLE credit_transactions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    amount          INTEGER NOT NULL,
    balance_after   INTEGER NOT NULL,
    type            VARCHAR(30) NOT NULL,
    reference_type  VARCHAR(50),
    reference_id    BIGINT,
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_credit_transactions_user ON credit_transactions(user_id);
CREATE INDEX idx_credit_transactions_type ON credit_transactions(type);

-- 充值订单
CREATE TABLE payment_orders (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    amount_cents    INTEGER NOT NULL,
    credits         INTEGER NOT NULL,
    payment_method   VARCHAR(30),
    payment_status   VARCHAR(20) NOT NULL DEFAULT 'pending',
    transaction_id   VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ----------------------------------------------------------
-- 资产域（核心差异化）
-- ----------------------------------------------------------

-- 角色资产
CREATE TABLE character_assets (
    id                  BIGSERIAL PRIMARY KEY,
    creator_id          BIGINT NOT NULL REFERENCES users(id),
    name                VARCHAR(100) NOT NULL,
    role_ref            VARCHAR(20),
    description         TEXT,
    appearance          TEXT,
    visual_attributes   JSONB DEFAULT '{}',
    vocal_attributes    JSONB DEFAULT '{}',
    personality          TEXT,
    image_url            TEXT,
    reference_images     JSONB DEFAULT '[]',
    is_public            BOOLEAN NOT NULL DEFAULT false,
    tags                 JSONB DEFAULT '[]',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at           TIMESTAMPTZ
);

CREATE INDEX idx_character_assets_creator ON character_assets(creator_id);
CREATE INDEX idx_character_assets_public ON character_assets(is_public) WHERE is_public = true;

-- 场景资产
CREATE TABLE scene_assets (
    id                  BIGSERIAL PRIMARY KEY,
    creator_id          BIGINT NOT NULL REFERENCES users(id),
    name                VARCHAR(100) NOT NULL,
    location_ref        VARCHAR(20),
    description         TEXT,
    atmosphere          TEXT,
    lighting             TEXT,
    visual_attributes   JSONB DEFAULT '{}',
    image_url           TEXT,
    reference_images    JSONB DEFAULT '[]',
    is_public           BOOLEAN NOT NULL DEFAULT false,
    tags                JSONB DEFAULT '[]',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE INDEX idx_scene_assets_creator ON scene_assets(creator_id);

-- 道具资产
CREATE TABLE prop_assets (
    id                  BIGSERIAL PRIMARY KEY,
    creator_id          BIGINT NOT NULL REFERENCES users(id),
    name                VARCHAR(100) NOT NULL,
    prop_ref            VARCHAR(20),
    description         TEXT,
    material            TEXT,
    visual_attributes   JSONB DEFAULT '{}',
    image_url           TEXT,
    reference_images    JSONB DEFAULT '[]',
    is_public           BOOLEAN NOT NULL DEFAULT false,
    tags                JSONB DEFAULT '[]',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE INDEX idx_prop_assets_creator ON prop_assets(creator_id);

-- 风格预设
CREATE TABLE style_presets (
    id                  BIGSERIAL PRIMARY KEY,
    creator_id          BIGINT NOT NULL REFERENCES users(id),
    name                VARCHAR(100) NOT NULL,
    category            VARCHAR(30) NOT NULL,
    description         TEXT,
    config              JSONB NOT NULL DEFAULT '{}',
    preview_url         TEXT,
    is_public           BOOLEAN NOT NULL DEFAULT false,
    tags                JSONB DEFAULT '[]',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE INDEX idx_style_presets_creator ON style_presets(creator_id);

-- ----------------------------------------------------------
-- 创作域
-- ----------------------------------------------------------

-- 项目（支持多创作类型）
CREATE TABLE projects (
    id                  BIGSERIAL PRIMARY KEY,
    creator_id          BIGINT NOT NULL REFERENCES users(id),
    title               VARCHAR(200) NOT NULL,
    description         TEXT,
    type                VARCHAR(30) NOT NULL DEFAULT 'short_drama',
    genre               VARCHAR(50),
    ratio               VARCHAR(10) NOT NULL DEFAULT '9:16',
    visual_style        VARCHAR(50),
    status              VARCHAR(20) NOT NULL DEFAULT 'draft',
    thumbnail           TEXT,
    metadata            JSONB DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE INDEX idx_projects_creator ON projects(creator_id);
CREATE INDEX idx_projects_type ON projects(type);

-- 剧本大纲
CREATE TABLE scripts (
    id                  BIGSERIAL PRIMARY KEY,
    project_id          BIGINT NOT NULL REFERENCES projects(id),
    original_idea       TEXT,
    summary             TEXT,
    full_script         TEXT,
    generation_mode     VARCHAR(20) NOT NULL DEFAULT 'manual',
    source              VARCHAR(20) NOT NULL DEFAULT 'user',
    status              VARCHAR(20) NOT NULL DEFAULT 'draft',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 集（可选，短剧类型才有）
CREATE TABLE episodes (
    id                  BIGSERIAL PRIMARY KEY,
    project_id          BIGINT NOT NULL REFERENCES projects(id),
    episode_number      INTEGER NOT NULL,
    title               VARCHAR(200) NOT NULL,
    synopsis            TEXT,
    duration            INTEGER NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'draft',
    xyq_thread_id       VARCHAR(255),
    bgm_url             TEXT,
    bgm_type            VARCHAR(20) DEFAULT 'none',
    bgm_volume          REAL DEFAULT 0.3,
    metadata            JSONB DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_episodes_project_number ON episodes(project_id, episode_number);

-- 分镜（统一数据模型，支持多创作类型）
CREATE TABLE storyboards (
    id                      BIGSERIAL PRIMARY KEY,
    project_id              BIGINT NOT NULL REFERENCES projects(id),
    episode_id              BIGINT REFERENCES episodes(id),
    storyboard_number       INTEGER NOT NULL,
    shot_id                 VARCHAR(50),

    description             TEXT,
    raw_description         TEXT,
    duration_ms             INTEGER DEFAULT 5000,

    generation_mode         VARCHAR(20) NOT NULL DEFAULT 'manual',
    express_prompt           TEXT,

    scene_description        TEXT,
    atmosphere              TEXT,
    time_of_day              VARCHAR(50),

    dialogue                TEXT,
    voiceover               TEXT,

    composed_image_url      TEXT,
    first_frame_image_url   TEXT,
    last_frame_image_url    TEXT,
    video_url               TEXT,
    tts_audio_url           TEXT,
    subtitle_url            TEXT,
    composed_video_url      TEXT,

    tag_map                 JSONB DEFAULT '{}',

    status                  VARCHAR(20) NOT NULL DEFAULT 'draft',

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ
);

CREATE INDEX idx_storyboards_project ON storyboards(project_id);
CREATE INDEX idx_storyboards_episode ON storyboards(episode_id);

-- 分镜-角色关联
CREATE TABLE storyboard_characters (
    storyboard_id   BIGINT NOT NULL REFERENCES storyboards(id),
    character_id    BIGINT NOT NULL REFERENCES character_assets(id),
    position        VARCHAR(50),
    position_detail TEXT,
    action          TEXT,
    visibility      VARCHAR(20) DEFAULT 'visible',
    PRIMARY KEY (storyboard_id, character_id)
);

-- 分镜-场景关联
CREATE TABLE storyboard_scenes (
    storyboard_id   BIGINT NOT NULL REFERENCES storyboards(id),
    scene_id         BIGINT NOT NULL REFERENCES scene_assets(id),
    PRIMARY KEY (storyboard_id, scene_id)
);

-- 分镜-道具关联
CREATE TABLE storyboard_props (
    storyboard_id   BIGINT NOT NULL REFERENCES storyboards(id),
    prop_id          BIGINT NOT NULL REFERENCES prop_assets(id),
    PRIMARY KEY (storyboard_id, prop_id)
);

-- 分镜-风格关联
CREATE TABLE storyboard_styles (
    storyboard_id   BIGINT NOT NULL REFERENCES storyboards(id),
    style_id        BIGINT NOT NULL REFERENCES style_presets(id),
    PRIMARY KEY (storyboard_id, style_id)
);

-- 集-角色关联
CREATE TABLE episode_characters (
    episode_id      BIGINT NOT NULL REFERENCES episodes(id),
    character_id    BIGINT NOT NULL REFERENCES character_assets(id),
    PRIMARY KEY (episode_id, character_id)
);

-- 集-场景关联
CREATE TABLE episode_scenes (
    episode_id      BIGINT NOT NULL REFERENCES episodes(id),
    scene_id        BIGINT NOT NULL REFERENCES scene_assets(id),
    PRIMARY KEY (episode_id, scene_id)
);

-- 集-道具关联
CREATE TABLE episode_props (
    episode_id      BIGINT NOT NULL REFERENCES episodes(id),
    prop_id         BIGINT NOT NULL REFERENCES prop_assets(id),
    PRIMARY KEY (episode_id, prop_id)
);

-- 集-风格关联
CREATE TABLE episode_styles (
    episode_id      BIGINT NOT NULL REFERENCES episodes(id),
    style_id       BIGINT NOT NULL REFERENCES style_presets(id),
    PRIMARY KEY (episode_id, style_id)
);

-- ----------------------------------------------------------
-- 模型域（SPI）
-- ----------------------------------------------------------

-- 模型服务商
CREATE TABLE model_providers (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(50) NOT NULL UNIQUE,
    display_name    VARCHAR(100) NOT NULL,
    service_type    VARCHAR(30) NOT NULL,
    base_url        TEXT,
    api_key         TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    config          JSONB DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 模型配置（步骤级）
CREATE TABLE model_configs (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL REFERENCES projects(id),
    step_type       VARCHAR(30) NOT NULL,
    provider_id     BIGINT NOT NULL REFERENCES model_providers(id),
    model_name      VARCHAR(100),
    config          JSONB DEFAULT '{}',
    is_default       BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_model_configs_project_step ON model_configs(project_id, step_type, is_default);

-- 生成任务
CREATE TABLE generation_tasks (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL REFERENCES projects(id),
    episode_id      BIGINT REFERENCES episodes(id),
    storyboard_id   BIGINT REFERENCES storyboards(id),
    asset_id        BIGINT,

    task_type       VARCHAR(30) NOT NULL,
    generation_mode VARCHAR(20) NOT NULL,
    provider        VARCHAR(50) NOT NULL,
    model           VARCHAR(100),

    prompt          TEXT,
    negative_prompt TEXT,
    reference_urls  JSONB DEFAULT '[]',
    config          JSONB DEFAULT '{}',

    output_url      TEXT,
    output_path     TEXT,
    duration_ms     INTEGER,

    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    task_id         VARCHAR(255),
    error_msg       TEXT,
    credits_cost    INTEGER DEFAULT 0,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);

CREATE INDEX idx_generation_tasks_project ON generation_tasks(project_id);
CREATE INDEX idx_generation_tasks_status ON generation_tasks(status);
CREATE INDEX idx_generation_tasks_type ON generation_tasks(task_type);

-- ----------------------------------------------------------
-- 广场域
-- ----------------------------------------------------------

-- 广场条目
CREATE TABLE marketplace_items (
    id                  BIGSERIAL PRIMARY KEY,
    creator_id          BIGINT NOT NULL REFERENCES users(id),
    item_type           VARCHAR(30) NOT NULL,
    source_asset_id     BIGINT,
    source_config_id    BIGINT,
    title               VARCHAR(200) NOT NULL,
    description         TEXT,
    content             JSONB NOT NULL DEFAULT '{}',
    category            VARCHAR(50),
    tags                JSONB DEFAULT '[]',
    download_count      INTEGER NOT NULL DEFAULT 0,
    rating_avg          REAL DEFAULT 0,
    rating_count        INTEGER NOT NULL DEFAULT 0,
    preview_url         TEXT,
    thumbnail_url       TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'active',
    is_featured         BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_marketplace_items_type ON marketplace_items(item_type);
CREATE INDEX idx_marketplace_items_category ON marketplace_items(category);
CREATE INDEX idx_marketplace_items_creator ON marketplace_items(creator_id);
CREATE INDEX idx_marketplace_items_featured ON marketplace_items(is_featured) WHERE is_featured = true;

-- 广场评分
CREATE TABLE marketplace_ratings (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    item_id             BIGINT NOT NULL REFERENCES marketplace_items(id),
    rating              SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    review              TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, item_id)
);

CREATE INDEX idx_marketplace_ratings_item ON marketplace_ratings(item_id);

-- 用户导入记录
CREATE TABLE user_imports (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    item_id             BIGINT NOT NULL REFERENCES marketplace_items(id),
    local_asset_id      BIGINT,
    local_config_id     BIGINT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, item_id)
);

CREATE INDEX idx_user_imports_user ON user_imports(user_id);