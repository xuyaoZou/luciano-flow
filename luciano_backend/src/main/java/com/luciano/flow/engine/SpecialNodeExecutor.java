package com.luciano.flow.engine;

import com.luciano.flow.NodeType;
import com.luciano.flow.OutputRef;
import com.luciano.flow.PortType;

/**
 * 特殊节点执行器
 * <p>
 * 处理输入类节点和预览类节点，不经过适配器。
 * <p>
 * 输入节点（ImageInput/VideoInput/AudioInput/TextInput）：
 * - 用户已通过 params 提供了值（URL 或文本）
 * - 执行时直接将 params 转为 OutputRef
 * <p>
 * 预览节点（ImagePreview/VideoPreview）：
 * - 不产生输出，只接收输入用于前端预览
 * - 执行时标记为 completed 即可
 * <p>
 * 主体节点（ElementSource）：
 * - 用户已通过 params 提供了 elementId（Kling 主体库 ID）
 * - 执行时直接将 elementId 转为 OutputRef
 * <p>
 * 参考设计文档 §3.4 特殊节点定义。
 */
public class SpecialNodeExecutor {

    /**
     * 执行特殊节点
     *
     * @param nodeType 节点类型名
     * @param params   节点参数
     * @return 输出端口的 OutputRef Map（slotName → OutputRef）
     */
    public static java.util.Map<String, OutputRef> execute(String nodeType, java.util.Map<String, Object> params) {
        java.util.Map<String, OutputRef> outputs = new java.util.HashMap<>();

        switch (nodeType) {
            case NodeType.IMAGE_INPUT -> {
                // 图片输入节点：用户在 params 中提供了图片 URL
                String url = getParamAsString(params, "url");
                if (url != null) {
                    outputs.put("image", OutputRef.builder()
                            .url(url)
                            .dataType(PortType.IMAGE)
                            .build());
                }
            }
            case NodeType.VIDEO_INPUT -> {
                String url = getParamAsString(params, "url");
                if (url != null) {
                    outputs.put("video", OutputRef.builder()
                            .url(url)
                            .dataType(PortType.VIDEO)
                            .build());
                }
            }
            case NodeType.AUDIO_INPUT -> {
                String url = getParamAsString(params, "url");
                if (url != null) {
                    outputs.put("audio", OutputRef.builder()
                            .url(url)
                            .dataType(PortType.AUDIO)
                            .build());
                }
            }
            case NodeType.TEXT_INPUT -> {
                String text = getParamAsString(params, "text");
                if (text != null) {
                    outputs.put("prompt", OutputRef.builder()
                            .url(text)  // 文本直接用 url 字段存储内容
                            .dataType(PortType.PROMPT)
                            .build());
                }
            }
            case NodeType.ELEMENT_SOURCE -> {
                // 主体节点：用户在 params 中提供了 elementId
                // elementId 存在 params.elementId 中（数字），用于 Kling 主体引用
                Object elementId = params != null ? params.get("elementId") : null;
                if (elementId != null) {
                    outputs.put("element", OutputRef.builder()
                            .url(elementId.toString())  // 用 url 字段存 element_id（数字字符串）
                            .dataType(PortType.ELEMENT)
                            .build());
                }
            }
            case NodeType.IMAGE_PREVIEW, NodeType.VIDEO_PREVIEW -> {
                // 预览节点不产生输出
            }
            case NodeType.SWITCH -> {
                // Switch 节点暂未实现
            }
            default -> throw new IllegalArgumentException("未知特殊节点类型: " + nodeType);
        }

        return outputs;
    }

    private static String getParamAsString(java.util.Map<String, Object> params, String key) {
        if (params == null) return null;
        Object val = params.get(key);
        return val != null ? val.toString() : null;
    }
}
