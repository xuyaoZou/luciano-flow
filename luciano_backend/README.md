# Luciano Backend — Spring Boot API

> AI 视频创作平台后端，Spring Boot 3.3 + PostgreSQL 16 + MyBatis-Plus

## 快速启动

```bash
# PostgreSQL (Docker)
docker run -d --name luciano-postgres -p 5432:5432 \
  -e POSTGRES_USER=luciano -e POSTGRES_PASSWORD=luciano_dev -e POSTGRES_DB=luciano \
  postgres:16-alpine

# 后端
cd /Users/luciano/workspace/my/project/luciano/luciano_backend
XYQ_ACCESS_KEY=ak-Eo4...X2qQ mvn spring-boot:run -q

# 或指定 Python 脚本（小云雀 Agent 测试）
XYQ_ACCESS_KEY=ak-Eo4...X2qQ XYQ_PYTHON_SCRIPT=...test_storyboard_flow.py npx tsx watch src/index.ts
```

测试账号：`luciano / luciano123`  
API 地址：`http://localhost:8090/api/v1`

---

## 项目结构

```
luciano_backend/
├── src/main/java/com/luciano/
│   ├── config/
│   │   ├── SecurityConfig.java      # Spring Security + CORS + JWT
│   │   ├── MyBatisPlusConfig.java   # MyBatis-Plus + MapperScan
│   │   └── WebConfig.java           # CORS 配置
│   ├── controller/
│   │   ├── AuthController.java      # 登录/注册/用户信息
│   │   ├── ProjectController.java   # 项目 CRUD
│   │   ├── AgentConversationController.java  # Agent 会话 + 消息
│   │   ├── MediaAssetController.java         # 媒体资产关联
│   │   ├── CharacterAssetController.java      # 角色资产
│   │   ├── SceneAssetController.java          # 场景资产
│   │   ├── PropAssetController.java            # 道具资产
│   │   ├── StoryboardController.java          # 分镜
│   │   ├── GenerationTaskController.java      # 生成任务
│   │   ├── ModelProviderController.java        # 模型提供商
│   │   ├── ModelConfigController.java          # 模型配置
│   │   ├── StylePresetController.java          # 风格预设
│   │   ├── UserApiKeyController.java            # 用户 API Key
│   │   └── ScriptController.java               # 剧本
│   ├── entity/
│   │   ├── User.java
│   │   ├── Project.java
│   │   ├── AgentConversation.java
│   │   ├── AgentMessage.java         # 消息持久化
│   │   ├── MediaAsset.java
│   │   ├── CharacterAsset.java / SceneAsset.java / PropAsset.java
│   │   ├── Storyboard.java
│   │   ├── GenerationTask.java
│   │   └── ... (ModelConfig, StylePreset, UserApiKey, Episode, Script)
│   ├── mapper/ → repository/mapper/
│   │   ├── AgentConversationMapper.java
│   │   ├── AgentMessageMapper.java
│   │   ├── MediaAssetMapper.java
│   │   └── ... (Project, User, Character, Scene, Prop, etc.)
│   ├── service/
│   │   ├── AuthService.java
│   │   ├── ProjectService.java
│   │   ├── AgentConversationService.java  # 会话 + 消息持久化
│   │   ├── AgentMessageService.java        # 消息 CRUD
│   │   ├── MediaAssetService.java          # 资产入库/关联
│   │   └── ... (Character, Scene, Prop, Storyboard, Generation, etc.)
│   ├── spi/impl/
│   │   └── XyqConversationAdapter.java    # 小云雀 Agent 适配器
│   ├── common/
│   │   ├── Result.java               # 统一响应
│   │   └── StringArrayTypeHandler.java
│   └── exception/
│       └── GlobalExceptionHandler.java
├── src/main/resources/
│   ├── application.yml               # 主配置
│   └── db/migration/
│       ├── V1__initial_schema.sql
│       ├── V2__auth_and_api_keys.sql
│       ├── V3__script_enhancement_and_task_fields.sql
│       ├── V5__phase3_creation_workflow.sql
│       ├── V6__agent_mode_refactor.sql
│       └── V7__agent_messages.sql     # 消息持久化
└── pom.xml
```

---

## 核心数据流

### Agent 会话 + 消息持久化

```
用户发消息 → AgentConversationController.sendMessage()
  → 保存用户消息到 agent_messages (role=user, status=completed)
  → 保存 Agent 占位消息到 agent_messages (role=assistant, status=processing)
  → XyqConversationAdapter.submit() → 小云雀 API
  → 返回 runId 给前端轮询

前端轮询 → AgentConversationController.pollConversation()
  → XyqConversationAdapter.poll() → 检查状态
  → 完成：提取媒体 → MediaAssetService.addAsset() → 更新 Agent 消息 status=completed
  → 失败：更新 Agent 消息 status=failed, errorMsg=...

前端恢复 → AgentConversationController.getMessages()
  → 返回完整消息历史（用户消息 + Agent 消息 + 状态）
  → 前端 loadHistory() 重建聊天界面
```

### 消息表结构 (agent_messages)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| conversation_id | bigint | 关联会话 |
| role | varchar(20) | user / assistant |
| content | text | 用户发送的文本 |
| text | text | Agent 回复的文本 |
| run_id | varchar(200) | 小云雀 run_id |
| status | varchar(20) | processing / completed / failed |
| error_msg | text | 失败原因 |
| media_count | integer | 产出的媒体数量 |
| created_at | timestamptz | 创建时间 |

---

## CORS 配置

`SecurityConfig.java` 允许 `http://localhost:3000` 跨域访问。

---

## Mapper 扫描路径

⚠️ **重要**: `@MapperScan("com.luciano.repository.mapper")`  
所有 Mapper 必须放在 `com.luciano.repository.mapper` 包下，否则 Bean 找不到。

---

## 已知问题

| 问题 | 说明 | 状态 |
|------|------|------|
| V7 迁移需手动执行 | Flyway 未配置，需手动 `psql` 执行 | 待配置 Flyway |
| 小云雀视频生成 | 需充积分才能测试 | 待测试 |