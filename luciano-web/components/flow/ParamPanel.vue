<!-- 节点参数面板 — Schema 驱动动态渲染（暗色科技风） -->
<template>
  <div v-if="visible" class="param-panel">
    <div class="param-panel-header">
      <h3>{{ nodeData.label || nodeData.type }}</h3>
      <span v-if="isReadOnly" class="status-tag completed">✅ 已完成</span>
      <span v-else-if="nodeData.status === 'running'" class="status-tag running">⏳ 执行中</span>
      <span v-else-if="nodeData.status === 'failed'" class="status-tag failed">❌ 失败</span>
      <button class="close-btn" @click="$emit('close')">✕</button>
    </div>

    <!-- 节点类型信息 -->
    <div class="param-panel-type">
      <span class="type-badge">{{ nodeData.category }}</span>
      <span class="type-name">{{ nodeData.type }}</span>
      <span v-if="nodeData.adapterId" class="adapter-badge">{{ nodeData.adapterId }}</span>
    </div>

    <!-- 适配器选择 -->
    <div v-if="!nodeData.isSpecial" class="param-section">
      <label class="param-label">适配器</label>
      <select v-model="adapterId" class="param-select" :disabled="isReadOnly" @change="onAdapterChange">
        <option :value="null">自动路由</option>
        <option v-for="adapter in availableAdapters" :key="adapter.id" :value="adapter.id">
          {{ adapter.displayName }}
        </option>
      </select>
    </div>

    <!-- 输入节点：直接值（排除主体节点） -->
    <template v-if="nodeData.isSpecial && isInputNode && nodeData.type !== 'ElementSource'">
      <div class="param-section">
        <label class="param-label">{{ inputLabel }}</label>
        <!-- 图片/视频/音频：URL 输入 -->
        <input
          v-if="hasUrlInput"
          v-model="params.url"
          type="text"
          class="param-input"
          placeholder="输入 URL..."
          @input="onParamChange"
        />
        <!-- 文本输入：多行 -->
        <textarea
          v-if="nodeData.type === 'TextInput'"
          v-model="params.text"
          class="param-textarea"
          placeholder="输入提示词..."
          rows="4"
          @input="onParamChange"
        />
      </div>
    </template>

    <!-- 主体节点：ElementSource 专用面板 -->
    <template v-if="nodeData.isSpecial && nodeData.type === 'ElementSource'">
      <!-- 已创建主体信息展示 -->
      <div v-if="params.elementId" class="param-section">
        <div class="element-info-card">
          <div v-if="params.elementThumb" class="element-info-thumb">
            <img :src="params.elementThumb" alt="" />
          </div>
          <div class="element-info-detail">
            <div class="element-info-name">{{ params.elementName }}</div>
            <div v-if="params.elementDesc" class="element-info-desc">{{ params.elementDesc }}</div>
            <div v-if="params.tagList && params.tagList.length" class="element-info-tags">
              <span v-for="tag in params.tagList" :key="tag.id" class="element-info-tag">{{ tag.name }}</span>
            </div>
            <div class="element-info-id">ID: {{ params.elementId }}</div>
          </div>
        </div>
        <!-- 参考图 -->
        <div v-if="params.refImageUrls && params.refImageUrls.length" class="param-section">
          <label class="param-label">参考图</label>
          <div class="element-ref-images">
            <div v-for="(url, idx) in params.refImageUrls" :key="idx" class="element-ref-item">
              <img v-if="url" :src="url" alt="" />
            </div>
          </div>
        </div>
        <button class="param-btn" @click="resetElement">重新创建</button>
      </div>
      <!-- 创建/选择模式 -->
      <div v-else class="param-section">
        <div class="element-mode-tabs">
          <button class="mode-tab" :class="{ active: elementMode === 'create' }" @click="elementMode = 'create'">新建主体</button>
          <button class="mode-tab" :class="{ active: elementMode === 'select' }" @click="elementMode = 'select'; loadExistingElements()">选择已有</button>
        </div>

        <!-- 新建模式 -->
        <template v-if="elementMode === 'create'">
          <div class="param-section">
            <label class="param-label">主体名称 <span class="required-star">*</span></label>
            <input v-model="createForm.element_name" type="text" class="param-input" placeholder="输入主体名称" />
          </div>
          <div class="param-section">
            <label class="param-label">描述</label>
            <input v-model="createForm.element_desc" type="text" class="param-input" placeholder="可选描述" />
          </div>
          <div class="param-section">
            <label class="param-label">标签</label>
            <select v-model="createForm.tag_id" class="param-input">
              <option value="o_102">人物</option>
              <option value="o_103">动物</option>
              <option value="o_104">道具</option>
              <option value="o_105">服饰</option>
              <option value="o_106">场景</option>
              <option value="o_107">特效</option>
              <option value="o_101">热梗</option>
              <option value="o_108">其他</option>
            </select>
          </div>
          <div class="param-section">
            <label class="param-label">正面图 <span class="required-star">*</span></label>
            <div class="upload-row">
              <div class="upload-area" @click="$refs.faceFileInput.click()">
                <img v-if="createForm.faceImageUrl" :src="createForm.faceImageUrl" class="upload-preview" />
                <div v-else class="upload-placeholder">📷 上传</div>
              </div>
              <button v-if="imageNodes && imageNodes.length > 0" class="canvas-pick-btn" @click="showImagePicker = 'face'; loadPickerImages()">画布选择</button>
            </div>
            <input ref="faceFileInput" type="file" accept="image/*" style="display:none" @change="onFaceUpload" />
          </div>
          <div class="param-section">
            <label class="param-label">参考图（可选 1~3 张）</label>
            <div class="ref-images-row">
              <div v-for="(img, idx) in [0,1,2]" :key="idx" class="ref-slot" @click="$refs['refFileInput' + idx]?.click()">
                <img v-if="createForm.refImageUrls[idx]" :src="createForm.refImageUrls[idx]" class="ref-thumb" />
                <div v-else class="ref-placeholder">+</div>
              </div>
              <input v-for="idx in [0,1,2]" :key="idx" :ref="el => { if (el) $refs['refFileInput' + idx] = el }" type="file" accept="image/*" style="display:none" @change="e => onRefUpload(e, idx)" />
            </div>
            <button v-if="imageNodes && imageNodes.length > 0" class="canvas-pick-btn" @click="openRefPicker()">🎨 从画布选择参考图</button>
          </div>
          <div class="param-section">
            <button class="create-element-btn" :disabled="elementCreating" @click="createElement">
              {{ elementCreating ? '创建中...' : '创建主体' }}
            </button>
          </div>
          <div v-if="createStatus" class="param-section">
            <div class="element-status" :class="createStatus.type">{{ createStatus.text }}</div>
          </div>
        </template>

        <!-- 选择已有模式 -->
        <template v-if="elementMode === 'select'">
          <div class="param-section">
            <label class="param-label">选择主体</label>
            <div v-if="loadingElements" class="param-loading">加载中...</div>
            <div v-else-if="existingElements.length === 0" class="param-loading">暂无主体，请先创建</div>
            <div v-else class="element-list">
            <div v-for="el in existingElements" :key="el.element_id" class="element-item" :class="{ selected: params.elementId == el.element_id }" @click="selectElement(el)">
              <img v-if="el.face_image_url" :src="el.face_image_url" class="element-item-thumb" />
              <div class="element-item-info">
                <span class="element-item-name">{{ el.element_name }}</span>
                <span class="element-item-id">ID: {{ el.element_id }}</span>
              </div>
            </div>
          </div>
        </div>
      </template>
      </div>
    </template>

    <!-- 从画布选择图片弹窗 -->
    <div v-if="showImagePicker" class="image-picker-overlay" @click.self="showImagePicker = null">
      <div class="image-picker-dialog">
        <h4>从画布选择图片</h4>
        <div v-if="imageNodes && imageNodes.length > 0" class="image-picker-list">
          <div v-for="node in imageNodes" :key="node.nodeId" class="picker-node-group">
            <div class="picker-node-name">{{ node.nodeName }}</div>
            <div class="picker-images-row">
              <img
                v-for="(url, idx) in node.imageUrls"
                :key="idx"
                :src="getPickerImage(node.nodeId, idx, url)"
                class="picker-image"
                @click="selectImageFromCanvas(node, idx)"
              />
            </div>
          </div>
        </div>
        <div v-else class="picker-empty">画布上没有图片输出节点</div>
      </div>
    </div>

    <!-- Capability 节点：Schema 驱动参数 -->
    <template v-if="!nodeData.isSpecial">
      <!-- 加载中 -->
      <div v-if="loadingSchema" class="param-loading">
        加载参数 Schema...
      </div>

      <!-- Schema 参数渲染 -->
      <template v-else-if="schemaParams.length > 0">
        <!-- 默认参数（必填 + 端口参数，去重） -->
        <div class="param-section">
          <div v-for="p in defaultParams" :key="p.name" class="param-field">
            <label class="param-label">
              {{ p.displayName }}
              <span v-if="p.required" class="required-star">*</span>
            </label>
            <!-- multi 端口连线：资源选择器 -->
            <ConnectedResourceList
              v-if="isMultiPortParam(p.name)"
              :connections="getMultiConnections(p.name)"
              :selected-urls="getSelectedUrls(p.name)"
              :readonly="isReadOnly"
              @update:selected-urls="onSelectionChange(p.name, $event)"
            />
            <!-- single 端口连线：来源标签 -->
            <div v-else-if="isParamConnected(p.name)" class="connected-input">
              <span class="connected-badge">🔗 {{ getConnectionSource(p.name) }}</span>
            </div>
            <ParamInput v-else :schema="p" :readonly="isReadOnly" v-model="params[p.name]" :all-params="params" @update:modelValue="onParamChange" />
          </div>
        </div>

        <!-- 更多参数（折叠） -->
        <div v-if="moreParams.length > 0" class="param-section">
          <button class="toggle-advanced" @click="showAdvanced = !showAdvanced">
            {{ showAdvanced ? '▼' : '▶' }} 更多参数 ({{ moreParams.length }})
          </button>
          <template v-if="showAdvanced">
            <div v-for="p in moreParams" :key="p.name" class="param-field">
              <label class="param-label">{{ p.displayName }}</label>
              <ConnectedResourceList
                v-if="isMultiPortParam(p.name)"
                :connections="getMultiConnections(p.name)"
                :selected-urls="getSelectedUrls(p.name)"
                :readonly="isReadOnly"
                @update:selected-urls="onSelectionChange(p.name, $event)"
              />
              <div v-else-if="isParamConnected(p.name)" class="connected-input">
                <span class="connected-badge">🔗 {{ getConnectionSource(p.name) }}</span>
              </div>
              <ParamInput v-else :schema="p" :readonly="isReadOnly" v-model="params[p.name]" :all-params="params" @update:modelValue="onParamChange" />
            </div>
          </template>
        </div>
      </template>

      <!-- 无 Schema 时显示端口提示 -->
      <div v-else class="param-empty">
        <p>暂无参数配置</p>
        <p class="param-hint">连线传入的值会自动覆盖手动参数</p>
      </div>
    </template>


  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import type { FlowNodeData } from '~/composables/useFlowCanvas'
import type { ParamSchema, CapabilitySchema } from '~/types/adapter'
import { isInputNode as checkInputNode } from '~/types/flow'
import ParamInput from './ParamInput.vue'
import ConnectedResourceList from './ConnectedResourceList.vue'
import { useApi } from '~/composables/useApi'
import { useMediaLoader } from '~/composables/useMediaLoader'

interface ConnectionInfo {
  sourceNodeId: string
  sourceSlot: string
  targetSlot: string
  sourceNode?: FlowNodeData
}

const props = defineProps<{
  visible: boolean
  nodeData: FlowNodeData
  nodeId: string
  connectedInputs?: ConnectionInfo[]
  imageNodes?: { nodeId: string; nodeName: string; nodeType: string; imageUrls: string[]; mediaIds: number[] }[]
}>()

const emit = defineEmits<{
  close: []
  'update:params': [nodeId: string, params: Record<string, any>]
  'update:adapterId': [nodeId: string, adapterId: string | null]
}>()

const api = useApi()
const flowApi = useFlowApi()
const mediaLoader = useMediaLoader()

const params = ref<Record<string, any>>({ ...props.nodeData.params })
const adapterId = ref<string | null>(props.nodeData.adapterId)

// 只读模式：已完成节点参数锁定
const isReadOnly = computed(() => props.nodeData.status === 'completed')
const showAdvanced = ref(false)
const loadingSchema = ref(false)
const schemaParams = ref<ParamSchema[]>([])
const currentSchema = ref<CapabilitySchema | null>(null)

// ElementSource 主体节点状态
const elementMode = ref<'create' | 'select'>('create')
const elementCreating = ref(false)
const loadingElements = ref(false)
const existingElements = ref<any[]>([])
const createStatus = ref<{ type: string; text: string } | null>(null)
const createForm = ref({
  element_name: '',
  element_desc: '',
  tag_id: 'o_102',
  faceImageUrl: '',
  refImageUrls: [null, null, null] as (string | null)[],
})
const faceFileInput = ref<HTMLInputElement | null>(null)

// 画布图片选择器
const showImagePicker = ref<string | null>(null)  // 'face' | 'ref_0' | 'ref_1' | 'ref_2' | null
const pickerBlobUrls = ref<Record<string, string>>({})  // "nodeId-idx" -> blob URL

/** 打开画布图片选择器时预加载 blob URL */
async function loadPickerImages() {
  if (!props.imageNodes) return
  for (const node of props.imageNodes) {
    for (let i = 0; i < node.imageUrls.length; i++) {
      const key = `${node.nodeId}-${i}`
      if (pickerBlobUrls.value[key]) continue
      const rawUrl = node.imageUrls[i]
      const mediaIdMatch = rawUrl.match(/\/media\/(\d+)\/file/)
      const mediaId = mediaIdMatch ? parseInt(mediaIdMatch[1]) : null
      if (mediaId) {
        try {
          const blobUrl = await mediaLoader.loadMedia({ id: mediaId, url: rawUrl })
          pickerBlobUrls.value[key] = blobUrl
        } catch {
          pickerBlobUrls.value[key] = rawUrl
        }
      } else {
        pickerBlobUrls.value[key] = rawUrl
      }
    }
  }
}

/** 获取选择器中的图片 blob URL */
function getPickerImage(nodeId: string, idx: number, fallback: string): string {
  return pickerBlobUrls.value[`${nodeId}-${idx}`] || fallback
}

// 可用适配器列表（从 API 获取）
const availableAdapters = ref<{ id: string; displayName: string }[]>([])

// 连线占用的输入端口名集合（兼容旧逻辑）
const connectedSlotNames = computed(() => {
  const names = new Set<string>()
  for (const conn of (props.connectedInputs || [])) {
    names.add(conn.targetSlot)
  }
  return names
})

// ========== 端口→参数映射 ==========

/**
 * 端口名 → 参数名候选列表
 * multi 端口: [portName_list, portName]
 * 普通端口: [portName]
 * 如果 PortDef 有 paramName 字段，优先使用它
 */
function getParamNamesForPort(portName: string, multi: boolean, explicitParamName?: string): string[] {
  if (explicitParamName) return [explicitParamName]
  if (multi) return [`${portName}_list`, portName]
  return [portName]
}

/**
 * 端口类型 → Schema 参数类型匹配
 * 用于端口名和参数名不一致时（如端口 image，参数 reference_images）的类型匹配
 */
function isPortTypeCompatible(portDataType: string, paramType: string): boolean {
  const map: Record<string, string[]> = {
    'image': ['IMAGE_URL', 'IMAGE_LIST'],
    'video': ['VIDEO_URL', 'VIDEO_LIST'],
    'audio': ['AUDIO', 'AUDIO_LIST'],
    'element': ['ELEMENT_LIST'],
    'prompt': ['STRING'],
    'text': ['STRING'],
    'negative_prompt': ['STRING'],
  }
  const compatible = map[portDataType?.toLowerCase()] || []
  return compatible.includes(paramType?.toUpperCase())
}

/**
 * 构建参数名→连线来源的映射
 * key = 参数名, value = 连线信息
 */
const connectedParamMap = computed(() => {
  const map = new Map<string, ConnectionInfo>()
  const inputs = props.connectedInputs || []
  for (const conn of inputs) {
    // 从节点定义查找端口信息
    const port = props.nodeData.inputSlots?.find(s => s.name === conn.targetSlot)
    if (!port) {
    	// 端口定义找不到，直接用 targetSlot 作为参数名
    	map.set(conn.targetSlot, conn)
    	continue
    }
    const paramNames = getParamNamesForPort(port.name, port.multi, (port as any).paramName)
    for (const pn of paramNames) {
      map.set(pn, conn)
    }
  }
  return map
})

/**
 * multi 端口的所有连线（用于资源列表展示）
 * key = 端口名, value = 连线列表
 */
const multiPortConnections = computed(() => {
  const map = new Map<string, ConnectionInfo[]>()
  const inputs = props.connectedInputs || []
  for (const conn of inputs) {
    const port = props.nodeData.inputSlots?.find(s => s.name === conn.targetSlot)
    if (!port?.multi) continue
    const list = map.get(conn.targetSlot) || []
    list.push(conn)
    map.set(conn.targetSlot, list)
  }
  return map
})

/**
 * 判断参数是否被连线占用
 * 支持端口名≠参数名时的类型匹配回退
 */
function isParamConnected(paramName: string): boolean {
  // 1. 精确匹配参数名
  if (connectedParamMap.value.has(paramName)) return true
  // 2. 类型匹配回退：找端口类型兼容但参数名不匹配的连线
  const schemaParam = schemaParams.value.find(p => p.name === paramName)
  if (!schemaParam) return false
  for (const conn of (props.connectedInputs || [])) {
    const port = props.nodeData.inputSlots?.find(s => s.name === conn.targetSlot)
    if (!port) continue
    // 已在 connectedParamMap 中的跳过（已精确匹配）
    const paramNames = getParamNamesForPort(port.name, port.multi, (port as any).paramName)
    if (paramNames.some(pn => connectedParamMap.value.has(pn))) continue
    // 类型兼容匹配
    if (isPortTypeCompatible(port.dataType, schemaParam.type)) return true
  }
  return false
}

/**
 * 获取参数对应的连线来源描述
 */
function getConnectionSource(paramName: string): string | null {
  const conn = connectedParamMap.value.get(paramName)
  if (!conn) return null
  const sourceType = conn.sourceNode?.type || conn.sourceNodeId
  return `来自 ${sourceType}#${conn.sourceNodeId.slice(-4)}`
}

/**
 * 获取参数对应的 multi 端口连线列表
 * 支持端口名≠参数名时的类型匹配回退
 */
function getMultiConnections(paramName: string): ConnectionInfo[] {
  // 1. 精确匹配：参数名反查端口名
  for (const [portName, conns] of multiPortConnections.value) {
    const port = props.nodeData.inputSlots?.find(s => s.name === portName)
    if (!port) continue
    const paramNames = getParamNamesForPort(port.name, port.multi, (port as any).paramName)
    if (paramNames.includes(paramName)) return conns
  }
  // 2. 类型匹配回退：IMAGE_LIST 参数匹配 IMAGE multi 端口
  const schemaParam = schemaParams.value.find(p => p.name === paramName)
  if (!schemaParam) return []
  for (const [portName, conns] of multiPortConnections.value) {
    const port = props.nodeData.inputSlots?.find(s => s.name === portName)
    if (!port?.multi) continue
    if (isPortTypeCompatible(port.dataType, schemaParam.type)) return conns
  }
  return []
}

/**
 * 判断参数是否对应 multi 端口
 */
function isMultiPortParam(paramName: string): boolean {
  return getMultiConnections(paramName).length > 0
}

/**
 * 获取 multi 端口参数的已选 URL 列表（从 params 读取）
 */
function getSelectedUrls(paramName: string): string[] {
  const val = props.nodeData.params?.[paramName]
  if (Array.isArray(val)) return val as string[]
  if (typeof val === 'string' && val) return [val]
  return []
}

/**
 * 用户勾选变化 → 更新 params
 */
function onSelectionChange(paramName: string, urls: string[]) {
  const newParams = { ...props.nodeData.params, [paramName]: urls }
  emit('update:params', props.nodeId, newParams)
}

const isInputNode = computed(() => checkInputNode(props.nodeData.type))

const inputLabel = computed(() => {
  const labels: Record<string, string> = {
    ImageInput: '图片 URL',
    VideoInput: '视频 URL',
    AudioInput: '音频 URL',
    TextInput: '提示词',
  }
  return labels[props.nodeData.type] || '值'
})

const hasUrlInput = computed(() =>
  ['ImageInput', 'VideoInput', 'AudioInput'].includes(props.nodeData.type)
)

// 判断参数是否对应某个输入端口（有连线能力的参数）
function isPortParam(paramName: string): boolean {
  if (!props.nodeData.inputSlots) return false
  for (const slot of props.nodeData.inputSlots) {
    const paramNames = getParamNamesForPort(slot.name, slot.multi, (slot as any).paramName)
    if (paramNames.includes(paramName)) return true
    // 类型兼容回退
    const schemaParam = schemaParams.value.find(p => p.name === paramName)
    if (schemaParam && isPortTypeCompatible(slot.dataType, schemaParam.type)) return true
  }
  return false
}

// 分组逻辑：
// 1. defaultParams（默认显示）：必填参数 + 所有端口对应参数（无论是否连线），去重
// 2. moreParams（折叠）：其余所有参数
const defaultParams = computed(() => {
  const result = []
  const seen = new Set()
  // 先加必填参数
  for (const p of schemaParams.value) {
    if (p.required && !seen.has(p.name)) {
      result.push(p)
      seen.add(p.name)
    }
  }
  // 再加端口对应参数（无论是否连线）
  for (const p of schemaParams.value) {
    if (!p.required && isPortParam(p.name) && !seen.has(p.name)) {
      result.push(p)
      seen.add(p.name)
    }
  }
  return result
})
const moreParams = computed(() =>
  schemaParams.value.filter(p => !p.required && !isPortParam(p.name))
)

// 加载 Capability Schema
async function loadSchema() {
  if (props.nodeData.isSpecial) return

  loadingSchema.value = true
  try {
    // 获取适配器列表
    const capInfo = await api.listCapabilities()
    availableAdapters.value = capInfo.adapters.map(a => ({
      id: a.id,
      displayName: a.displayName,
    }))

    // 用当前选中的适配器，或第一个支持的适配器拿 Schema
    const selectedAdapterId = adapterId.value || availableAdapters.value[0]?.id
    if (selectedAdapterId) {
      const schemas = await api.getAllSchemas(selectedAdapterId)
      const schema = schemas[props.nodeData.type]
      if (schema) {
        currentSchema.value = schema
        schemaParams.value = [...schema.requiredParams, ...schema.optionalParams]
        // 填充默认值
        for (const p of schemaParams.value) {
          if (params.value[p.name] === undefined && p.defaultValue !== null && p.defaultValue !== undefined) {
            params.value[p.name] = p.defaultValue
          }
        }
      }
    }
  } catch (e) {
    console.error('加载 Schema 失败:', e)
  } finally {
    loadingSchema.value = false
  }
}

function onParamChange() {
  emit('update:params', props.nodeId, { ...params.value })
}

function onAdapterChange() {
  emit('update:adapterId', props.nodeId, adapterId.value)
  // 切换适配器后重新加载 Schema
  loadSchema()
}

// 监听 nodeId 变化（切换节点时重置参数和重新加载 Schema）
watch(() => props.nodeId, (newId, oldId) => {
  if (!newId) return
  // 切换到不同节点时，完全重置状态
  params.value = { ...props.nodeData.params }
  adapterId.value = props.nodeData.adapterId
  showAdvanced.value = false
  schemaParams.value = []
  currentSchema.value = null
  // 重置 ElementSource 状态
  elementMode.value = 'create'
  elementCreating.value = false
  loadingElements.value = false
  existingElements.value = []
  createStatus.value = null
  createForm.value = { element_name: '', element_desc: '', tag_id: 'o_102', faceImageUrl: '', refImageUrls: [null, null, null] }
  if (!props.nodeData.isSpecial) {
    loadSchema()
  }
}, { immediate: true })

// 监听 nodeData.params 变化（外部更新了节点 params 时同步到本地）
let syncingFromExternal = false
watch(() => props.nodeData.params, (newParams) => {
  if (newParams && !syncingFromExternal) {
    syncingFromExternal = true
    params.value = { ...newParams }
    nextTick(() => { syncingFromExternal = false })
  }
}, { deep: true })

// ==================== ElementSource 主体节点逻辑 ====================

/** 加载已有主体列表 */
async function loadExistingElements() {
  loadingElements.value = true
  try {
    const res = await api.listKlingElements()
    // Kling API 返回格式: { code: 0, data: { elements: [...] } }
    const elements = res?.data?.elements || res?.elements || []
    existingElements.value = elements
  } catch (e) {
    console.error('[ElementSource] 加载主体列表失败:', e)
    existingElements.value = []
  } finally {
    loadingElements.value = false
  }
}

/** 重置主体参数，回到创建模式 */
function resetElement() {
  params.value = {}
  elementMode.value = 'create'
  createForm.value = { element_name: '', element_desc: '', tag_id: 'o_102', faceImageUrl: '', refImageUrls: [null, null, null] }
  createStatus.value = null
  onParamChange()
}

/** 选择已有主体 */
function selectElement(el: any) {
  params.value.elementId = el.element_id
  params.value.elementName = el.element_name
  params.value.elementThumb = el.face_image_url || ''
  onParamChange()
}

/** 打开参考图选择器，自动找第一个空位 */
function openRefPicker() {
  for (let i = 0; i < 3; i++) {
    if (!createForm.value.refImageUrls[i]) {
      showImagePicker.value = 'ref_' + i
      loadPickerImages()
      return
    }
  }
  // 都满了，覆盖第一个
  showImagePicker.value = 'ref_0'
  loadPickerImages()
}

/** 从画布选择图片 */
async function selectImageFromCanvas(node: { nodeId: string; nodeName: string; imageUrls: string[]; mediaIds: number[] }, idx: number) {
  const rawUrl = node.imageUrls[idx]
  const mediaId = node.mediaIds[idx]
  // 用 blob URL 展示（相对路径 /api/v1/media/xxx/file 不能直接给 <img :src> 用）
  const displayUrl = await mediaLoader.loadMedia({ id: mediaId, url: rawUrl })
  if (showImagePicker.value === 'face') {
    createForm.value.faceImageUrl = displayUrl
    createForm.value.faceMediaId = mediaId
  } else if (showImagePicker.value?.startsWith('ref_')) {
    const refIdx = parseInt(showImagePicker.value.split('_')[1])
    createForm.value.refImageUrls[refIdx] = displayUrl
    ;(createForm.value as any)['refMediaId_' + refIdx] = mediaId
  }
  showImagePicker.value = null
}

/** 正面图上传 */
async function onFaceUpload(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  // TODO: 上传到后端获取 URL
  // 暂时用 FileReader 转 base64 预览
  const reader = new FileReader()
  reader.onload = () => {
    createForm.value.faceImageUrl = reader.result as string
  }
  reader.readAsDataURL(file)
}

/** 参考图上传 */
async function onRefUpload(e: Event, idx: number) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    createForm.value.refImageUrls[idx] = reader.result as string
  }
  reader.readAsDataURL(file)
}

/** 创建主体 */
async function createElement() {
  if (!createForm.value.element_name || !createForm.value.faceImageUrl) {
    createStatus.value = { type: 'error', text: '请填写名称并上传正面图' }
    return
  }
  elementCreating.value = true
  createStatus.value = { type: 'pending', text: '创建中...' }
  try {
    // 收集所有有 mediaId 的图片（从画布选择的）
    const mediaIds: number[] = []
    if ((createForm.value as any).faceMediaId) {
      mediaIds.push((createForm.value as any).faceMediaId)
    }
    for (let i = 0; i < 3; i++) {
      const refMediaId = (createForm.value as any)['refMediaId_' + i]
      if (refMediaId) mediaIds.push(refMediaId)
    }

    const tagId = createForm.value.tag_id

    if (mediaIds.length > 1) {
      // 多张图（正面图+参考图）：走 from-media，后端下载转 base64
      const res = await api.createKlingElementFromMedia({
        element_name: createForm.value.element_name,
        element_desc: createForm.value.element_desc,
        media_ids: mediaIds,
        tag_id: tagId,
      })
      const taskId = res.task_id || res.data?.task_id
      if (taskId) {
        createStatus.value = { type: 'pending', text: '主体创建中，等待 Kling 处理...' }
        pollElementTask(taskId)
      } else {
        finishElementCreation(res)
      }
    } else if (mediaIds.length === 1) {
      // 只有一张正面图：走 auto-from-media（异步），后端自动翻转生成参考图
      createStatus.value = { type: 'pending', text: '正在生成参考图并创建主体...' }
      const res = await api.autoCreateKlingElement({
        element_name: createForm.value.element_name,
        element_desc: createForm.value.element_desc,
        media_id: mediaIds[0],
        tag_id: tagId,
      })
      // auto-from-media 返回 job_id，轮询异步任务状态
      const jobId = res.job_id
      if (jobId) {
        pollAutoElementJob(jobId)
      } else {
        // 兼容旧格式
        const klingResult = res.kling_result || res
        const taskId = klingResult.task_id || klingResult.data?.task_id
        if (taskId) {
          createStatus.value = { type: 'pending', text: '主体创建中，等待 Kling 处理...' }
          pollElementTask(taskId, res)
        } else {
          finishElementCreation(res)
        }
      }
    } else {
      // 本地上传的方式：直接传 URL/base64
      const referImgs = createForm.value.refImageUrls.filter(Boolean).map((u: string) => ({ image_url: u }))
      const elementImageList: any = { frontal_image: createForm.value.faceImageUrl }
      if (referImgs.length > 0) elementImageList.refer_images = referImgs

      const tagList = tagId ? [{ tag_id: tagId }] : undefined
      const res = await api.createKlingElement({
        element_name: createForm.value.element_name,
        element_description: createForm.value.element_desc || createForm.value.element_name,
        reference_type: 'image_refer',
        element_image_list: elementImageList,
        ...(tagList ? { tag_list: tagList } : {}),
      })
      const taskId = res.task_id || res.data?.task_id
      if (taskId) {
        createStatus.value = { type: 'pending', text: '主体创建中，等待 Kling 处理...' }
        pollElementTask(taskId)
      } else {
        finishElementCreation(res)
      }
    }
  } catch (e: any) {
    createStatus.value = { type: 'error', text: '创建失败: ' + (e.message || '未知错误') }
    elementCreating.value = false
  }
}

/** 轮询 auto-from-media 异步任务（两阶段：后端处理 → Kling 处理） */
async function pollAutoElementJob(jobId: string) {
  const maxAttempts = 80
  for (let i = 0; i < maxAttempts; i++) {
    await new Promise(r => setTimeout(r, 3000))
    try {
      const job = await api.getAutoElementJobStatus(jobId)
      const status = job.status
      const step = job.step || ''

      if (status === 'failed') {
        createStatus.value = { type: 'error', text: '创建失败: ' + (job.error || '未知错误') }
        elementCreating.value = false
        return
      }

      if (status === 'kling_processing') {
        // 后端完成，进入 Kling 处理阶段
        const klingTaskId = job.kling_task_id
        if (klingTaskId) {
          createStatus.value = { type: 'pending', text: '主体创建中，等待 Kling 处理...' }
          // 轮询 Kling 任务状态
          pollElementTask(klingTaskId, { ref_image_urls: job.ref_image_urls, frontal_media_id: job.frontal_media_id })
          return
        }
      }

      // 更新进度文本
      if (step) {
        createStatus.value = { type: 'pending', text: step }
      }
    } catch (e) {
      console.error('[ElementSource] 轮询异步任务失败:', e)
    }
  }
  createStatus.value = { type: 'error', text: '轮询超时' }
  elementCreating.value = false
}

/** 完成主体创建（同步返回的情况） */
function finishElementCreation(res: any) {
  const klingResult = res.kling_result || res
  params.value.elementId = klingResult.element_id || klingResult.data?.element_id || res.element_id || res.data?.element_id
  params.value.elementName = createForm.value.element_name
  params.value.elementThumb = res.face_image_url || res.data?.face_image_url || klingResult.face_image_url || klingResult.data?.face_image_url || createForm.value.faceImageUrl
  if (res.ref_image_urls) {
    ;(params.value as any).refImageUrls = res.ref_image_urls
  }
  createStatus.value = { type: 'success', text: '创建成功!' }
  onParamChange()
  elementCreating.value = false
}

/** 轮询主体创建任务 */
async function pollElementTask(taskId: string, autoRes?: any) {
  const maxAttempts = 80
  for (let i = 0; i < maxAttempts; i++) {
    await new Promise(r => setTimeout(r, 3000))
    try {
      const res = await api.pollKlingElementTask(taskId)
      // Kling API 返回格式：{ code: 0, data: { task_status: "succeed", task_result: { elements: [...] } } }
      const data = res.data || res
      const taskStatus = data.task_status || res.status || res.task_status
      const runState = data.run_state ?? res.run_state
      if (taskStatus === 'succeed' || taskStatus === 'succeeded' || runState === 3) {
        // 提取 element 信息
        const elements = data.task_result?.elements || []
        const element = elements[0] || {}
        params.value.elementId = element.element_id || data.element_id || res.element_id
        params.value.elementName = createForm.value.element_name
        // Kling 返回的正面图在 element_image_list.frontal_image
        params.value.elementThumb = element.element_image_list?.frontal_image || element.face_image_url || createForm.value.faceImageUrl
        // 保存标签
        if (element.tag_list?.length) {
          ;(params.value as any).tagList = element.tag_list
        }
        // 保存参考图（优先 Kling 返回的，其次 auto-from-media 返回的）
        const klingRefImages = element.element_image_list?.refer_images || []
        if (klingRefImages.length) {
          ;(params.value as any).refImageUrls = klingRefImages.map((r: any) => r.image_url || r.url || r)
        } else if (autoRes?.ref_image_urls) {
          ;(params.value as any).refImageUrls = autoRes.ref_image_urls
        }
        // 保存描述
        if (createForm.value.element_desc) {
          ;(params.value as any).elementDesc = createForm.value.element_desc
        }
        createStatus.value = { type: 'success', text: '创建成功!' }
        onParamChange()
        elementCreating.value = false
        return
      }
      if (taskStatus === 'failed' || runState === 4) {
        createStatus.value = { type: 'error', text: '创建失败' }
        elementCreating.value = false
        return
      }
    } catch (e) {
      console.error('[ElementSource] 轮询失败:', e)
    }
  }
  createStatus.value = { type: 'error', text: '轮询超时' }
  elementCreating.value = false
}
</script>

<style scoped>
.param-panel {
  position: absolute;
  right: 0;
  top: 0;
  width: 320px;
  height: 100%;
  background: var(--flow-surface);
  border-left: 1px solid var(--flow-border);
  overflow-y: auto;
  z-index: 20;
  color: var(--flow-text);
  box-shadow: -4px 0 16px rgba(0, 0, 0, 0.3);
}

.param-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--flow-border);
  background: rgba(255, 255, 255, 0.01);
  gap: 8px;
}

.status-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
  white-space: nowrap;
}

.status-tag.completed {
  background: rgba(34, 197, 94, 0.15);
  color: #22c55e;
  border: 1px solid rgba(34, 197, 94, 0.3);
}

.status-tag.running {
  background: rgba(59, 130, 246, 0.15);
  color: #3b82f6;
  border: 1px solid rgba(59, 130, 246, 0.3);
}

.status-tag.failed {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
  border: 1px solid rgba(239, 68, 68, 0.3);
}

.param-panel-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  background: linear-gradient(135deg, var(--flow-text) 0%, var(--flow-text-muted) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.close-btn {
  background: none;
  border: none;
  color: var(--flow-text-dim);
  font-size: 18px;
  cursor: pointer;
  transition: color 0.2s;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
}

.close-btn:hover {
  color: var(--flow-text);
  background: var(--flow-ctrl-hover-bg, rgba(0, 0, 0, 0.05));
}

.param-panel-type {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 12px;
  color: var(--flow-text-dim);
  border-bottom: 1px solid var(--flow-border);
}

.type-badge {
  background: rgba(59, 130, 246, 0.15);
  color: #60a5fa;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.adapter-badge {
  background: rgba(168, 85, 247, 0.15);
  color: #c084fc;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.param-section {
  padding: 12px 16px;
  border-bottom: 1px solid var(--flow-border);
}

.section-title {
  font-size: 10px;
  color: var(--flow-text-dim);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin: 0 0 10px;
  font-weight: 600;
}

.param-field {
  margin-bottom: 10px;
}

.param-field:last-child {
  margin-bottom: 0;
}

.param-label {
  display: block;
  font-size: 12px;
  color: var(--flow-text-muted);
  margin-bottom: 4px;
  font-weight: 500;
}

.required-star {
  color: #ef4444;
  margin-left: 2px;
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
  transition: border-color 0.2s, box-shadow 0.2s;
  box-sizing: border-box;
}

.param-input:focus {
  border-color: var(--flow-accent);
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.15);
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

.param-textarea {
  width: 100%;
  padding: 8px 10px;
  background: var(--flow-bg);
  border: 1px solid var(--flow-border-hover);
  border-radius: 6px;
  color: var(--flow-text);
  font-size: 13px;
  resize: vertical;
  min-height: 60px;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
  box-sizing: border-box;
}

.param-textarea:focus {
  border-color: var(--flow-accent);
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.15);
}

.toggle-advanced {
  background: none;
  border: none;
  color: var(--flow-text-dim);
  font-size: 12px;
  cursor: pointer;
  padding: 4px 0;
  transition: color 0.2s;
}

.toggle-advanced:hover {
  color: var(--flow-text);
}

.param-loading,
.param-empty {
  padding: 16px;
  text-align: center;
  color: var(--flow-text-dim);
  font-size: 13px;
}

.param-hint {
  font-size: 11px;
  color: var(--flow-text-dim);
  margin-top: 8px;
}

/* 连线来源标签 */
.connected-input {
  display: flex;
  align-items: center;
  padding: 6px 10px;
  background: var(--flow-hover-bg);
  border: 1px dashed var(--flow-border);
  border-radius: 6px;
  font-size: 12px;
  color: var(--flow-text-muted);
}
.connected-badge {
  display: flex;
  align-items: center;
  gap: 4px;
}
.connected-badge::before {
  content: '';
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--flow-accent);
  opacity: 0.6;
}

/* ==================== ElementSource 主体节点样式 ==================== */
.element-mode-tabs {
  display: flex;
  gap: 4px;
}

.mode-tab {
  flex: 1;
  padding: 6px 10px;
  background: var(--flow-bg);
  border: 1px solid var(--flow-border-hover);
  border-radius: 6px;
  color: var(--flow-text-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.mode-tab.active {
  background: var(--flow-accent);
  border-color: var(--flow-accent);
  color: white;
}

.upload-area {
  width: 100%;
  min-height: 80px;
  border: 1px dashed var(--flow-border-hover);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color 0.2s;
  overflow: hidden;
}

.upload-area:hover {
  border-color: var(--flow-accent);
}

.upload-preview {
  max-width: 100%;
  max-height: 80px;
  object-fit: contain;
  border-radius: 6px;
}

.upload-placeholder {
  font-size: 12px;
  color: var(--flow-text-dim);
}

.ref-images-row {
  display: flex;
  gap: 6px;
}

.ref-slot {
  flex: 1;
  aspect-ratio: 1;
  border: 1px dashed var(--flow-border-hover);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  overflow: hidden;
  transition: border-color 0.2s;
}

.ref-slot:hover {
  border-color: var(--flow-accent);
}

.ref-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 6px;
}

.ref-placeholder {
  font-size: 18px;
  color: var(--flow-text-dim);
}

.create-element-btn {
  width: 100%;
  padding: 8px;
  background: var(--flow-accent);
  border: none;
  border-radius: 6px;
  color: white;
  font-size: 13px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.create-element-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.element-status {
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 12px;
  text-align: center;
}

.element-status.pending {
  background: rgba(217, 160, 74, 0.15);
  color: #D9A04A;
}

.element-status.success {
  background: rgba(74, 217, 122, 0.15);
  color: #4AD97A;
}

.element-status.error {
  background: rgba(217, 74, 74, 0.15);
  color: #D94A4A;
}

.element-info-card {
  display: flex;
  gap: 10px;
  padding: 10px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.element-info-thumb {
  width: 60px;
  height: 60px;
  border-radius: 6px;
  overflow: hidden;
  flex-shrink: 0;
}

.element-info-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.element-info-detail {
  flex: 1;
  min-width: 0;
}

.element-info-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary, #fff);
  margin-bottom: 4px;
}

.element-info-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  margin-bottom: 4px;
}

.element-info-tag {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 3px;
  background: rgba(255, 107, 107, 0.2);
  color: #FF6B6B;
}

.element-info-id {
  font-size: 10px;
  color: var(--text-dim, #888);
}

.element-info-desc {
  font-size: 11px;
  color: var(--text-dim, #aaa);
  margin-bottom: 4px;
  line-height: 1.3;
}

.element-ref-images {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.element-ref-item {
  width: 50px;
  height: 50px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.element-ref-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.param-btn {
  width: 100%;
  padding: 8px;
  margin-top: 8px;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 6px;
  background: transparent;
  color: var(--text-dim, #888);
  cursor: pointer;
  font-size: 12px;
}

.param-btn:hover {
  background: rgba(255, 255, 255, 0.05);
}

.element-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 300px;
  overflow-y: auto;
}

.element-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px;
  border: 1px solid var(--flow-border-hover);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.element-item:hover {
  border-color: var(--flow-accent);
}

.element-item.selected {
  border-color: var(--flow-accent);
  background: rgba(59, 130, 246, 0.1);
}

.element-item-thumb {
  width: 36px;
  height: 36px;
  border-radius: 4px;
  object-fit: cover;
}

.element-item-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.element-item-name {
  font-size: 12px;
  color: var(--flow-text);
}

.element-item-id {
  font-size: 10px;
  color: var(--flow-text-dim);
}

/* ==================== 画布图片选择器 ==================== */
.upload-row {
  display: flex;
  gap: 6px;
  align-items: stretch;
}

.upload-row .upload-area {
  flex: 1;
}

.canvas-pick-btn {
  padding: 6px 10px;
  background: var(--flow-bg);
  border: 1px solid var(--flow-border-hover);
  border-radius: 6px;
  color: var(--flow-text-muted);
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s;
}

.canvas-pick-btn:hover {
  border-color: var(--flow-accent);
  color: var(--flow-text);
}

.image-picker-overlay {
  position: fixed;
  inset: 0;
  z-index: 300;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
}

.image-picker-dialog {
  background: var(--flow-surface);
  border: 1px solid var(--flow-border-hover);
  border-radius: 12px;
  padding: 16px;
  max-width: 500px;
  max-height: 60vh;
  overflow-y: auto;
}

.image-picker-dialog h4 {
  margin: 0 0 12px;
  font-size: 14px;
  color: var(--flow-text);
}

.image-picker-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.picker-node-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.picker-node-name {
  font-size: 12px;
  color: var(--flow-text-muted);
}

.picker-images-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.picker-image {
  width: 60px;
  height: 60px;
  border-radius: 6px;
  object-fit: cover;
  cursor: pointer;
  border: 2px solid transparent;
  transition: border-color 0.15s;
}

.picker-image:hover {
  border-color: var(--flow-accent);
}

.picker-empty {
  font-size: 12px;
  color: var(--flow-text-dim);
  text-align: center;
  padding: 20px;
}

</style>