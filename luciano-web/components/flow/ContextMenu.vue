<!-- 右键菜单 — 暗色毛玻璃风格 -->
<template>
  <Transition name="context-menu">
    <div
      v-if="visible"
      class="context-menu"
      :style="{ left: position.x + 'px', top: position.y + 'px' }"
      @click.stop
    >
      <template v-if="type === 'node'">
        <!-- 执行此节点：completed 状态隐藏，failed 状态保留 -->
        <button v-if="!isCompleted" class="menu-item primary" :disabled="!canExecute" :class="{ disabled: !canExecute }" @click="canExecute && $emit('executeNode')">
          <span class="menu-icon">▶</span>
          <span>执行此节点</span>
          <span v-if="!canExecute" class="menu-hint">{{ executeHint }}</span>
        </button>

        <!-- 复制节点并编辑：completed 状态显示 -->
        <button v-if="isCompleted" class="menu-item primary" @click="$emit('duplicateWithEdges')">
          <span class="menu-icon">📋</span>
          <span>复制节点并编辑</span>
        </button>

        <button v-if="hasImageOutput" class="menu-item element-action" @click="$emit('becomeElement')">
          <span class="menu-icon">🧑</span>
          <span>成为主体</span>
        </button>
        <button class="menu-item" @click="$emit('duplicate')">
          <span class="menu-icon">📋</span>
          <span>创建副本</span>
          <span class="menu-shortcut">Ctrl+D</span>
        </button>
        <button class="menu-item" @click="$emit('copy')">
          <span class="menu-icon">📑</span>
          <span>复制参数</span>
          <span class="menu-shortcut">Ctrl+C</span>
        </button>
        <div class="menu-divider"></div>
        <button class="menu-item danger" @click="$emit('delete')">
          <span class="menu-icon">🗑️</span>
          <span>删除节点</span>
          <span class="menu-shortcut">Del</span>
        </button>
      </template>
      <template v-else-if="type === 'pane'">
        <button class="menu-item primary" @click="$emit('addNode')">
          <span class="menu-icon">➕</span>
          <span>添加节点</span>
        </button>
        <div class="menu-divider"></div>
        <button class="menu-item" @click="$emit('fitView')">
          <span class="menu-icon">⊞</span>
          <span>适应视图</span>
        </button>
        <button class="menu-item" @click="$emit('selectAll')">
          <span class="menu-icon">☑️</span>
          <span>全选节点</span>
        </button>
      </template>
    </div>
  </Transition>
</template>

<script setup lang="ts">
const props = defineProps<{
  visible: boolean
  position: { x: number; y: number }
  type: 'node' | 'pane'
  hasImageOutput?: boolean
  canExecute?: boolean
  nodeStatus?: string
}>()

const isCompleted = computed(() => props.nodeStatus === 'completed')

const executeHint = computed(() => {
  if (props.nodeStatus === 'running') return '执行中...'
  if (props.nodeStatus === 'completed') return '已完成'
  return '已有输出'
})

defineEmits<{
  executeNode: []
  becomeElement: []
  duplicate: []
  duplicateWithEdges: []
  copy: []
  delete: []
  addNode: []
  fitView: []
  selectAll: []
}>()
</script>

<style scoped>
.context-menu {
  position: fixed;
  z-index: 200;
  min-width: 180px;
  background: var(--flow-surface);
  border: 1px solid var(--flow-border-hover);
  border-radius: 10px;
  padding: 6px;
  box-shadow:
    0 8px 32px rgba(0, 0, 0, 0.5),
    0 0 1px rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(16px);
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 12px;
  border: none;
  background: none;
  color: var(--flow-text-muted);
  font-size: 13px;
  cursor: pointer;
  border-radius: 6px;
  transition: background 0.15s, color 0.15s;
  text-align: left;
}

.menu-item:hover {
  background: var(--flow-hover-bg);
  color: var(--flow-text);
}

.menu-item.primary {
  color: #60a5fa;
  font-weight: 500;
}

.menu-item.primary:hover {
  background: rgba(59, 130, 246, 0.18);
  color: #60a5fa;
}

.menu-item.disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.menu-item.disabled:hover {
  background: none;
}

.menu-hint {
  margin-left: auto;
  font-size: 10px;
  color: var(--flow-text-dim);
  font-style: italic;
}

.menu-item.element-action {
  color: #ff6b6b;
  font-weight: 500;
}

.menu-item.element-action:hover {
  background: rgba(255, 107, 107, 0.18);
  color: #ff8a8a;
}

.menu-item.danger {
  color: #fca5a5;
}

.menu-item.danger:hover {
  background: rgba(239, 68, 68, 0.15);
  color: #fecaca;
}

.menu-icon {
  width: 18px;
  text-align: center;
  font-size: 14px;
  flex-shrink: 0;
}

.menu-shortcut {
  margin-left: auto;
  font-size: 11px;
  color: var(--flow-text-dim);
  font-family: monospace;
}

.menu-divider {
  height: 1px;
  background: var(--flow-surface-hover);
  margin: 4px 8px;
}

/* 过渡动画 */
.context-menu-enter-active {
  transition: opacity 0.12s ease, transform 0.12s ease;
}
.context-menu-enter-from {
  opacity: 0;
  transform: scale(0.95);
}
.context-menu-leave-active {
  transition: opacity 0.08s ease, transform 0.08s ease;
}
.context-menu-leave-to {
  opacity: 0;
  transform: scale(0.97);
}
</style>