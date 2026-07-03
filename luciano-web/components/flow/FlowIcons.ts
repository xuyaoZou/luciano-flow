/**
 * Flow 节点图标 — SVG path 定义
 * 每个能力/特殊节点类型对应一组图标路径
 * viewBox: 0 0 24 24
 */

/** 能力类型 → 图标路径 */
export const CAPABILITY_ICONS: Record<string, string> = {
  // 视频
  text_to_video: 'M8 5v14l11-7z',                           // ▶ play
  image_to_video: 'M4 8l4-4v3h4v2H8v3L4 8zm8 4l4 4-4 4v-3H8v-2h4v-3z',  // image→video arrows
  first_last_frame: 'M3 5h6v6H3V5zm0 8h6v6H3v-6zm8-4h2v2h-2V9zm4-4h2v2h-2V5zm0 8h2v2h-2v-2zm4-4h2v2h-2V9zm0 8h2v2h-2v-2z',  // two frames + dots
  reference_to_video: 'M5 3l7 9-7 9V3zm7 3l7 6-7 6V6z',  // double play
  lip_sync: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 14c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm4-2c0-1.1-.9-2-2-2s-2 .9-2 2 .9 2 2 2 2-.9 2-2z', // mouth/lips
  extend_video: 'M6 6h2v12H6V6zm4 6l8 6V6l-8 6z',          // extend + play
  text_to_image: 'M3 5h14v14H3V5zm2 2v10h10V7H5z',        // frame
  omni_video: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 14l-5-5h3V8h4v3h3l-5 5z',  // circle + play
  omni_image: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-3 14l-3-3 1.4-1.4L9 13.2l5.6-5.6L16 9l-7 7z', // circle + check frame
}

/** 特殊节点类型 → 图标路径 */
export const SPECIAL_ICONS: Record<string, string> = {
  ImageInput: 'M5 3h14v14H5V3zm2 2v10h10V5H7zm3 3l2 3 2-2 3 4H8l2-5z',  // frame + image
  VideoInput: 'M5 3h14v14H5V3zm4 4l6 3-6 3V7z',                           // frame + play
  AudioInput: 'M12 3v10.55A4 4 0 1 0 14 17V7h4V3h-6z',                  // music note
  TextInput: 'M3 5h18v14H3V5zm2 2v10h14V7H5zm4 3h6v2H9v-2z',            // text lines
  ImagePreview: 'M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z', // eye
  VideoPreview: 'M3 5h18v14H3V5zm7 4l5 3-5 3V9z',                        // screen + play
  Switch: 'M4 8h4l3-3v14l-3-3H4V8zm12.5 4c0-1.77-1.02-3.3-2.5-4.03v8.05c1.48-.73 2.5-2.26 2.5-4.02z', // switch/fork
  ElementSource: 'M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z', // person/subject
}

/** 类别 → 图标路径（兜底） */
export const CATEGORY_ICONS: Record<string, string> = {
  '视频': 'M8 5v14l11-7z',
  '图片': 'M3 5h14v14H3V5zm2 2v10h10V7H5z',
  '音频': 'M12 3v10.55A4 4 0 1 0 14 17V7h4V3h-6z',
  '输入': 'M9 3l-6 9h4v9h4v-9h4L9 3z',
  '输出': 'M15 21l6-9h-4V3h-4v9h4l-6 9z',
  '控制': 'M4 8h4l3-3v14l-3-3H4V8zm12.5 4c0-1.77-1.02-3.3-2.5-4.03v8.05c1.48-.73 2.5-2.26 2.5-4.02z',
}

/** 类别 → 节点边框强调色 */
export const CATEGORY_ACCENTS: Record<string, string> = {
  '视频': '#ef4444',
  '图片': '#3b82f6',
  '音频': '#22c55e',
  '输入': '#4ade80',
  '输出': '#60a5fa',
  '控制': '#a855f7',
}

/** 获取节点图标路径 */
export function getNodeIcon(type: string, category?: string): string {
  return CAPABILITY_ICONS[type] || CATEGORY_ICONS[category || ''] || 'M12 2l3.09 6.26L22 9.27l-5 4.87L18.18 22 12 18.27 5.82 22 7 14.14l-5-4.87 6.91-1.01L12 2z'
}