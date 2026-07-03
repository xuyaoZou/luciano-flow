-- V12: 为所有表和字段添加中文注释
-- 根据代码逻辑补充完整，方便后续维护

-- ============================================================
-- users 用户表
-- ============================================================
COMMENT ON TABLE users IS '用户表';
COMMENT ON COLUMN users.id IS '主键ID';
COMMENT ON COLUMN users.username IS '用户名，唯一';
COMMENT ON COLUMN users.email IS '邮箱，唯一';
COMMENT ON COLUMN users.password_hash IS '密码哈希（bcrypt）';
COMMENT ON COLUMN users.avatar_url IS '头像URL';
COMMENT ON COLUMN users.role IS '角色：free / admin 等';
COMMENT ON COLUMN users.credits IS '积分余额';
COMMENT ON COLUMN users.created_at IS '创建时间';
COMMENT ON COLUMN users.updated_at IS '更新时间';
COMMENT ON COLUMN users.deleted_at IS '软删除时间，NULL表示未删除';
COMMENT ON COLUMN users.refresh_token IS 'JWT刷新令牌';
COMMENT ON COLUMN users.refresh_token_expires_at IS '刷新令牌过期时间';

-- ============================================================
-- user_api_keys 用户自带API Key表
-- ============================================================
COMMENT ON TABLE user_api_keys IS '用户自带API Key（双Key模式：平台Key兜底+用户自有Key优先）';
COMMENT ON COLUMN user_api_keys.id IS '主键ID';
COMMENT ON COLUMN user_api_keys.user_id IS '关联用户ID';
COMMENT ON COLUMN user_api_keys.provider_name IS '提供商标识：volcengine / siliconflow / minimax / openai / kling / xyq';
COMMENT ON COLUMN user_api_keys.encrypted_key IS 'AES-256加密存储的API Key';
COMMENT ON COLUMN user_api_keys.base_url IS '可选，用户自建端点URL';
COMMENT ON COLUMN user_api_keys.is_active IS '是否启用';
COMMENT ON COLUMN user_api_keys.last_verified IS '最近一次验证通过时间';
COMMENT ON COLUMN user_api_keys.created_at IS '创建时间';
COMMENT ON COLUMN user_api_keys.updated_at IS '更新时间';

-- ============================================================
-- projects 项目表
-- ============================================================
COMMENT ON TABLE projects IS '创作项目（短剧/短视频等）';
COMMENT ON COLUMN projects.id IS '主键ID';
COMMENT ON COLUMN projects.creator_id IS '创建者用户ID';
COMMENT ON COLUMN projects.title IS '项目标题';
COMMENT ON COLUMN projects.description IS '项目描述';
COMMENT ON COLUMN projects.type IS '项目类型：drama（短剧）/ video（短视频）';
COMMENT ON COLUMN projects.genre IS '风格类型：urban_romance / suspense 等';
COMMENT ON COLUMN projects.ratio IS '画面比例：16:9 / 9:16 / 1:1';
COMMENT ON COLUMN projects.visual_style IS '视觉风格描述';
COMMENT ON COLUMN projects.status IS '项目状态：draft / active / completed / archived';
COMMENT ON COLUMN projects.thumbnail IS '项目封面图URL';
COMMENT ON COLUMN projects.metadata IS 'JSONB扩展字段，存储项目级配置';
COMMENT ON COLUMN projects.model_provider IS '当前使用的模型提供商标识';
COMMENT ON COLUMN projects.context_session_id IS 'Agent上下文会话ID，用于复用会话';
COMMENT ON COLUMN projects.provider_meta IS 'JSONB，Agent提供商附加元数据';
COMMENT ON COLUMN projects.created_at IS '创建时间';
COMMENT ON COLUMN projects.updated_at IS '更新时间';
COMMENT ON COLUMN projects.deleted_at IS '软删除时间，NULL表示未删除';

-- ============================================================
-- scripts 剧本大纲表
-- ============================================================
COMMENT ON TABLE scripts IS '剧本大纲（三级结构：原始创意→摘要→完整剧本）';
COMMENT ON COLUMN scripts.id IS '主键ID';
COMMENT ON COLUMN scripts.project_id IS '关联项目ID';
COMMENT ON COLUMN scripts.original_idea IS '原始创意（一句话/一段话）';
COMMENT ON COLUMN scripts.summary IS '剧本摘要（几百字）';
COMMENT ON COLUMN scripts.full_script IS '完整剧本文本';
COMMENT ON COLUMN scripts.generation_mode IS '生成模式：manual（手动）/ agent（Agent自动）';
COMMENT ON COLUMN scripts.source IS '来源：user（用户提交）/ agent（Agent生成）';
COMMENT ON COLUMN scripts.status IS '状态：draft / generating / completed / failed';
COMMENT ON COLUMN scripts.agent_thread_id IS 'Agent会话ID（复用会话）';
COMMENT ON COLUMN scripts.agent_run_id IS 'Agent运行ID（当前轮次）';
COMMENT ON COLUMN scripts.version IS '版本号';
COMMENT ON COLUMN scripts.created_at IS '创建时间';
COMMENT ON COLUMN scripts.updated_at IS '更新时间';

-- ============================================================
-- episodes 剧集表
-- ============================================================
COMMENT ON TABLE episodes IS '剧集（项目下的分集）';
COMMENT ON COLUMN episodes.id IS '主键ID';
COMMENT ON COLUMN episodes.project_id IS '关联项目ID';
COMMENT ON COLUMN episodes.episode_number IS '集号（从1开始）';
COMMENT ON COLUMN episodes.title IS '剧集标题';
COMMENT ON COLUMN episodes.synopsis IS '剧情梗概';
COMMENT ON COLUMN episodes.duration IS '预计时长（秒）';
COMMENT ON COLUMN episodes.status IS '状态：draft / generating / completed / failed';
COMMENT ON COLUMN episodes.xyq_thread_id IS '小云雀Agent会话线程ID';
COMMENT ON COLUMN episodes.bgm_url IS '背景音乐URL';
COMMENT ON COLUMN episodes.bgm_type IS '背景音乐类型标识';
COMMENT ON COLUMN episodes.bgm_volume IS '背景音乐音量（0.0-1.0）';
COMMENT ON COLUMN episodes.metadata IS 'JSONB扩展字段';
COMMENT ON COLUMN episodes.created_at IS '创建时间';
COMMENT ON COLUMN episodes.updated_at IS '更新时间';
COMMENT ON COLUMN episodes.deleted_at IS '软删除时间，NULL表示未删除';

-- ============================================================
-- storyboards 分镜表
-- ============================================================
COMMENT ON TABLE storyboards IS '分镜（剧集下的镜头）';
COMMENT ON COLUMN storyboards.id IS '主键ID';
COMMENT ON COLUMN storyboards.project_id IS '关联项目ID';
COMMENT ON COLUMN storyboards.episode_id IS '关联剧集ID';
COMMENT ON COLUMN storyboards.storyboard_number IS '分镜序号（从1开始）';
COMMENT ON COLUMN storyboards.shot_id IS '镜头标识，如 S01E01_001';
COMMENT ON COLUMN storyboards.description IS '分镜描述（最终版）';
COMMENT ON COLUMN storyboards.raw_description IS '原始描述（Agent生成未编辑）';
COMMENT ON COLUMN storyboards.duration_ms IS '镜头时长（毫秒）';
COMMENT ON COLUMN storyboards.generation_mode IS '生成模式：express（快速）/ professional（专业）';
COMMENT ON COLUMN storyboards.express_prompt IS '快速模式提示词';
COMMENT ON COLUMN storyboards.scene_description IS '场景描述';
COMMENT ON COLUMN storyboards.atmosphere IS '氛围/情绪';
COMMENT ON COLUMN storyboards.time_of_day IS '时间段（早晨/正午/黄昏/夜晚等）';
COMMENT ON COLUMN storyboards.dialogue IS '对白文本';
COMMENT ON COLUMN storyboards.voiceover IS '旁白文本';
COMMENT ON COLUMN storyboards.composed_image_url IS '合成参考图URL（角色+场景）';
COMMENT ON COLUMN storyboards.first_frame_image_url IS '首帧图URL';
COMMENT ON COLUMN storyboards.last_frame_image_url IS '尾帧图URL';
COMMENT ON COLUMN storyboards.video_url IS '生成的视频URL';
COMMENT ON COLUMN storyboards.first_frame_media_id IS '首帧图关联media_assets ID';
COMMENT ON COLUMN storyboards.last_frame_media_id IS '尾帧图关联media_assets ID';
COMMENT ON COLUMN storyboards.video_media_id IS '视频关联media_assets ID';
COMMENT ON COLUMN storyboards.tts_audio_url IS 'TTS配音URL';
COMMENT ON COLUMN storyboards.subtitle_url IS '字幕文件URL';
COMMENT ON COLUMN storyboards.composed_video_url IS '合成视频URL（画面+配音+字幕）';
COMMENT ON COLUMN storyboards.tag_map IS 'JSONB，标签映射（角色/场景/道具位置等）';
COMMENT ON COLUMN storyboards.status IS '状态：draft / composing / generating / completed / failed';
COMMENT ON COLUMN storyboards.created_at IS '创建时间';
COMMENT ON COLUMN storyboards.updated_at IS '更新时间';
COMMENT ON COLUMN storyboards.deleted_at IS '软删除时间，NULL表示未删除';

-- ============================================================
-- character_assets 角色资产表
-- ============================================================
COMMENT ON TABLE character_assets IS '角色资产（项目中的角色定义）';
COMMENT ON COLUMN character_assets.id IS '主键ID';
COMMENT ON COLUMN character_assets.creator_id IS '创建者用户ID';
COMMENT ON COLUMN character_assets.project_id IS '关联项目ID';
COMMENT ON COLUMN character_assets.name IS '角色名称';
COMMENT ON COLUMN character_assets.role_ref IS '角色引用标识（如 protagonist_1）';
COMMENT ON COLUMN character_assets.description IS '角色描述';
COMMENT ON COLUMN character_assets.appearance IS '外貌描述';
COMMENT ON COLUMN character_assets.visual_attributes IS 'JSONB，视觉属性（发型/服装/体型等）';
COMMENT ON COLUMN character_assets.vocal_attributes IS 'JSONB，声音属性（音色/语速/情感等）';
COMMENT ON COLUMN character_assets.personality IS '性格描述';
COMMENT ON COLUMN character_assets.image_url IS '角色参考图URL';
COMMENT ON COLUMN character_assets.media_asset_id IS '关联media_assets ID（角色图）';
COMMENT ON COLUMN character_assets.reference_images IS 'JSONB，参考图列表（含URL和描述）';
COMMENT ON COLUMN character_assets.is_public IS '是否公开（可被其他项目引用）';
COMMENT ON COLUMN character_assets.tags IS 'JSONB，标签数组';
COMMENT ON COLUMN character_assets.created_at IS '创建时间';
COMMENT ON COLUMN character_assets.updated_at IS '更新时间';
COMMENT ON COLUMN character_assets.deleted_at IS '软删除时间，NULL表示未删除';

-- ============================================================
-- scene_assets 场景资产表
-- ============================================================
COMMENT ON TABLE scene_assets IS '场景资产（项目中的场景定义）';
COMMENT ON COLUMN scene_assets.id IS '主键ID';
COMMENT ON COLUMN scene_assets.creator_id IS '创建者用户ID';
COMMENT ON COLUMN scene_assets.project_id IS '关联项目ID';
COMMENT ON COLUMN scene_assets.name IS '场景名称';
COMMENT ON COLUMN scene_assets.location_ref IS '场景引用标识（如 street_day）';
COMMENT ON COLUMN scene_assets.description IS '场景描述';
COMMENT ON COLUMN scene_assets.atmosphere IS '氛围/情绪';
COMMENT ON COLUMN scene_assets.lighting IS '光线描述（自然光/暖光/冷光等）';
COMMENT ON COLUMN scene_assets.visual_attributes IS 'JSONB，视觉属性（色调/构图等）';
COMMENT ON COLUMN scene_assets.image_url IS '场景参考图URL';
COMMENT ON COLUMN scene_assets.media_asset_id IS '关联media_assets ID（场景图）';
COMMENT ON COLUMN scene_assets.reference_images IS 'JSONB，参考图列表';
COMMENT ON COLUMN scene_assets.is_public IS '是否公开';
COMMENT ON COLUMN scene_assets.tags IS 'JSONB，标签数组';
COMMENT ON COLUMN scene_assets.created_at IS '创建时间';
COMMENT ON COLUMN scene_assets.updated_at IS '更新时间';
COMMENT ON COLUMN scene_assets.deleted_at IS '软删除时间，NULL表示未删除';

-- ============================================================
-- prop_assets 道具资产表
-- ============================================================
COMMENT ON TABLE prop_assets IS '道具资产（项目中的道具定义）';
COMMENT ON COLUMN prop_assets.id IS '主键ID';
COMMENT ON COLUMN prop_assets.creator_id IS '创建者用户ID';
COMMENT ON COLUMN prop_assets.project_id IS '关联项目ID';
COMMENT ON COLUMN prop_assets.name IS '道具名称';
COMMENT ON COLUMN prop_assets.prop_ref IS '道具引用标识（如 magic_sword）';
COMMENT ON COLUMN prop_assets.description IS '道具描述';
COMMENT ON COLUMN prop_assets.material IS '材质描述（金属/布料/木质等）';
COMMENT ON COLUMN prop_assets.visual_attributes IS 'JSONB，视觉属性';
COMMENT ON COLUMN prop_assets.image_url IS '道具参考图URL';
COMMENT ON COLUMN prop_assets.media_asset_id IS '关联media_assets ID（道具图）';
COMMENT ON COLUMN prop_assets.reference_images IS 'JSONB，参考图列表';
COMMENT ON COLUMN prop_assets.is_public IS '是否公开';
COMMENT ON COLUMN prop_assets.tags IS 'JSONB，标签数组';
COMMENT ON COLUMN prop_assets.created_at IS '创建时间';
COMMENT ON COLUMN prop_assets.updated_at IS '更新时间';
COMMENT ON COLUMN prop_assets.deleted_at IS '软删除时间，NULL表示未删除';

-- V5 补充的列（ALTER TABLE 加的）
COMMENT ON COLUMN projects.creation_mode IS '创作模式：manual / express / professional';
COMMENT ON COLUMN generation_tasks.batch_id IS '批量生成批次ID';

COMMENT ON COLUMN character_assets.project_id IS '关联项目ID（V5新增）';
COMMENT ON COLUMN scene_assets.project_id IS '关联项目ID（V5新增）';
COMMENT ON COLUMN prop_assets.project_id IS '关联项目ID（V5新增）';
-- ============================================================
COMMENT ON TABLE style_presets IS '风格预设（可复用的视觉风格模板）';
COMMENT ON COLUMN style_presets.id IS '主键ID';
COMMENT ON COLUMN style_presets.creator_id IS '创建者用户ID';
COMMENT ON COLUMN style_presets.name IS '预设名称';
COMMENT ON COLUMN style_presets.category IS '分类：cinematic / anime / watercolor 等';
COMMENT ON COLUMN style_presets.description IS '预设描述';
COMMENT ON COLUMN style_presets.config IS 'JSONB，风格配置参数（模型/提示词/权重等）';
COMMENT ON COLUMN style_presets.preview_url IS '预览图URL';
COMMENT ON COLUMN style_presets.is_public IS '是否公开';
COMMENT ON COLUMN style_presets.tags IS 'JSONB，标签数组';
COMMENT ON COLUMN style_presets.created_at IS '创建时间';
COMMENT ON COLUMN style_presets.updated_at IS '更新时间';
COMMENT ON COLUMN style_presets.deleted_at IS '软删除时间，NULL表示未删除';

-- ============================================================
-- media_assets 媒体资产表
-- ============================================================
COMMENT ON TABLE media_assets IS '媒体资产（图片/视频/音频的统一存储记录）';
COMMENT ON COLUMN media_assets.id IS '主键ID';
COMMENT ON COLUMN media_assets.project_id IS '关联项目ID';
COMMENT ON COLUMN media_assets.user_id IS '创建者用户ID';
COMMENT ON COLUMN media_assets.conversation_id IS '关联Agent会话ID';
COMMENT ON COLUMN media_assets.source IS '来源：kling / seedance / xyq / upload / local';
COMMENT ON COLUMN media_assets.media_type IS '媒体类型：image / video / audio';
COMMENT ON COLUMN media_assets.url IS '原始URL（对象存储公网URL或本地路径）';
COMMENT ON COLUMN media_assets.thumbnail_url IS '缩略图URL';
COMMENT ON COLUMN media_assets.local_path IS '本地存储相对路径';
COMMENT ON COLUMN media_assets.run_id IS '生成任务runId（用于防重复，同conversation不同run各自独立）';
COMMENT ON COLUMN media_assets.agent_message_id IS '关联Agent消息ID';
COMMENT ON COLUMN media_assets.metadata IS 'JSONB，扩展元数据（分辨率/时长/格式等）';
COMMENT ON COLUMN media_assets.tags IS '标签数组';
COMMENT ON COLUMN media_assets.created_at IS '创建时间';
COMMENT ON COLUMN media_assets.deleted_at IS '软删除时间，NULL表示未删除';
COMMENT ON COLUMN media_assets.storage_provider_id IS '存储提供者ID（关联storage_providers表）';
COMMENT ON COLUMN media_assets.storage_key IS '存储键（local=本地相对路径，s3=对象key）';

-- ============================================================
-- generation_tasks 生成任务表
-- ============================================================
COMMENT ON TABLE generation_tasks IS '生成任务（AI图片/视频/音频的生成记录）';
COMMENT ON COLUMN generation_tasks.id IS '主键ID';
COMMENT ON COLUMN generation_tasks.project_id IS '关联项目ID';
COMMENT ON COLUMN generation_tasks.episode_id IS '关联剧集ID';
COMMENT ON COLUMN generation_tasks.storyboard_id IS '关联分镜ID';
COMMENT ON COLUMN generation_tasks.asset_id IS '关联资产ID（角色/场景/道具）';
COMMENT ON COLUMN generation_tasks.user_id IS '发起者用户ID';
COMMENT ON COLUMN generation_tasks.task_type IS '任务类型：image / video / audio';
COMMENT ON COLUMN generation_tasks.generation_mode IS '生成模式：express / professional / manual';
COMMENT ON COLUMN generation_tasks.provider IS '原始提供商标识（xyq/kling等，兼容旧字段）';
COMMENT ON COLUMN generation_tasks.model IS '模型名称';
COMMENT ON COLUMN generation_tasks.adapter_id IS '适配器ID：kling / seedance';
COMMENT ON COLUMN generation_tasks.capability IS '能力标识：text_to_video / image_to_video 等';
COMMENT ON COLUMN generation_tasks.prompt IS '正向提示词';
COMMENT ON COLUMN generation_tasks.negative_prompt IS '反向提示词';
COMMENT ON COLUMN generation_tasks.reference_urls IS 'JSONB，参考图URL列表';
COMMENT ON COLUMN generation_tasks.config IS 'JSONB，生成配置参数（模型/尺寸/时长等）';
COMMENT ON COLUMN generation_tasks.output_url IS '生成结果URL';
COMMENT ON COLUMN generation_tasks.output_path IS '本地存储路径';
COMMENT ON COLUMN generation_tasks.duration_ms IS '生成结果时长（毫秒，视频用）';
COMMENT ON COLUMN generation_tasks.status IS '状态：PENDING / PROCESSING / COMPLETED / FAILED';
COMMENT ON COLUMN generation_tasks.task_id IS '提供者任务ID（格式：adapterId:capability:providerTaskId）';
COMMENT ON COLUMN generation_tasks.provider_source IS '提供者来源：platform / user';
COMMENT ON COLUMN generation_tasks.thread_id IS 'Agent会话线程ID（小云雀等）';
COMMENT ON COLUMN generation_tasks.error_msg IS '失败时的错误信息';
COMMENT ON COLUMN generation_tasks.credits_cost IS '本次消耗积分';
COMMENT ON COLUMN generation_tasks.created_at IS '创建时间';
COMMENT ON COLUMN generation_tasks.updated_at IS '更新时间';
COMMENT ON COLUMN generation_tasks.completed_at IS '完成时间';

-- ============================================================
-- agent_conversations Agent会话表
-- ============================================================
COMMENT ON TABLE agent_conversations IS 'Agent对话会话（用户与AI助手的对话上下文）';
COMMENT ON COLUMN agent_conversations.id IS '主键ID';
COMMENT ON COLUMN agent_conversations.project_id IS '关联项目ID';
COMMENT ON COLUMN agent_conversations.user_id IS '用户ID';
COMMENT ON COLUMN agent_conversations.provider IS '提供者：xyq / openai / custom';
COMMENT ON COLUMN agent_conversations.context_session_id IS '提供者侧会话ID（用于恢复上下文）';
COMMENT ON COLUMN agent_conversations.status IS '状态：active / closed';
COMMENT ON COLUMN agent_conversations.provider_meta IS 'JSONB，提供者附加元数据';
COMMENT ON COLUMN agent_conversations.created_at IS '创建时间';
COMMENT ON COLUMN agent_conversations.updated_at IS '更新时间';
COMMENT ON COLUMN agent_conversations.deleted_at IS '软删除时间，NULL表示未删除';
COMMENT ON COLUMN agent_conversations.title IS '会话标题，取自第一条用户消息的摘要';

-- ============================================================
-- agent_messages Agent消息表
-- ============================================================
COMMENT ON TABLE agent_messages IS 'Agent对话消息（用户与AI的每条消息）';
COMMENT ON COLUMN agent_messages.id IS '主键ID';
COMMENT ON COLUMN agent_messages.conversation_id IS '关联会话ID';
COMMENT ON COLUMN agent_messages.role IS '角色：user / assistant';
COMMENT ON COLUMN agent_messages.content IS '用户发送的文本内容';
COMMENT ON COLUMN agent_messages.text IS 'Agent回复的文本内容';
COMMENT ON COLUMN agent_messages.run_id IS '小云雀run_id（用于关联生成结果）';
COMMENT ON COLUMN agent_messages.status IS '状态：processing / completed / failed';
COMMENT ON COLUMN agent_messages.error_msg IS '失败时的错误信息';
COMMENT ON COLUMN agent_messages.media_count IS '本条消息产出的媒体数量';
COMMENT ON COLUMN agent_messages.created_at IS '创建时间';
COMMENT ON COLUMN agent_messages.updated_at IS '更新时间';

-- ============================================================
-- model_providers 模型提供商表
-- ============================================================
COMMENT ON TABLE model_providers IS '模型提供商配置（Kling/Seedance/火山等）';
COMMENT ON COLUMN model_providers.id IS '主键ID';
COMMENT ON COLUMN model_providers.name IS '提供商标识：kling / seedance / volcengine 等';
COMMENT ON COLUMN model_providers.display_name IS '显示名称：可灵 / Seedance / 火山引擎';
COMMENT ON COLUMN model_providers.service_type IS '服务类型：image / video / tts / agent';
COMMENT ON COLUMN model_providers.base_url IS 'API基础URL';
COMMENT ON COLUMN model_providers.api_key IS 'API密钥（AK/SK或Bearer Token）';
COMMENT ON COLUMN model_providers.api_secret IS 'API密钥（用于JWT签名的SK，如可灵）';
COMMENT ON COLUMN model_providers.is_active IS '是否启用';
COMMENT ON COLUMN model_providers.config IS 'JSONB，提供商特有配置';
COMMENT ON COLUMN model_providers.created_at IS '创建时间';
COMMENT ON COLUMN model_providers.updated_at IS '更新时间';

-- ============================================================
-- model_configs 模型配置表
-- ============================================================
COMMENT ON TABLE model_configs IS '模型配置（项目级/步骤级的模型参数配置）';
COMMENT ON COLUMN model_configs.id IS '主键ID';
COMMENT ON COLUMN model_configs.project_id IS '关联项目ID';
COMMENT ON COLUMN model_configs.step_type IS '步骤类型：character_image / scene_image / storyboard_video 等';
COMMENT ON COLUMN model_configs.provider_id IS '关联model_providers ID';
COMMENT ON COLUMN model_configs.model_name IS '模型名称：kling-v1 / seedance-1.0 等';
COMMENT ON COLUMN model_configs.config IS 'JSONB，模型参数配置（分辨率/风格等）';
COMMENT ON COLUMN model_configs.is_default IS '是否为该步骤类型的默认配置';
COMMENT ON COLUMN model_configs.provider_source IS '来源：platform（平台Key）/ user（用户自有Key）';
COMMENT ON COLUMN model_configs.created_at IS '创建时间';
COMMENT ON COLUMN model_configs.updated_at IS '更新时间';

-- ============================================================
-- adapter_registry 适配器注册表
-- ============================================================
COMMENT ON TABLE adapter_registry IS '适配器注册表（多模型路由，每个适配器声明支持的AI能力）';
COMMENT ON COLUMN adapter_registry.id IS '主键ID';
COMMENT ON COLUMN adapter_registry.adapter_id IS '适配器标识：kling / seedance（唯一）';
COMMENT ON COLUMN adapter_registry.display_name IS '显示名称：可灵 / Seedance';
COMMENT ON COLUMN adapter_registry.description IS '适配器描述';
COMMENT ON COLUMN adapter_registry.capabilities IS '支持的能力数组：text_to_video / image_to_video 等';
COMMENT ON COLUMN adapter_registry.cost_level IS '费用等级：low / medium / high';
COMMENT ON COLUMN adapter_registry.is_active IS '是否启用';
COMMENT ON COLUMN adapter_registry.config IS 'JSONB，适配器配置（API端点映射等）';
COMMENT ON COLUMN adapter_registry.created_at IS '创建时间';
COMMENT ON COLUMN adapter_registry.updated_at IS '更新时间';

-- ============================================================
-- storage_providers 存储提供者表
-- ============================================================
COMMENT ON TABLE storage_providers IS '存储提供者配置（可插拔存储：local/s3/MinIO/TOS等）';
COMMENT ON COLUMN storage_providers.id IS '主键ID';
COMMENT ON COLUMN storage_providers.provider_type IS '类型：local / s3 / minio / tos（兼容映射）';
COMMENT ON COLUMN storage_providers.name IS '显示名称，如"阿里云OSS-生产"';
COMMENT ON COLUMN storage_providers.config IS 'JSONB，存储配置（endpoint/bucket/publicUrl/path等）';
COMMENT ON COLUMN storage_providers.is_default IS '是否为默认存储';
COMMENT ON COLUMN storage_providers.enabled IS '是否启用';
-- storage_providers 没有 created_at / updated_at 列（V10建表时未加）