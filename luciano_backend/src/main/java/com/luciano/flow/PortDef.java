package com.luciano.flow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 端口定义
 * <p>
 * 描述工作流节点的一个输入/输出端口。
 * 参考设计文档 §4.2 PortDef 定义。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortDef {

    /** 端口名（如 "image", "video", "prompt"） */
    private String name;

    /** 显示名（如 "图片", "视频", "提示词"） */
    private String displayName;

    /** 端口数据类型 */
    private PortType dataType;

    /** 是否必填端口 */
    @Builder.Default
    private boolean required = false;

    /** 是否支持多输入（如参考图可传多张） */
    @Builder.Default
    private boolean multi = false;

    /**
     * 对应的适配器参数名（可选）。
     * 为空时自动推断：multi 端口 → name_list，普通端口 → name。
     * 用于端口名和适配器参数名不一致的场景（如端口 image → 参数 image_url）。
     */
    private String paramName;
}