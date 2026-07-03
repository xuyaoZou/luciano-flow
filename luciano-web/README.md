# Luciano Web — 前端项目文档

> AI 视频创作平台前端，Nuxt 3 + Tailwind CSS，Apple 风格设计

## 快速开始

```bash
cd /Users/luciano/workspace/my/project/luciano/luciano-web
pnpm dev          # 开发服务器 http://localhost:3000
pnpm build        # 生产构建
```

后端启动：
```bash
cd /Users/luciano/workspace/my/project/luciano/luciano_backend
XYQ_ACCESS_KEY=ak-Eo4...X2qQ mvn spring-boot:run -q
```

测试账号：`luciano / luciano123`

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 框架 | Nuxt 3.21.6 |
| 样式 | Tailwind CSS v3 |
| 状态 | useState (跨组件) + ref (组件内) |
| HTTP | $fetch (Nuxt 内置) |
| 后端 | Spring Boot 3.3 + PostgreSQL 16 (Docker) |
| ORM | MyBatis-Plus |
| 认证 | JWT (accessToken + refreshToken) |

---

## 项目结构

```
luciano-web/
├── app.vue                    # 根组件（空壳，只用 NuxtLayout）
├── nuxt.config.ts             # Nuxt 配置
├── pages/
│   ├── index.vue              # 首页（触发 default layout）
│   └── login.vue              # 登录/注册页
├── layouts/
│   ├── default.vue            # 主布局：左栏 + 主内容区
│   ├── blank.vue              # 空布局（登录页）
│   └── project.vue            # 项目布局
├── components/
│   ├── HomeChat.vue           # 主聊天页（核心）
│   ├── ChatInput.vue          # 聊天输入框
│   ├── ModelSelector.vue      # 模型选择下拉
│   ├── QuickChip.vue          # 快捷标签（比例等）
│   ├── Sidebar.vue            # 左边栏
│   ├── SidebarButton.vue      # 侧栏导航按钮
│   ├── DramaAgent.vue         # 短剧 Agent 入口页
│   ├── ProjectView.vue        # 项目视图（详情+会话+专业模式）
│   ├── AssetsView.vue         # 资产页（资源库+专业模式标签）
│   ├── MediaLibrary.vue       # 资源库网格
│   ├── ProfessionalMode.vue    # 专业模式入口
│   ├── LinkPopover.vue        # 关联浮层
│   ├── Lightbox.vue           # 大图预览
│   ├── FilterChip.vue         # 筛选标签
│   └── AgentChat.vue          # Agent 聊天（旧版，保留）
├── composables/
│   ├── useApi.ts              # API 客户端（所有后端接口）
│   ├── useAuth.ts             # 认证状态管理
│   └── useChatStore.ts        # 聊天状态持久化（useState）
├── assets/css/
│   └── main.css               # Tailwind + 自定义样式
└── public/
    └── favicon.svg
```

---

## 页面路由与布局

| 路径 | 布局 | 视图 |
|------|------|------|
| `/` | default | HomeChat（创作聊天） |
| `/login` | blank | 登录/注册 |

**default 布局导航**（通过 `activeView` 切换，非路由）：

| 视图 | 组件 | 说明 |
|------|------|------|
| `chat` | HomeChat | 创作聊天（空状态+灵感 / 消息列表+输入框） |
| `drama` | DramaAgent | 短剧 Agent 入口 |
| `project` | ProjectView | 项目详情+专业模式+会话记录 |
| `assets` | AssetsView | 资源库+专业模式标签 |

---

## 核心数据流

### 认证流程
```
登录 → useAuth.login() → token 存 cookie → 请求自动带 Bearer token → 401 自动跳登录页
```

### Agent 聊天流程
```
用户输入 → HomeChat.handleSend()
  → ensureProject() (无项目则自动创建)
  → chatStore.sendMessage(text, ratio, model)
    → createConversation(projectId, provider) (首次)
    → sendMessage(conversationId, message)
    → pollRun(conversationId, runId) (轮询 3s 间隔)
    → getConversationAssets(conversationId) (完成时拉媒体)
```

### 聊天状态持久化
```
useChatStore (composables/useChatStore.ts)
  - messages: useState('chat-messages')  — 跨组件共享，刷新丢失
  - conversationId: useState('chat-conversation-id')
  - activeProjectId: useState('chat-project-id')
  - loadHistory(convId) — 从后端加载消息历史 + 媒体
  - archiveCurrentChat() — 归档：清空创作区，返回会话信息
  - reset() — 登出时清理
```

### 归档流程
```
Agent 完成 → 显示「归档到项目」按钮
  → chatStore.archiveCurrentChat()
    → 清空 messages/conversationId
  → default.vue handleArchived()
    → 刷新侧栏历史列表
    → 切到 ProjectView（项目视图）
```

---

## 后端 API 接口

### Auth
| 接口 | 方法 | 说明 |
|------|------|------|
| `/auth/login` | POST | 登录 |
| `/auth/register` | POST | 注册 |
| `/auth/me` | GET | 当前用户 |

### Projects
| 接口 | 方法 | 说明 |
|------|------|------|
| `/projects?creatorId=` | GET | 项目列表 |
| `/projects` | POST | 创建项目 |
| `/projects/{id}` | GET | 项目详情 |

### Agent Conversations
| 接口 | 方法 | 说明 |
|------|------|------|
| `/agent/conversations` | POST | 创建/复用会话 |
| `/agent/conversations?projectId=` | GET | 项目会话列表 |
| `/agent/conversations/{id}/message` | POST | 发送消息 |
| `/agent/conversations/{id}/poll?runId=` | GET | 轮询状态 |
| `/agent/conversations/{id}/assets` | GET | 会话媒体资产 |
| `/agent/conversations/{id}/messages` | GET | 消息历史 |
| `/agent/conversations/{id}` | DELETE | 关闭会话 |

### Media Assets
| 接口 | 方法 | 说明 |
|------|------|------|
| `/media-assets?projectId=` | GET | 资源库列表 |
| `/media-assets/{id}` | GET | 资产详情 |
| `/media-assets/{id}/link` | POST | 关联到专业模式资产 |
| `/media-assets/{id}/link` | DELETE | 取消关联 |

---

## 数据库表

| 表 | 说明 | 关键字段 |
|-----|------|----------|
| `users` | 用户 | id, username, email, role, credits |
| `projects` | 项目 | id, creator_id, title, type, status |
| `agent_conversations` | Agent 会话 | id, project_id, user_id, provider, context_session_id, status |
| `agent_messages` | 消息记录 | id, conversation_id, role, content, text, run_id, status, media_count |
| `media_assets` | 媒体资产 | id, project_id, user_id, conversation_id, source, media_type, url |

迁移文件：`luciano_backend/src/main/resources/db/migration/V1~V7`

---

## 已知 Bug 与修复记录

| Bug | 根因 | 修复 |
|-----|------|------|
| ChatInput v-model 不工作 | `defineEmits` 返回值未赋给 `emit`，`onInput` 调 `emit()` 报错 | 赋值 `const emit = defineEmits(...)` |
| 发送文字变成灵感模板 | 上述 v-model bug 导致 `inputText` 始终为空，灵感点击直接设值所以能用 | 同上 |
| `conversations/null/message` 500 | 无 projectId 时不创建会话，直接用 null 发消息 | `ensureProject()` 自动创建默认项目 |
| `listProjects` 传错参数 | 传了 `projectId` 而非 `creatorId` | 修正为 `creatorId` |
| `<Transition>` HMR 解析失败 | 多条件组件包在 Transition 里导致新组件无法解析 | 去掉 Transition |
| AgentMessageMapper 找不到 | 放在 `com.luciano.mapper` 但扫描路径是 `com.luciano.repository.mapper` | 移到正确包下 |

---

## 待做

### 高优先级
- [ ] 端到端测试：登录 → 创建项目 → Agent 对话 → 生成图片 → 归档 → 继续对话
- [ ] 关联流程完善（选择具体角色/场景/道具）
- [ ] Auth middleware + 未登录自动跳转

### 中优先级
- [ ] 专业模式子页面（角色/场景/道具/分镜详情）
- [ ] 灵感内容接入真实数据
- [ ] 新建项目弹窗完善（类型选择 UI）

### 低优先级
- [ ] 响应式适配（移动端）
- [ ] 深色/浅色主题切换
- [ ] Agent 对话文字回复展示（目前只有媒体卡片，文字回复未展示）