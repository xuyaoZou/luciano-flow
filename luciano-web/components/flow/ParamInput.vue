<!-- 参数输入控件 — 根据 ParamSchema 类型动态渲染（暗色科技风） -->
<template>
  <div class="param-input-wrapper" :class="{ 'readonly': readonly }">
    <!-- ENUM 类型：下拉选择 -->
    <select
      v-if="schema.type === 'ENUM'"
      v-model="localValue"
      class="param-select"
      :disabled="readonly"
    >
      <option v-for="opt in schema.options" :key="opt" :value="opt">{{ formatOption(opt) }}</option>
    </select>

    <!-- BOOLEAN 类型：开关 -->
    <label v-else-if="schema.type === 'BOOLEAN'" class="param-switch" :class="{ 'disabled': readonly }">
      <input
        type="checkbox"
        v-model="localValue"
        :disabled="readonly"
      />
      <span class="switch-slider"></span>
    </label>

    <!-- INTEGER 类型：智能时长模式（duration 专用）-->
    <div v-else-if="schema.type === 'INTEGER' && allowsMinusOne" class="duration-control">
      <!-- 智能时长开关 -->
      <label class="duration-smart-toggle" :class="{ 'disabled': readonly }">
        <input
          type="checkbox"
          :checked="isSmartDuration"
          :disabled="readonly"
          @change="toggleSmartDuration"
        />
        <span class="toggle-label">智能时长</span>
      </label>
      <!-- 步进器（关闭智能时长时显示）-->
      <div v-if="!isSmartDuration" class="duration-stepper">
        <button
          type="button"
          class="stepper-btn"
          :disabled="readonly || durationValue <= effectiveMin"
          @click="stepDuration(-1)"
        >−</button>
        <span class="stepper-value">{{ durationValue }}</span>
        <button
          type="button"
          class="stepper-btn"
          :disabled="readonly || durationValue >= effectiveMax"
          @click="stepDuration(1)"
        >+</button>
        <span class="stepper-range">{{ effectiveMin }} ~ {{ effectiveMax }}秒</span>
      </div>
    </div>

    <!-- INTEGER 类型：普通数字输入 -->
    <input
      v-else-if="schema.type === 'INTEGER'"
      type="number"
      v-model="localValue"
      :min="schema.min ?? undefined"
      :max="schema.max ?? undefined"
      :step="schema.step ?? 1"
      class="param-input"
      :readonly="readonly"
    />

    <!-- FLOAT 类型：数字输入 -->
    <input
      v-else-if="schema.type === 'FLOAT'"
      type="number"
      v-model="localValue"
      :min="schema.min ?? undefined"
      :max="schema.max ?? undefined"
      :step="schema.step ?? 0.1"
      class="param-input"
      :readonly="readonly"
    />

    <!-- IMAGE_URL / VIDEO_URL 类型：URL 输入 -->
    <input
      v-else-if="schema.type === 'IMAGE_URL' || schema.type === 'VIDEO_URL'"
      type="url"
      v-model="localValue"
      placeholder="输入 URL..."
      class="param-input"
      :readonly="readonly"
    />

    <!-- STRING 类型（含 multilingual）：多行文本 -->
    <textarea
      v-else-if="schema.type === 'STRING' && schema.multilingual"
      v-model="localValue"
      :placeholder="schema.description || ''"
      rows="3"
      class="param-textarea"
      :readonly="readonly"
    />

    <!-- STRING 类型（普通）：单行输入 -->
    <input
      v-else
      type="text"
      v-model="localValue"
      :placeholder="schema.description || ''"
      class="param-input"
      :readonly="readonly"
    />

    <!-- 参数描述 -->
    <div v-if="schema.description" class="param-desc">{{ schema.description }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ParamSchema } from '~/types/adapter'

const props = defineProps<{
  schema: ParamSchema
  modelValue: any
  readonly?: boolean
  allParams?: Record<string, any>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: any]
}>()

const readonly = computed(() => props.readonly ?? false)

/** 是否允许 -1 作为特殊值（duration 智能时长）*/
const allowsMinusOne = computed(() => {
  return props.schema.description?.includes('-1=') || props.schema.description?.includes('-1 表示') || false
})

/** 判断当前模型是否为 V2（2.0 系列）*/
const isModelV2 = computed(() => {
  if (!props.allParams) return false
  const model = props.allParams.model
  if (!model) return false
  return model.includes('2-0') || model.includes('seedance-2') || model.includes('dreamina-seedance-2')
})

/** 根据模型版本动态计算 min/max */
const effectiveMin = computed(() => {
  if (isModelV2.value) return 4  // DURATION_MIN_V2
  // V1 或混合：取 Schema 的 min（已经是 3）
  return props.schema.min ?? 3
})

const effectiveMax = computed(() => {
  // max 在 Schema 里统一是 15（V2）或 12（V1），直接用
  if (isModelV2.value) return 15  // DURATION_MAX_V2
  return props.schema.max ?? 12
})

/** 是否处于智能时长模式（value === -1） */
const isSmartDuration = computed(() => localValue.value === -1)

/** 步进器显示值（非智能模式下） */
const durationValue = ref(Math.max(effectiveMin.value, Math.min(effectiveMax.value, props.modelValue ?? 5)))
if (durationValue.value === -1) durationValue.value = effectiveMin.value

// 当 model 变化导致范围变化时，钳制当前值
watch([effectiveMin, effectiveMax], ([newMin, newMax]) => {
  if (!isSmartDuration.value) {
    if (durationValue.value < newMin) durationValue.value = newMin
    if (durationValue.value > newMax) durationValue.value = newMax
    // 同步到外部
    emit('update:modelValue', durationValue.value)
  }
})

/** 切换智能时长开关 */
function toggleSmartDuration(e: Event) {
  const checked = (e.target as HTMLInputElement).checked
  if (checked) {
    // 开启智能时长 → -1
    emit('update:modelValue', -1)
  } else {
    // 关闭智能时长 → 用步进器当前值（钳制到有效范围）
    durationValue.value = Math.max(effectiveMin.value, Math.min(effectiveMax.value, 5))
    emit('update:modelValue', durationValue.value)
  }
}

/** 步进器 +/- */
function stepDuration(delta: number) {
  let v = durationValue.value + delta
  if (v < effectiveMin.value) v = effectiveMin.value
  if (v > effectiveMax.value) v = effectiveMax.value
  durationValue.value = v
  emit('update:modelValue', v)
}

/**
 * 本地值代理：v-model 绑定本地变量，避免每次 input 都触发父组件重新渲染。
 * 只在值真正变化时才 emit，由 Vue 的 v-model 内部处理 DOM 更新。
 */
const localValue = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

/** 格式化枚举选项：snake_case → 可读 */
function formatOption(opt: string): string {
  // 常见中文映射
  const zhNames: Record<string, string> = {
    'pro': '专业版',
    'std': '标准版',
    'standard': '标准版',
    'high': '高质量',
    'medium': '中等',
    'low': '低质量',
    '16:9': '16:9 横屏',
    '9:16': '9:16 竖屏',
    '1:1': '1:1 方形',
    '5': '5秒',
    '10': '10秒',
    'on': '开',
    'off': '关',
  }
  if (zhNames[opt]) return zhNames[opt]

  // snake_case → 可读
  return opt.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
}
</script>

<style scoped>
.param-input-wrapper {
  width: 100%;
}

.param-input-wrapper.readonly {
  opacity: 0.7;
}

.param-input {
  width: 100%;
  padding: 7px 10px;
  background: var(--flow-bg);
  border: 1px solid var(--flow-border-hover);
  border-radius: 6px;
  color: var(--flow-text);
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.param-input:focus {
  border-color: var(--flow-accent);
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.15);
}

.param-input:read-only {
  cursor: default;
  border-color: var(--flow-border);
}

.param-select {
  width: 100%;
  padding: 7px 10px;
  background: var(--flow-bg);
  border: 1px solid var(--flow-border-hover);
  border-radius: 6px;
  color: var(--flow-text);
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.2s;
}

.param-select:focus {
  border-color: var(--flow-accent);
}

.param-select:disabled {
  cursor: default;
  opacity: 0.7;
}

.param-textarea {
  width: 100%;
  padding: 8px 10px;
  background: var(--flow-bg);
  border: 1px solid var(--flow-border-hover);
  border-radius: 6px;
  color: var(--flow-text);
  font-size: 13px;
  resize: vertical;
  min-height: 50px;
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.param-textarea:focus {
  border-color: var(--flow-accent);
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.15);
}

.param-textarea:read-only {
  cursor: default;
  border-color: var(--flow-border);
}

.param-switch {
  position: relative;
  display: inline-block;
  width: 40px;
  height: 22px;
  cursor: pointer;
}

.param-switch.disabled {
  cursor: default;
  opacity: 0.7;
}

.param-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.switch-slider {
  position: absolute;
  inset: 0;
  background: var(--flow-border-hover);
  border-radius: 11px;
  transition: 0.2s;
}

.switch-slider::before {
  content: '';
  position: absolute;
  width: 18px;
  height: 18px;
  left: 2px;
  bottom: 2px;
  background: var(--flow-text-muted);
  border-radius: 50%;
  transition: 0.2s;
}

.param-switch input:checked + .switch-slider {
  background: var(--flow-accent);
  box-shadow: 0 0 8px rgba(59, 130, 246, 0.3);
}

.param-switch input:checked + .switch-slider::before {
  transform: translateX(18px);
  background: var(--flow-text);
}

.param-desc {
  font-size: 10px;
  color: var(--flow-text-dim);
  margin-top: 3px;
}

/* ========== Duration 智能时长控件 ========== */
.duration-control {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.duration-smart-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}

.duration-smart-toggle.disabled {
  cursor: default;
  opacity: 0.7;
}

.duration-smart-toggle input {
  width: 16px;
  height: 16px;
  cursor: pointer;
  accent-color: var(--flow-accent);
}

.toggle-label {
  font-size: 12px;
  color: var(--flow-text);
  font-weight: 500;
}

.duration-stepper {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}

.stepper-btn {
  width: 28px;
  height: 28px;
  border: 1px solid var(--flow-border-hover);
  border-radius: 6px;
  background: var(--flow-bg);
  color: var(--flow-text);
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
  flex-shrink: 0;
}

.stepper-btn:hover:not(:disabled) {
  border-color: var(--flow-accent);
  background: var(--flow-bg-hover, rgba(59, 130, 246, 0.1));
}

.stepper-btn:active:not(:disabled) {
  transform: scale(0.95);
}

.stepper-btn:disabled {
  opacity: 0.35;
  cursor: default;
}

.stepper-value {
  min-width: 32px;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  color: var(--flow-text);
}

.stepper-range {
  font-size: 11px;
  color: var(--flow-text-dim);
  margin-left: 4px;
}
</style>